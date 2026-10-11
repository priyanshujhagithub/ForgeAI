from app.correction_loop import CorrectionLoop
from app.generator import CodeGenerator
from app.sandbox import SandboxSetupError, SandboxUnavailableError, SandboxUsage
from app.schemas import EngineeringPlan, RepositoryRef
from tests.fakes import (
    VALID_PLAN,
    FakeSandboxRunner,
    make_context,
    make_llm,
    sandbox_result,
)

TASK = "Add input validation to the greeting."
PLAN = EngineeringPlan.model_validate(VALID_PLAN)
REPO = RepositoryRef(clone_url="https://github.com/octocat/demo.git", base_branch="main")

APP_V1 = {"content": "print('v1')\n", "explanation": "First try."}
TEST_V1 = {"content": "def test_app():\n    assert True\n", "explanation": "Cover it."}
APP_V2 = {"content": "print('v2')\n", "explanation": "Fix the import."}
APP_V3 = {"content": "print('v3')\n", "explanation": "Fix it again."}
DIAGNOSIS = {
    "rootCause": "app.py imports a name that does not exist.",
    "fixStrategy": "Use the correct import.",
    "filesToChange": ["app.py"],
}


def run_loop(llm_replies, sandbox_results, context=None, **settings):
    llm, client = make_llm(llm_replies, **settings)
    runner = FakeSandboxRunner(sandbox_results)
    loop = CorrectionLoop(CodeGenerator(llm), runner, llm)
    result = loop.run(context or make_context(), TASK, PLAN, REPO)
    return result, client, runner


def contents(changes):
    return {change.path: change.content for change in changes}


def test_passing_first_attempt_needs_no_correction():
    result, client, runner = run_loop([APP_V1, TEST_V1], [sandbox_result("passed")])

    assert result.outcome == "passed"
    assert len(result.attempts) == 1
    assert result.attempts[0].diagnosis is None
    assert contents(result.final_changes)["app.py"] == "print('v1')\n"
    assert len(client.requests) == 2  # two files generated, no diagnosis call
    assert runner.closed == 1


def test_failure_is_diagnosed_then_only_the_named_file_is_rewritten():
    result, client, runner = run_loop(
        [APP_V1, TEST_V1, DIAGNOSIS, APP_V2],
        [sandbox_result("tests_failed", stderr="ImportError: MinLength"), sandbox_result("passed")],
    )

    assert result.outcome == "passed"
    assert [attempt.number for attempt in result.attempts] == [1, 2]
    # The diagnosis sits on the attempt that failed, not on the one that fixed it.
    assert result.attempts[0].diagnosis.root_cause == DIAGNOSIS["rootCause"]
    assert result.attempts[1].diagnosis is None
    # app.py was rewritten; the test file from attempt 1 was kept as it was.
    assert contents(result.final_changes) == {
        "app.py": "print('v2')\n",
        "tests/test_app.py": "def test_app():\n    assert True\n",
    }
    assert contents(runner.tested[0])["app.py"] == "print('v1')\n"
    assert contents(runner.tested[1])["app.py"] == "print('v2')\n"


def test_diagnosis_prompt_carries_the_exact_test_output():
    _, client, _ = run_loop(
        [APP_V1, TEST_V1, DIAGNOSIS, APP_V2],
        [
            sandbox_result("tests_failed", stdout="collected 0 items", stderr="ImportError: X"),
            sandbox_result("passed"),
        ],
    )

    system, user = client.requests[2]["messages"]
    assert "First identify the root cause" in system["content"]
    assert "ImportError: X" in user["content"]
    assert "collected 0 items" in user["content"]
    assert "print('v1')" in user["content"]
    assert TASK in user["content"]


def test_revision_prompt_carries_the_diagnosis_and_the_failing_version():
    _, client, _ = run_loop(
        [APP_V1, TEST_V1, DIAGNOSIS, APP_V2],
        [sandbox_result("tests_failed", stderr="ImportError: X"), sandbox_result("passed")],
    )

    revision = client.requests[3]["messages"][1]["content"]
    assert "Root cause: app.py imports a name that does not exist." in revision
    assert "Fix strategy: Use the correct import." in revision
    assert "ImportError: X" in revision
    assert "===== CURRENT CONTENT =====\nprint('v1')" in revision


