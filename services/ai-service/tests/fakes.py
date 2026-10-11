"""Test doubles. No test in this suite talks to a real model."""

import json
from types import SimpleNamespace

from app.config import Settings
from app.llm import LLMClient
from app.schemas import RepositoryFile, RepositoryMetadata, RepositorySnapshotContext


class FakeChatClient:
    """Stands in for the OpenAI SDK client and replays scripted replies."""

    def __init__(self, replies: list, finish_reason: str = "stop"):
        self._replies = list(replies)
        self._finish_reason = finish_reason
        self.requests: list[dict] = []
        self.chat = SimpleNamespace(completions=SimpleNamespace(create=self._create))

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
    return LLMClient(client=client, settings=Settings(**settings_overrides)), client


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
