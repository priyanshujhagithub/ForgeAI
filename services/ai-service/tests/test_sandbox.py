from types import SimpleNamespace

import pytest
from e2b import CommandExitException, TimeoutException

from app.config import Settings
from app.sandbox import (
    E2BSandboxRunner,
    SandboxSetupError,
    SandboxUnavailableError,
    find_project_root,
)
from app.schemas import CodeChange, RepositoryRef

REPO = RepositoryRef(clone_url="https://github.com/octocat/demo.git", base_branch="main")
TOKEN = "ghs_secret_token_value"


class FakeE2BSandbox:
    """Records what the runner does to a sandbox; never touches the network."""

    def __init__(self, outcomes: dict[str, object] | None = None):
        # Maps a substring of a command to an exit code, or to an exception to raise.
        self.outcomes = outcomes or {}
        self.commands_run: list[dict] = []
        self.written: dict[str, str] = {}
        self.removed: list[str] = []
        self.killed = 0
        self.commands = SimpleNamespace(run=self._run)
        self.files = SimpleNamespace(write=self._write, remove=self.removed.append)

    def _run(self, cmd, cwd=None, envs=None, timeout=None):
        self.commands_run.append({"cmd": cmd, "cwd": cwd, "envs": envs, "timeout": timeout})
        for fragment, outcome in self.outcomes.items():
            if fragment in cmd:
                if isinstance(outcome, Exception):
                    raise outcome
                if outcome != 0:
                    raise _exit(outcome, stdout="out", stderr="boom")
        return SimpleNamespace(exit_code=0, stdout="ok", stderr="")

    def _write(self, path, data):
        self.written[path] = data

    def kill(self):
        self.killed += 1

    def ran(self, fragment: str) -> list[dict]:
        return [call for call in self.commands_run if fragment in call["cmd"]]


def _exit(code: int, stdout: str = "", stderr: str = "") -> CommandExitException:
    exc = CommandExitException.__new__(CommandExitException)
    exc.exit_code, exc.stdout, exc.stderr, exc.error = code, stdout, stderr, None
    return exc


def open_session(sandbox, repository=REPO, project_type="PYTHON", project_root="", **settings):
    runner = E2BSandboxRunner(
        settings=Settings(**settings),
        sandbox_factory=lambda **kwargs: sandbox,
    )
    return runner.open(repository, project_type, project_root)


def change(path="app.py", action="modify", content="print('new')\n"):
    return CodeChange(path=path, action=action, content=content, explanation="")


# --- project root detection -------------------------------------------------


@pytest.mark.parametrize(
    ("paths", "project_type", "expected"),
    [
        (["README.md", "requirements.txt", "app/main.py"], "PYTHON", ""),
        (["README.md", "registration-service/requirements.txt"], "PYTHON", "registration-service"),
        (["a/b/requirements.txt", "a/requirements.txt"], "PYTHON", "a"),
        (["services/api/pom.xml"], "MAVEN", "services/api"),
        (["web/package.json", "package.json"], "NODE", ""),
        (["README.md"], "PYTHON", ""),
        (["requirements.txt"], "UNKNOWN", ""),
    ],
)
def test_find_project_root_uses_the_shallowest_manifest(paths, project_type, expected):
    assert find_project_root(paths, project_type) == expected


# --- setup ------------------------------------------------------------------


def test_open_clones_then_installs_dependencies_in_the_project_root():
    sandbox = FakeE2BSandbox()

    open_session(sandbox, project_root="registration-service")

    clone, install = sandbox.commands_run
    assert "clone -q --depth 1 --branch main https://github.com/octocat/demo.git" in clone["cmd"]
    assert "pip install" in install["cmd"]
    assert install["cwd"] == "/home/user/repo/registration-service"


def test_base_sha_is_checked_out_when_given():
    sandbox = FakeE2BSandbox()
    pinned = REPO.model_copy(update={"base_sha": "abc123"})

    open_session(sandbox, repository=pinned)

    assert sandbox.ran("git checkout -q --detach abc123")


def test_token_travels_in_the_environment_never_in_a_command():
    sandbox = FakeE2BSandbox()
    private = REPO.model_copy(update={"access_token": TOKEN})

    session = open_session(sandbox, repository=private)
    session.run_tests([change()])

    clone = sandbox.commands_run[0]
    assert clone["envs"] == {"FORGEAI_GIT_TOKEN": TOKEN}
    assert all(TOKEN not in call["cmd"] for call in sandbox.commands_run)
    # Only git commands receive the token; dependency installs and tests do not.
    assert all(
        not call["envs"] for call in sandbox.commands_run if "clone" not in call["cmd"]
    )
    assert TOKEN not in repr(private)


def test_failed_clone_is_an_infrastructure_error_and_the_sandbox_is_destroyed():
    sandbox = FakeE2BSandbox({"clone": 128})

    with pytest.raises(SandboxSetupError) as raised:
        open_session(sandbox)

    assert raised.value.result.outcome == "infrastructure_error"
    assert raised.value.result.message == "Could not clone the repository."
    assert raised.value.result.stderr == "boom"
    assert sandbox.killed == 1


def test_missing_api_key_means_unavailable():
    runner = E2BSandboxRunner(settings=Settings(e2b_api_key=""))

    with pytest.raises(SandboxUnavailableError, match="E2B_API_KEY is not set"):
        runner.open(REPO, "PYTHON")


