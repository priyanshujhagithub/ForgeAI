from app.prompting import build_repository_evidence
from tests.fakes import make_context


def build(context, **overrides):
    options = {"budget_chars": 10_000, "max_file_chars": 5_000, **overrides}
    return build_repository_evidence(context, **options)


def test_includes_metadata_tree_and_contents():
    evidence = build(make_context())

    assert "Repository: octocat/demo" in evidence
    assert "Detected project type: PYTHON" in evidence
    assert "- README.md" in evidence
    assert "===== FILE: app.py =====" in evidence
    assert "print('hi')" in evidence
    assert "2 of 2 files shown" in evidence


def test_truncates_long_files_and_says_so():
    evidence = build(make_context({"big.txt": "x" * 1_000}), max_file_chars=300)

    assert "x" * 300 in evidence
    assert "x" * 301 not in evidence
    assert "[... truncated 700 characters ...]" in evidence


def test_stops_at_budget_but_still_lists_every_path():
    files = {f"file{i}.txt": "y" * 400 for i in range(5)}

    evidence = build(make_context(files), budget_chars=900)

    assert "2 of 5 files shown" in evidence
    assert "===== FILE: file1.txt =====" in evidence
    assert "===== FILE: file2.txt =====" not in evidence
    assert "- file4.txt" in evidence


def test_focus_paths_come_first():
    files = {"a.txt": "A" * 400, "b.txt": "B" * 400, "c.txt": "C" * 400}

    evidence = build(make_context(files), budget_chars=500, focus_paths=["c.txt", "missing.txt"])

    assert "===== FILE: c.txt =====" in evidence
    assert "===== FILE: a.txt =====" not in evidence
