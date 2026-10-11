"""Rules for repository paths proposed by the model.

The model's output decides which files get written and committed, so a path is
treated as hostile until it passes these checks.
"""

import re

_DRIVE_PREFIX = re.compile(r"^[A-Za-z]:")
_PROTECTED_PREFIXES = (".github/workflows/",)


def unsafe_path_reason(path: str) -> str | None:
    """Return why `path` is not an acceptable repository-relative path, or None."""
    if not path or not path.strip():
        return "path is empty"
    if path != path.strip():
        return "path has leading or trailing whitespace"
    if "\\" in path:
        return "use forward slashes"
    if path.startswith("/") or _DRIVE_PREFIX.match(path):
        return "path must be relative to the repository root"
    if path.endswith("/"):
        return "path must name a file, not a directory"

    segments = path.split("/")
    if any(segment in ("", ".", "..") for segment in segments):
        return "path must not contain '.', '..' or empty segments"
    if ".git" in segments:
        return "the .git directory is off limits"
    if path.startswith(_PROTECTED_PREFIXES):
        # A generated workflow would run with the repository's CI permissions.
        return "CI workflow files are off limits"
    return None
