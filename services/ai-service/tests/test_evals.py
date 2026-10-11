import json

from app.correction_loop import CorrectionLoop
from app.generator import CodeGenerator
from app.planner import TaskPlanner
from app.sandbox import SandboxUnavailableError
from app.schemas import RepositoryRef
from evals.run import TASKS_FILE, TaskOutcome, run_task, summarise
from evals.snapshot import detect_project_type
from tests.fakes import (
    VALID_PLAN,
    FakeSandboxRunner,
    make_context,
    make_llm,
    sandbox_result,
)

REPO = RepositoryRef(clone_url="https://github.com/octocat/demo.git", base_branch="main")
TASK = {"id": "email-validation", "task": "Validate emails.", "acceptance": "def test_x(): pass\n"}

APP_EDIT = {"content": "print('validated')\n", "explanation": "Validate first."}
TEST_EDIT = {"content": "def test_app():\n    assert True\n", "explanation": "Cover it."}


def evaluate(llm_replies, sandbox_results, **settings):
    llm, _ = make_llm(llm_replies, **settings)
    runner = FakeSandboxRunner(sandbox_results)
    loop = CorrectionLoop(CodeGenerator(llm), runner, llm)
    outcome = run_task(TASK, make_context(), REPO, TaskPlanner(llm), loop, runner)
    return outcome, runner


def test_solved_requires_the_loop_and_the_hidden_test_to_pass():
    outcome, runner = evaluate(
        [VALID_PLAN, APP_EDIT, TEST_EDIT], [sandbox_result("passed"), sandbox_result("passed")]
    )

    assert outcome.loop_outcome == "passed"
    assert outcome.accepted is True
    assert outcome.solved is True
    # The acceptance run applies the agent's changes plus the hidden test.
    assert [change.path for change in runner.tested[1]] == [
        "app.py",
        "tests/test_app.py",
        "tests/test_acceptance_email_validation.py",
    ]
    assert outcome.sandbox_seconds == 20.0


def test_passing_its_own_tests_is_not_enough_if_the_hidden_test_fails():
    outcome, _ = evaluate(
        [VALID_PLAN, APP_EDIT, TEST_EDIT],
        [sandbox_result("passed"), sandbox_result("tests_failed")],
    )

    assert outcome.loop_outcome == "passed"
    assert outcome.accepted is False
    assert outcome.solved is False
    assert outcome.message == "The hidden acceptance test failed."


def test_acceptance_is_not_run_when_the_loop_fails():
    outcome, runner = evaluate(
        [VALID_PLAN, APP_EDIT, TEST_EDIT], [sandbox_result("tests_failed")], max_attempts=1
    )

    assert outcome.loop_outcome == "tests_failed"
    assert outcome.accepted is None
    assert outcome.solved is False
    assert len(runner.opened) == 1


def test_unusable_plan_is_recorded_not_raised():
    outcome, _ = evaluate(["not json", "still not json"], [])

    assert outcome.loop_outcome == "no_valid_output"
    assert outcome.attempts == 0
    assert outcome.solved is False


def test_unavailable_sandbox_during_acceptance_is_reported():
    outcome, _ = evaluate(
        [VALID_PLAN, APP_EDIT, TEST_EDIT],
        [sandbox_result("passed"), SandboxUnavailableError("credit exhausted")],
    )

    assert outcome.accepted is None
    assert "credit exhausted" in outcome.message


def test_summary_reports_solved_count_and_cost():
    outcomes = [
        TaskOutcome("a", "passed", 2, True, 600.0, 30.0, 0.0009),
        TaskOutcome("b", "passed", 1, False, 300.0, 20.0, 0.0006),
        TaskOutcome("c", "tests_failed", 3, None, 900.0, 45.0, 0.0014),
    ]

    summary = summarise(outcomes, "forgeai-qwen3")

    assert "Solved (loop passed and hidden test passed): 1/3" in summary
    assert "Loop reported passing: 2/3" in summary
    assert "Mean attempts: 2.0" in summary
    assert "Total time: 30.0 min" in summary
    assert "estimated $0.0029 of E2B credit" in summary
    assert "| b | passed | 1 | failed | 300 |" in summary
    assert "| c | tests_failed | 3 | not run | 900 |" in summary


def test_task_file_is_well_formed():
    tasks = json.loads(TASKS_FILE.read_text(encoding="utf-8"))

    assert len(tasks) >= 8
    assert len({task["id"] for task in tasks}) == len(tasks)
    for task in tasks:
        assert task["task"].strip()
        compile(task["acceptance"], f"<{task['id']}>", "exec")
        assert "def test_" in task["acceptance"]


def test_project_type_is_detected_from_the_manifest():
    assert detect_project_type(["README.md", "svc/requirements.txt"]) == "PYTHON"
    assert detect_project_type(["pom.xml", "package.json"]) == "MAVEN"
    assert detect_project_type(["README.md"]) == "UNKNOWN"
