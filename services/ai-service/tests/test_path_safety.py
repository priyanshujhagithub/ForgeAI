import pytest

from app.path_safety import unsafe_path_reason


@pytest.mark.parametrize(
    "path",
    ["app/main.py", "README.md", "src/main/java/App.java", ".github/CODEOWNERS", "a.b/c-d_e.txt"],
)
def test_accepts_ordinary_repository_paths(path):
    assert unsafe_path_reason(path) is None


@pytest.mark.parametrize(
    "path",
    [
        "",
        "   ",
        " app/main.py",
        "/etc/passwd",
        "C:/Windows/system32/x.dll",
        "app\\main.py",
        "../outside.py",
        "app/../../outside.py",
        "./app/main.py",
        "app//main.py",
        "app/",
        ".git/config",
        "sub/.git/hooks/pre-commit",
        ".github/workflows/ci.yml",
    ],
)
def test_rejects_unsafe_paths(path):
    assert unsafe_path_reason(path) is not None
