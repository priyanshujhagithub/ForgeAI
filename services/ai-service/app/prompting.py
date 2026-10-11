"""Turns a repository snapshot into prompt text that fits the model's context."""

from collections.abc import Iterable

from .schemas import RepositoryFile, RepositorySnapshotContext

# Below this many characters a file excerpt is not worth including.
_MIN_USEFUL_CHARS = 200


def build_repository_evidence(
    context: RepositorySnapshotContext,
    *,
    budget_chars: int,
    max_file_chars: int,
    focus_paths: Iterable[str] = (),
) -> str:
    """Describe the repository within `budget_chars` of file content.

    The full file tree is always listed so the model knows every path that
    exists. File contents follow in the order Java prioritised them, with
    `focus_paths` moved to the front, each truncated to `max_file_chars`.
    """
    lines = [
        f"Repository: {context.owner}/{context.repository}",
        f"Detected project type: {context.metadata.projectType}",
        f"Spring Boot: {context.metadata.springBoot}",
        "",
        f"File tree ({len(context.files)} files):",
    ]
    lines.extend(f"- {file.path}" for file in context.files)
    lines.append("")

    shown: list[str] = []
    remaining = budget_chars

    for file in _ordered(context.files, focus_paths):
        if remaining < _MIN_USEFUL_CHARS:
            break
        body = _truncate(file.content, min(max_file_chars, remaining))
        shown.extend(
            [
                f"===== FILE: {file.path} =====",
                body,
                f"===== END FILE: {file.path} =====",
                "",
            ]
        )
        remaining -= len(body)

    shown_count = len(shown) // 4
    lines.append(
        f"File contents ({shown_count} of {len(context.files)} files shown; "
        "the rest are omitted for space):"
    )
    lines.extend(shown)
    return "\n".join(lines)


def _ordered(
    files: list[RepositoryFile],
    focus_paths: Iterable[str],
) -> list[RepositoryFile]:
    focus = list(dict.fromkeys(focus_paths))
    by_path = {file.path: file for file in files}
    first = [by_path[path] for path in focus if path in by_path]
    rest = [file for file in files if file.path not in set(focus)]
    return first + rest


def _truncate(content: str, limit: int) -> str:
    if len(content) <= limit:
        return content
    omitted = len(content) - limit
    return f"{content[:limit]}\n[... truncated {omitted} characters ...]"