def test_gives_up_after_the_attempt_limit():
    result, client, runner = run_loop(
        [APP_V1, TEST_V1, DIAGNOSIS, APP_V2, DIAGNOSIS, APP_V3],
        [sandbox_result("tests_failed")] * 3,
    )

    assert result.outcome == "tests_failed"
    assert len(result.attempts) == 3
    assert "after 3 attempts" in result.message
    assert contents(result.final_changes)["app.py"] == "print('v3')\n"
    # No diagnosis is requested after the final attempt.
    assert len(client.requests) == 6
    assert len(runner.opened) == 3


def test_attempt_limit_is_configurable():
    result, _, runner = run_loop(
        [APP_V1, TEST_V1], [sandbox_result("tests_failed")], max_attempts=1
    )

    assert result.outcome == "tests_failed"
    assert len(runner.opened) == 1


def test_each_attempt_uses_its_own_sandbox_and_usage_is_totalled():
    result, _, runner = run_loop(
        [APP_V1, TEST_V1, DIAGNOSIS, APP_V2],
        [sandbox_result("tests_failed"), sandbox_result("passed")],
    )

    assert len(runner.opened) == 2
    assert runner.closed == 2
    assert result.sandbox_seconds == 20.0
    assert result.estimated_cost_usd == 0.0006


def test_missing_sandbox_keeps_the_generated_changes():
    result, _, _ = run_loop(
        [APP_V1, TEST_V1], [SandboxUnavailableError("E2B_API_KEY is not set")]
    )

    assert result.outcome == "sandbox_unavailable"
    assert result.attempts == []
    assert "E2B_API_KEY" in result.message
    assert contents(result.final_changes)["app.py"] == "print('v1')\n"


def test_infrastructure_failure_ends_the_loop_without_spending_attempts():
    broken = sandbox_result(
        "infrastructure_error", exit_code=128, message="Could not clone the repository."
    )
    setup_error = SandboxSetupError(broken, SandboxUsage(3.0, 0.0001))

    result, client, runner = run_loop([APP_V1, TEST_V1], [setup_error])

    assert result.outcome == "infrastructure_error"
    assert result.message == "Could not clone the repository."
    assert len(result.attempts) == 1
    assert len(client.requests) == 2  # no diagnosis: the code is not at fault
    assert len(runner.opened) == 1
    assert result.sandbox_seconds == 3.0


def test_unusable_diagnosis_ends_the_loop_with_the_last_changes():
    empty = {**DIAGNOSIS, "filesToChange": []}

    result, _, _ = run_loop(
        [APP_V1, TEST_V1, empty, empty], [sandbox_result("tests_failed")]
    )

    assert result.outcome == "tests_failed"
    assert "Could not produce a fix" in result.message
    assert len(result.attempts) == 1
    assert contents(result.final_changes)["app.py"] == "print('v1')\n"


def test_diagnosis_may_name_an_existing_file_outside_the_plan():
    # e.g. the fix is to add a dependency to a manifest the plan did not mention.
    wider = {**DIAGNOSIS, "filesToChange": ["README.md"]}
    readme = {"content": "# Demo\n\nNow documented.\n", "explanation": "Docs."}

    result, _, runner = run_loop(
        [APP_V1, TEST_V1, wider, readme],
        [sandbox_result("tests_failed"), sandbox_result("passed")],
    )

    assert result.outcome == "passed"
    assert contents(runner.tested[1])["README.md"] == "# Demo\n\nNow documented.\n"
    assert [c.action for c in result.final_changes if c.path == "README.md"] == ["modify"]


def test_diagnosis_cannot_point_outside_the_repository():
    escape = {**DIAGNOSIS, "filesToChange": ["../../etc/passwd"]}

    result, client, _ = run_loop(
        [APP_V1, TEST_V1, escape, DIAGNOSIS, APP_V2],
        [sandbox_result("tests_failed"), sandbox_result("passed")],
    )

    assert result.outcome == "passed"
    assert "../../etc/passwd" in client.requests[3]["messages"][-1]["content"]


def test_tests_run_from_the_manifest_folder():
    context = make_context({"svc/requirements.txt": "pytest", "svc/app.py": "print('hi')"})
    plan_for_nested = {
        **VALID_PLAN,
        "filesToModify": ["svc/app.py"],
        "filesToCreate": [],
        "steps": [{"order": 1, "description": "Edit.", "files": ["svc/app.py"]}],
    }
    llm, _ = make_llm([APP_V1])
    runner = FakeSandboxRunner([sandbox_result("passed")])

    CorrectionLoop(CodeGenerator(llm), runner, llm).run(
        context, TASK, EngineeringPlan.model_validate(plan_for_nested), REPO
    )

    assert runner.opened[0] == (REPO, "PYTHON", "svc")