def test_sandbox_creation_failure_means_unavailable():
    def refuse(**kwargs):
        raise RuntimeError("account blocked")

    runner = E2BSandboxRunner(settings=Settings(), sandbox_factory=refuse)

    with pytest.raises(SandboxUnavailableError, match="account blocked"):
        runner.open(REPO, "PYTHON")


def test_java_projects_need_a_configured_template():
    runner = E2BSandboxRunner(settings=Settings(), sandbox_factory=lambda **kw: FakeE2BSandbox())

    with pytest.raises(SandboxUnavailableError, match="E2B_TEMPLATE_JAVA"):
        runner.open(REPO, "MAVEN")


def test_sandbox_is_created_with_a_hard_lifetime():
    created = {}

    def factory(**kwargs):
        created.update(kwargs)
        return FakeE2BSandbox()

    E2BSandboxRunner(
        settings=Settings(sandbox_lifetime_seconds=123), sandbox_factory=factory
    ).open(REPO, "PYTHON")

    assert created["timeout"] == 123


# --- test runs --------------------------------------------------------------


def test_run_tests_resets_applies_changes_and_reports_a_pass():
    sandbox = FakeE2BSandbox()
    session = open_session(sandbox, project_root="svc")

    result = session.run_tests(
        [change("svc/app.py"), change("svc/old.py", action="delete", content="")]
    )

    assert result.outcome == "passed"
    assert result.exit_code == 0
    assert result.command == "python -m pytest -q --disable-warnings"
    assert sandbox.written == {"/home/user/repo/svc/app.py": "print('new')\n"}
    assert sandbox.removed == ["/home/user/repo/svc/old.py"]
    reset_index = sandbox.commands_run.index(sandbox.ran("git reset")[0])
    test_index = sandbox.commands_run.index(sandbox.ran("pytest -q")[0])
    assert reset_index < test_index
    assert sandbox.ran("pytest -q")[0]["cwd"] == "/home/user/repo/svc"


def test_failing_tests_are_reported_with_their_output():
    sandbox = FakeE2BSandbox({"pytest -q": 1})
    session = open_session(sandbox)

    result = session.run_tests([change()])

    assert result.outcome == "tests_failed"
    assert result.exit_code == 1
    assert result.stdout == "out"
    assert result.stderr == "boom"


def test_every_attempt_starts_from_a_clean_tree():
    sandbox = FakeE2BSandbox()
    session = open_session(sandbox)

    session.run_tests([change()])
    session.run_tests([change()])

    assert len(sandbox.ran("git reset -q --hard && git clean -fdq")) == 2
    # The repository is cloned and its baseline dependencies installed only once.
    assert len(sandbox.ran("clone")) == 1
    assert len(sandbox.ran("pip install")) == 1


def test_changing_the_manifest_reinstalls_dependencies():
    sandbox = FakeE2BSandbox()
    session = open_session(sandbox)

    session.run_tests([change("requirements.txt", content="fastapi\nemail-validator\n")])

    assert len(sandbox.ran("pip install")) == 2


def test_a_bad_dependency_added_by_the_change_counts_against_the_change():
    sandbox = FakeE2BSandbox()
    session = open_session(sandbox)
    sandbox.outcomes["pip install"] = 1

    result = session.run_tests([change("requirements.txt", content="no-such-package-xyz\n")])

    assert result.outcome == "tests_failed"
    assert not sandbox.ran("pytest -q")


def test_a_hanging_test_run_is_reported_as_timed_out():
    sandbox = FakeE2BSandbox({"pytest -q": TimeoutException("too slow")})
    session = open_session(sandbox, sandbox_test_timeout_seconds=7)

    result = session.run_tests([change()])

    assert result.outcome == "tests_failed"
    assert result.timed_out is True
    assert result.exit_code is None
    assert "7 seconds" in result.stderr


@pytest.mark.parametrize("path", ["../outside.py", "/etc/passwd", ".git/config"])
def test_unsafe_paths_are_refused_before_anything_is_written(path):
    sandbox = FakeE2BSandbox()
    session = open_session(sandbox)

    with pytest.raises(ValueError, match="Refusing to write"):
        session.run_tests([change(path)])

    assert sandbox.written == {}


def test_long_output_keeps_only_the_tail():
    sandbox = FakeE2BSandbox()
    session = open_session(sandbox, sandbox_output_tail_chars=10)
    sandbox.commands.run = lambda *a, **k: SimpleNamespace(
        exit_code=0, stdout="A" * 50 + "TAILTAIL99", stderr=""
    )

    result = session.run_tests([])

    assert result.stdout.endswith("TAILTAIL99")
    assert "50 earlier characters omitted" in result.stdout


# --- cleanup and cost -------------------------------------------------------


def test_close_destroys_the_sandbox_once_and_reports_usage():
    sandbox = FakeE2BSandbox()
    session = open_session(sandbox, sandbox_usd_per_second=0.5)

    usage = session.close()
    again = session.close()

    assert sandbox.killed == 1
    assert usage is again
    assert usage.sandbox_seconds >= 0
    assert usage.estimated_cost_usd == pytest.approx(usage.sandbox_seconds * 0.5, abs=0.01)


def test_context_manager_destroys_the_sandbox_even_when_the_body_fails():
    sandbox = FakeE2BSandbox()

    with pytest.raises(RuntimeError), open_session(sandbox):
        raise RuntimeError("caller blew up")

    assert sandbox.killed == 1
