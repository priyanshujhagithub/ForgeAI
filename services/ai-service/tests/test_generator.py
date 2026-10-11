import pytest

from app.generator import CodeGenerator, FileTooLargeError, InvalidPlanError
from app.llm import LLMOutputError
from app.schemas import CodeChange, EngineeringPlan
from tests.fakes import VALID_PLAN, make_context, make_llm

TASK = "Add input validation to the greeting."
PLAN = EngineeringPlan.model_validate(VALID_PLAN)

APP_EDIT = {"content": "print('validated')\n", "explanation": "Validate first."}
TEST_EDIT = {"content": "def test_app():\n    assert True\n", "explanation": "Cover it."}


def generate(replies, context=None, plan=PLAN, **settings):
    llm, client = make_llm(replies, **settings)
    result = CodeGenerator(llm).generate(context or make_context(), TASK, plan)
    return result, client


def test_generates_one_change_per_planned_file_in_plan_order():
    result, client = generate([APP_EDIT, TEST_EDIT])

    assert [(change.path, change.action) for change in result.changes] == [
        ("app.py", "modify"),
        ("tests/test_app.py", "create"),
    ]
    assert result.changes[0].content == "print('validated')\n"
    assert result.changes[0].explanation == "Validate first."
    assert len(client.requests) == 2


def test_prompt_for_an_existing_file_shows_its_current_content():
    _, client = generate([APP_EDIT, TEST_EDIT])

    prompt = client.requests[0]["messages"][1]["content"]
    assert "Rewrite the existing file: app.py" in prompt
    assert "===== CURRENT CONTENT =====\nprint('hi')\n===== END CURRENT CONTENT =====" in prompt
    assert "Keep every existing function, class, fixture and import" in prompt
    # First write: current and original are the same, so the original is not repeated.
    assert "===== ORIGINAL CONTENT =====" not in prompt
    assert f"===== TASK =====\n{TASK}" in prompt
    assert "1. Add validation to app.py. (files: app.py)" in prompt
    # The file being rewritten is not repeated in the reference evidence.
    assert "===== FILE: app.py =====" not in prompt


def test_later_files_see_the_new_version_of_earlier_files():
    _, client = generate([APP_EDIT, TEST_EDIT])

    prompt = client.requests[1]["messages"][1]["content"]
    assert "Write the new file: tests/test_app.py" in prompt
    assert "===== FILE: app.py =====\nprint('validated')" in prompt
    assert "print('hi')" not in prompt


def test_model_cannot_choose_the_path_or_action():
    # Whatever the model says, path and action come from the plan.
    sneaky = {**APP_EDIT, "path": "../../etc/passwd", "action": "delete"}

    result, _ = generate([sneaky, TEST_EDIT])

    assert result.changes[0].path == "app.py"
    assert result.changes[0].action == "modify"


def test_unchanged_content_triggers_repair():
    no_op = {"content": "print('hi')", "explanation": "Nothing."}

    result, client = generate([no_op, APP_EDIT, TEST_EDIT])

    assert result.changes[0].content == "print('validated')\n"
    assert "identical to the current file" in client.requests[1]["messages"][-1]["content"]


def test_placeholder_comment_instead_of_code_triggers_repair():
    elided = {"content": "# ... rest of the file unchanged\nprint('x')", "explanation": "x"}

    _, client = generate([elided, APP_EDIT, TEST_EDIT])

    assert "leave out existing code" in client.requests[1]["messages"][-1]["content"]


def test_drastically_shortened_file_triggers_repair():
    context = make_context({"README.md": "# Demo", "app.py": "x = 1\n" * 200})
    stub = {"content": "x = 2\n", "explanation": "x"}
    full = {"content": "x = 2\n" * 200, "explanation": "x"}

    result, client = generate([stub, full, TEST_EDIT], context=context)

    assert result.changes[0].content == "x = 2\n" * 200
    assert len(client.requests) == 3


def test_empty_content_is_rejected_after_repair_fails():
    empty = {"content": "   ", "explanation": "x"}

    with pytest.raises(LLMOutputError):
        generate([empty, empty])


def test_markdown_fence_around_the_file_is_removed():
    fence = "`" * 3
    fenced = {"content": f"{fence}python\nprint('validated')\n{fence}", "explanation": "x"}

    result, _ = generate([fenced, TEST_EDIT])

    assert result.changes[0].content == "print('validated')\n"


def test_plan_that_does_not_fit_the_repository_is_refused_before_any_model_call():
    bad_plan = EngineeringPlan.model_validate(
        {**VALID_PLAN, "filesToModify": ["missing.py"]}
    )

    with pytest.raises(InvalidPlanError, match="missing.py"):
        generate([], plan=bad_plan)


def test_file_too_large_for_the_model_is_refused():
    context = make_context({"README.md": "# Demo", "app.py": "x" * 500})

    with pytest.raises(FileTooLargeError, match="app.py"):
        generate([], context=context, generation_max_file_chars=100)


def test_revision_starts_from_the_previous_attempt_and_includes_feedback():
    llm, client = make_llm([{"content": "print('fixed')\n", "explanation": "Fix."}])
    previous = [
        CodeChange(path="app.py", action="modify", content="print('broken')\n", explanation="")
    ]

    change = CodeGenerator(llm).generate_file(
        make_context(),
        TASK,
        PLAN,
        "app.py",
        earlier=previous,
        feedback="NameError: name 'validate' is not defined",
    )

    prompt = client.requests[0]["messages"][1]["content"]
    assert change.content == "print('fixed')\n"
    assert change.action == "modify"
    assert "===== CURRENT CONTENT =====\nprint('broken')" in prompt
    assert "NameError: name 'validate' is not defined" in prompt
    # Regression from a real run: the first attempt dropped a test fixture and the
    # fix could not restore it, because the model never saw the pre-task file.
    assert "===== ORIGINAL CONTENT =====\nprint('hi')\n===== END ORIGINAL CONTENT =====" in prompt


def test_revising_a_file_created_by_this_task_has_no_original_to_show():
    llm, client = make_llm([{"content": "def test_app():\n    assert 1\n", "explanation": "Fix."}])
    previous = [
        CodeChange(
            path="tests/test_app.py", action="create", content="broken\n", explanation=""
        )
    ]

    change = CodeGenerator(llm).generate_file(
        make_context(), TASK, PLAN, "tests/test_app.py", earlier=previous, feedback="SyntaxError"
    )

    prompt = client.requests[0]["messages"][1]["content"]
    assert change.action == "create"
    assert "===== CURRENT CONTENT =====\nbroken" in prompt
    assert "===== ORIGINAL CONTENT =====" not in prompt
