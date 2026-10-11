"""Runs generated changes against a repository's tests inside an E2B sandbox.

One session per agent run: the repository is cloned and its dependencies are
installed once, then each correction attempt resets the working tree, applies
its changes and runs the tests. Reusing the sandbox keeps E2B credit use and
waiting time low. Generated code never runs on the host machine.
"""

import shlex
import time
from collections.abc import Callable, Iterable
from dataclasses import dataclass
from typing import Any, Protocol

from .config import Settings
from .path_safety import unsafe_path_reason
from .schemas import CodeChange, RepositoryRef, SandboxResult

_REPO_DIR = "/home/user/repo"

# Asks git for credentials from the environment, so the token is never part of
# a command line, the clone URL, or the repository's .git/config.
_GIT_CREDENTIALS_FROM_ENV = (
    "-c credential.helper= "
    "-c credential.helper='!f() { echo username=x-access-token; "
    'echo "password=$FORGEAI_GIT_TOKEN"; }; f\''
)


class SandboxUnavailableError(RuntimeError):
    """No sandbox could be started: missing key, rejected key, or exhausted credit."""


@dataclass(frozen=True)
class Recipe:
    manifests: tuple[str, ...]
    setup: str | None
    test: str
    needs_java: bool = False


RECIPES: dict[str, Recipe] = {
    "PYTHON": Recipe(
        manifests=("requirements.txt", "pyproject.toml"),
        setup=(
            "if [ -f requirements.txt ]; then python -m pip install -q -r requirements.txt; "
            "else python -m pip install -q -e .; fi && python -m pip install -q pytest"
        ),
        # Warnings are hidden so the failure itself fits in the model's context.
        test="python -m pytest -q --disable-warnings",
    ),
    "NODE": Recipe(
        manifests=("package.json",),
        setup="npm install --no-audit --no-fund",
        test="npm test",
    ),
    "MAVEN": Recipe(
        manifests=("pom.xml",),
        setup=None,
        test="mvn -q -B test",
        needs_java=True,
    ),
    "GRADLE": Recipe(
        manifests=("build.gradle", "build.gradle.kts"),
        setup=None,
        test="gradle test",
        needs_java=True,
    ),
}


def find_project_root(paths: Iterable[str], project_type: str) -> str:
    """Folder holding the build manifest, relative to the repository root.

    Tests run from here, so a project nested inside a larger repository works.
    The shallowest manifest wins; "" means the repository root.
    """
    recipe = RECIPES.get(project_type.upper())
    if recipe is None:
        return ""
    folders = [
        path.rpartition("/")[0]
        for path in paths
        if path.rpartition("/")[2] in recipe.manifests
    ]
    return min(folders, key=lambda folder: (folder.count("/") + bool(folder), folder), default="")


@dataclass(frozen=True)
class SandboxUsage:
    sandbox_seconds: float
    estimated_cost_usd: float


class SandboxSession(Protocol):
    def run_tests(self, changes: list[CodeChange]) -> SandboxResult: ...

    def close(self) -> SandboxUsage: ...


class SandboxRunner(Protocol):
    def open(
        self,
        repository: RepositoryRef,
        project_type: str,
        project_root: str = "",
    ) -> SandboxSession:
        """Start a sandbox with the repository cloned and dependencies installed.

        Raises SandboxUnavailableError if no sandbox can be started, and
        SandboxSetupError if the unmodified repository cannot be prepared.
        """
        ...


class SandboxSetupError(RuntimeError):
    """The unmodified repository could not be cloned or its dependencies installed."""

    def __init__(self, result: SandboxResult, usage: SandboxUsage):
        super().__init__(result.message or "Sandbox setup failed")
        self.result = result
        self.usage = usage


class E2BSandboxRunner:
    def __init__(
        self,
        settings: Settings | None = None,
        sandbox_factory: Callable[..., Any] | None = None,
    ):
        self.settings = settings or Settings.from_env()
        # Tests inject a fake factory; production creates real E2B sandboxes.
        self._sandbox_factory = sandbox_factory

    def open(
        self,
        repository: RepositoryRef,
        project_type: str,
        project_root: str = "",
    ) -> "E2BSandboxSession":
        recipe = RECIPES.get(project_type.upper())
        if recipe is None:
            raise SandboxUnavailableError(
                f"No test recipe for project type '{project_type}'."
            )
        if project_root and unsafe_path_reason(project_root + "/x"):
            raise ValueError(f"Unsafe project root: {project_root!r}")

        template = self.settings.e2b_template
        if recipe.needs_java:
            template = self.settings.e2b_template_java
            if not template:
                raise SandboxUnavailableError(
                    f"{project_type} projects need a sandbox template with a JDK; "
                    "set E2B_TEMPLATE_JAVA (see infrastructure/e2b)."
                )

        session = E2BSandboxSession(
            sandbox=self._create_sandbox(template),
            settings=self.settings,
            recipe=recipe,
            project_root=project_root,
        )
        try:
            session.prepare(repository)
        except BaseException:
            session.close()
            raise
        return session

    def _create_sandbox(self, template: str):
        factory = self._sandbox_factory
        if factory is None:
            if not self.settings.e2b_api_key:
                raise SandboxUnavailableError(
                    "E2B_API_KEY is not set, so no sandbox can be started."
                )
            from e2b import Sandbox

            factory = Sandbox.create

        try:
            return factory(
                template=template or None,
                timeout=self.settings.sandbox_lifetime_seconds,
                api_key=self.settings.e2b_api_key or None,
            )
        except Exception as exc:  # auth failure, exhausted credit, rate limit, network
            raise SandboxUnavailableError(
                f"E2B could not start a sandbox: {type(exc).__name__}: {exc}"
            ) from exc