# --- regressions from the first real run ------------------------------------
# The model diagnosed "email-validator is not installed" correctly, named a
# source file instead of the manifest, and then returned that file unchanged.


def test_diagnosis_is_kept_even_when_no_fix_can_be_produced():
    unchanged = {"content": "print('v1')\n", "explanation": "Nothing to change."}

    result, _, _ = run_loop(
        [APP_V1, TEST_V1, DIAGNOSIS, unchanged], [sandbox_result("tests_failed")]
    )

    assert result.outcome == "tests_failed"
    assert "changed no files" in result.message
    assert result.attempts[0].diagnosis.root_cause == DIAGNOSIS["rootCause"]


def test_diagnosis_prompt_shows_the_manifest_and_says_it_installs_dependencies():
    context = make_context(
        {"README.md": "# Demo", "requirements.txt": "fastapi\npytest\n", "app.py": "print('hi')"}
    )

    _, client, _ = run_loop(
        [APP_V1, TEST_V1, DIAGNOSIS, APP_V2],
        [sandbox_result("tests_failed"), sandbox_result("passed")],
        context=context,
    )

    prompt = client.requests[2]["messages"][1]["content"]
    assert "Dependencies are installed from requirements.txt" in prompt
    assert "===== FILE: requirements.txt (unchanged) =====\nfastapi\npytest" in prompt
    assert "- requirements.txt" in prompt
    assert "- tests/test_app.py" in prompt  # files created by the change are listed too


def test_manifest_is_found_inside_a_nested_project():
    context = make_context({"svc/requirements.txt": "pytest\n", "svc/app.py": "print('hi')"})
    nested_plan = EngineeringPlan.model_validate(
        {
            **VALID_PLAN,
            "filesToModify": ["svc/app.py"],
            "filesToCreate": [],
            "steps": [{"order": 1, "description": "Edit.", "files": ["svc/app.py"]}],
        }
    )
    fix_manifest = {**DIAGNOSIS, "filesToChange": ["svc/requirements.txt"]}
    new_manifest = {"content": "pytest\nemail-validator\n", "explanation": "Add the package."}
    llm, client = make_llm([APP_V1, fix_manifest, new_manifest])
    runner = FakeSandboxRunner([sandbox_result("tests_failed"), sandbox_result("passed")])

    result = CorrectionLoop(CodeGenerator(llm), runner, llm).run(context, TASK, nested_plan, REPO)

    assert "Dependencies are installed from svc/requirements.txt" in (
        client.requests[1]["messages"][1]["content"]
    )
    assert result.outcome == "passed"
    assert contents(runner.tested[1])["svc/requirements.txt"] == "pytest\nemail-validator\n"


def test_a_fix_may_leave_one_named_file_unchanged_if_another_changes():
    both = {**DIAGNOSIS, "filesToChange": ["app.py", "README.md"]}
    app_unchanged = {"content": "print('v1')\n", "explanation": "Already fine."}
    readme = {"content": "# Demo\n\nUpdated.\n", "explanation": "Docs."}

    result, _, runner = run_loop(
        [APP_V1, TEST_V1, both, app_unchanged, readme],
        [sandbox_result("tests_failed"), sandbox_result("passed")],
    )

    assert result.outcome == "passed"
    assert contents(runner.tested[1]) == {
        "app.py": "print('v1')\n",
        "tests/test_app.py": "def test_app():\n    assert True\n",
        "README.md": "# Demo\n\nUpdated.\n",
    }


def test_an_untouched_file_named_by_the_diagnosis_is_not_added_as_a_change():
    wider = {**DIAGNOSIS, "filesToChange": ["app.py", "README.md"]}
    readme_same = {"content": "# Demo", "explanation": "No change needed."}

    result, _, _ = run_loop(
        [APP_V1, TEST_V1, wider, APP_V2, readme_same],
        [sandbox_result("tests_failed"), sandbox_result("passed")],
    )

    assert result.outcome == "passed"
    assert "README.md" not in contents(result.final_changes)

