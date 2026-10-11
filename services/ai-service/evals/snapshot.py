"""Builds the same repository context Java sends, for a public GitHub repository.

The evaluation harness runs the Python agent directly, without the Java
control plane, so it needs its own (much simpler) way to read a repository.
"""

import json
import urllib.request

from app.schemas import (
    RepositoryFile,
    RepositoryMetadata,
    RepositoryRef,
    RepositorySnapshotContext,
)

_MANIFEST_TYPES = {
    "pom.xml": "MAVEN",
    "build.gradle": "GRADLE",
    "build.gradle.kts": "GRADLE",
    "package.json": "NODE",
    "requirements.txt": "PYTHON",
    "pyproject.toml": "PYTHON",
}
_MAX_FILES = 200
_MAX_FILE_BYTES = 500 * 1024


def fetch_public_snapshot(
    owner: str,
    repository: str,
    branch: str,
    sha: str | None = None,
) -> tuple[RepositorySnapshotContext, RepositoryRef]:
    """Read the repository at `sha`, or at the head of `branch` when no commit is given.

    Pinning a commit keeps results comparable between models: the tasks are
    always attempted against the same starting code.
    """
    api = f"https://api.github.com/repos/{owner}/{repository}"
    head = sha or _get_json(f"{api}/git/ref/heads/{branch}")["object"]["sha"]
    tree = _get_json(f"{api}/git/trees/{head}?recursive=1")["tree"]

    blobs = [
        entry
        for entry in tree
        if entry["type"] == "blob" and entry.get("size", 0) <= _MAX_FILE_BYTES
    ]
    blobs.sort(key=lambda entry: (_priority(entry["path"]), entry["path"]))

    files = []
    for entry in blobs[:_MAX_FILES]:
        raw = f"https://raw.githubusercontent.com/{owner}/{repository}/{head}/{entry['path']}"
        try:
            content = _get(raw).decode("utf-8")
        except UnicodeDecodeError:
            continue  # binary file
        files.append(RepositoryFile(path=entry["path"], content=content, sha=entry["sha"]))

    context = RepositorySnapshotContext(
        owner=owner,
        repository=repository,
        files=files,
        metadata=RepositoryMetadata(
            projectType=detect_project_type(file.path for file in files),
            springBoot=False,
        ),
    )
    reference = RepositoryRef(
        clone_url=f"https://github.com/{owner}/{repository}.git",
        base_branch=branch,
        base_sha=head,
    )
    return context, reference


def detect_project_type(paths) -> str:
    names = {path.rpartition("/")[2] for path in paths}
    for manifest, project_type in _MANIFEST_TYPES.items():
        if manifest in names:
            return project_type
    return "UNKNOWN"


def _priority(path: str) -> int:
    """README, then manifests, then source, then tests: the order Java uses."""
    name = path.rpartition("/")[2].lower()
    if name in ("readme.md", "readme"):
        return 0
    if name in _MANIFEST_TYPES:
        return 1
    if "/tests/" in f"/{path}" or "/test/" in f"/{path}":
        return 4
    return 3


def _get(url: str) -> bytes:
    request = urllib.request.Request(url, headers={"User-Agent": "forgeai-evals"})
    with urllib.request.urlopen(request, timeout=30) as response:
        return response.read()


def _get_json(url: str):
    return json.loads(_get(url))