class E2BSandboxSession:
    def __init__(self, sandbox: Any, settings: Settings, recipe: Recipe, project_root: str):
        self._sandbox = sandbox
        self._settings = settings
        self._recipe = recipe
        self._project_root = project_root
        self._started = time.monotonic()
        self._usage: SandboxUsage | None = None

    @property
    def _workdir(self) -> str:
        return f"{_REPO_DIR}/{self._project_root}" if self._project_root else _REPO_DIR

    def prepare(self, repository: RepositoryRef) -> None:
        """Clone the repository and install its baseline dependencies."""
        envs = {}
        credentials = ""
        if repository.access_token:
            envs["FORGEAI_GIT_TOKEN"] = repository.access_token
            credentials = _GIT_CREDENTIALS_FROM_ENV + " "

        clone = (
            f"git {credentials}clone -q --depth 1 "
            f"--branch {shlex.quote(repository.base_branch)} "
            f"{shlex.quote(repository.clone_url)} {_REPO_DIR}"
        )
        self._setup_step(clone, cwd="/home/user", envs=envs, what="clone the repository")

        if repository.base_sha:
            sha = shlex.quote(repository.base_sha)
            pin = (
                f"git {credentials}fetch -q --depth 1 origin {sha} "
                f"&& git checkout -q --detach {sha}"
            )
            self._setup_step(pin, cwd=_REPO_DIR, envs=envs, what="check out the base commit")

        if self._recipe.setup:
            self._setup_step(
                self._recipe.setup, cwd=self._workdir, envs={}, what="install dependencies"
            )

    def run_tests(self, changes: list[CodeChange]) -> SandboxResult:
        """Reset to the base commit, apply `changes`, and run the test command."""
        for change in changes:
            if reason := unsafe_path_reason(change.path):
                raise ValueError(f"Refusing to write '{change.path}': {reason}")

        reset = self._run("git reset -q --hard && git clean -fdq", _REPO_DIR, timeout=60)
        if reset.exit_code != 0:
            return self._infrastructure_error(reset, "Could not reset the working tree.")

        for change in changes:
            target = f"{_REPO_DIR}/{change.path}"
            if change.action == "delete":
                self._sandbox.files.remove(target)
            else:
                self._sandbox.files.write(target, change.content)

        # A change that edits the manifest may add dependencies, so install again.
        # If that fails it is the change's fault, and the model gets to see why.
        if self._recipe.setup and self._touches_manifest(changes):
            install = self._run(
                self._recipe.setup, self._workdir, self._settings.sandbox_setup_timeout_seconds
            )
            if install.exit_code != 0:
                return install.model_copy(update={"outcome": "tests_failed"})

        return self._run(
            self._recipe.test, self._workdir, self._settings.sandbox_test_timeout_seconds
        )

    def close(self) -> SandboxUsage:
        """Destroy the sandbox. Safe to call more than once."""
        if self._usage is None:
            try:
                self._sandbox.kill()
            finally:
                seconds = time.monotonic() - self._started
                self._usage = SandboxUsage(
                    sandbox_seconds=round(seconds, 1),
                    estimated_cost_usd=round(
                        seconds * self._settings.sandbox_usd_per_second, 5
                    ),
                )
        return self._usage

    def __enter__(self) -> "E2BSandboxSession":
        return self

    def __exit__(self, *exc_info) -> None:
        self.close()

    def _touches_manifest(self, changes: list[CodeChange]) -> bool:
        return any(
            change.path.rpartition("/")[2] in self._recipe.manifests for change in changes
        )

    def _setup_step(self, command: str, cwd: str, envs: dict[str, str], what: str) -> None:
        result = self._run(
            command, cwd, self._settings.sandbox_setup_timeout_seconds, envs=envs
        )
        if result.exit_code != 0:
            failure = self._infrastructure_error(result, f"Could not {what}.")
            raise SandboxSetupError(failure, self.close())

    def _infrastructure_error(self, result: SandboxResult, message: str) -> SandboxResult:
        return result.model_copy(update={"outcome": "infrastructure_error", "message": message})

    def _run(
        self,
        command: str,
        cwd: str,
        timeout: float,
        envs: dict[str, str] | None = None,
    ) -> SandboxResult:
        # Imported here so the module loads (and unit tests run) without the SDK.
        from e2b import CommandExitException, TimeoutException

        started = time.monotonic()
        exit_code: int | None
        stdout = stderr = ""
        timed_out = False
        try:
            finished = self._sandbox.commands.run(
                command, cwd=cwd, envs=envs or None, timeout=timeout
            )
            exit_code, stdout, stderr = finished.exit_code, finished.stdout, finished.stderr
        except CommandExitException as exc:
            exit_code, stdout, stderr = exc.exit_code, exc.stdout, exc.stderr
        except TimeoutException:
            exit_code, timed_out = None, True
            stderr = f"Command did not finish within {timeout:.0f} seconds."

        return SandboxResult(
            outcome="passed" if exit_code == 0 else "tests_failed",
            command=command,
            exit_code=exit_code,
            stdout=self._tail(stdout),
            stderr=self._tail(stderr),
            duration_seconds=round(time.monotonic() - started, 1),
            timed_out=timed_out,
        )

    def _tail(self, text: str) -> str:
        """Keep the end of long output: that is where test failures are reported."""
        limit = self._settings.sandbox_output_tail_chars
        if len(text) <= limit:
            return text
        return f"[... {len(text) - limit} earlier characters omitted ...]\n{text[-limit:]}"
