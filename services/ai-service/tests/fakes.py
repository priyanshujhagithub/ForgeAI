"""Test doubles. No test in this suite talks to a real model."""

import json
from types import SimpleNamespace

from app.config import Settings
from app.llm import LLMClient
from app.sandbox import SandboxUsage
from app.schemas import (
    RepositoryFile,
    RepositoryMetadata,
    RepositorySnapshotContext,
    SandboxResult,
)


class FakeChatClient:
    """Stands in for the OpenAI SDK client and replays scripted replies."""

    def __init__(self, replies: list, finish_reason: str = "stop"):
        self._replies = list(replies)
        self._finish_reason = finish_reason
        self.requests: list[dict] = []
        self.chat = SimpleNamespace(completions=SimpleNamespace(create=self._create))

    def with_options(self, **_options):
        # The real client returns a copy with other settings (such as retries).
        return self

    def _create(self, **kwargs):
        # Copy the messages: the caller keeps appending to the same list.
        self.requests.append({**kwargs, "messages": list(kwargs["messages"])})
        reply = self._replies.pop(0)
        if isinstance(reply, Exception):
            raise reply
        content = reply if isinstance(reply, str) else json.dumps(reply)
        choice = SimpleNamespace(
            message=SimpleNamespace(content=content),
            finish_reason=self._finish_reason,
        )
        return SimpleNamespace(choices=[choice])


def make_llm(
    replies: list,
    finish_reason: str = "stop",
    **settings_overrides,
) -> tuple[LLMClient, FakeChatClient]:
    client = FakeChatClient(replies, finish_reason)
    # One model and a key, unless a test says otherwise, so scripted replies
    # are consumed by exactly the calls the test expects.
    defaults = {"llm_api_key": "test-key", "llm_fallback_models": ()}
    settings = Settings(**{**defaults, **settings_overrides})
    return LLMClient(client=client, settings=settings), client


def make_context(files: dict[str, str] | None = None) -> RepositorySnapshotContext:
    files = files if files is not None else {"README.md": "# Demo", "app.py": "print('hi')"}
    return RepositorySnapshotContext(
        owner="octocat",
        repository="demo",
        files=[
            RepositoryFile(path=path, content=content, sha=f"sha-{index}")
            for index, (path, content) in enumerate(files.items())
        ],
        metadata=RepositoryMetadata(projectType="PYTHON", springBoot=False),
    )


VALID_ANALYSIS = {
    "summary": "A demo app.",
    "projectType": "Python script",
    "keyComponents": ["app.py"],
    "dependencies": [],
    "architectureNotes": "Single module.",
    "relevantFiles": ["app.py"],
}

VALID_PLAN = {
    "taskUnderstanding": "Validate the name before greeting.",
    "filesToModify": ["app.py"],
    "filesToCreate": ["tests/test_app.py"],
    "steps": [
        {"order": 1, "description": "Add validation to app.py.", "files": ["app.py"]},
        {"order": 2, "description": "Cover it with a test.", "files": ["tests/test_app.py"]},
    ],
}


class FakeSandboxSession:
    def __init__(self, runner: "FakeSandboxRunner"):
        self._runner = runner

    def run_tests(self, changes):
        self._runner.tested.append([change.model_copy() for change in changes])
        return self._runner.results.pop(0)

    def close(self):
        self._runner.closed += 1
        return SandboxUsage(sandbox_seconds=10.0, estimated_cost_usd=0.0003)


class FakeSandboxRunner:
    """Replays scripted sandbox results; an Exception in the script is raised by open()."""

    def __init__(self, results: list):
        self.results = list(results)
        self.opened: list[tuple] = []
        self.tested: list[list] = []
        self.closed = 0

    def open(self, repository, project_type, project_root=""):
        self.opened.append((repository, project_type, project_root))
        if isinstance(self.results[0], Exception):
            raise self.results.pop(0)
        return FakeSandboxSession(self)


def sandbox_result(outcome: str = "passed", **fields) -> SandboxResult:
    defaults = {
        "command": "python -m pytest -q",
        "exit_code": 0 if outcome == "passed" else 1,
        "stdout": "3 passed" if outcome == "passed" else "FAILED tests/test_app.py::test_app",
    }
    return SandboxResult(outcome=outcome, **{**defaults, **fields})
