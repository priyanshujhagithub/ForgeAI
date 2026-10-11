import pytest
from fastapi.testclient import TestClient

from app.analyzer import RepositoryAnalyzer
from app.correction_loop import CorrectionLoop
from app.generator import CodeGenerator
from app.llm import LLMOutputError, LLMUnavailableError
from app.main import (
    app,
    get_analyzer,
    get_correction_loop,
    get_generator,
    get_planner,
    get_tracer,
)
from app.planner import TaskPlanner
from app.sandbox import SandboxUnavailableError
from app.tracing import Tracer
from tests.fakes import (
    VALID_ANALYSIS,
    VALID_PLAN,
    FakeSandboxRunner,
    make_context,
    make_llm,
    sandbox_result,
)

REQUEST_BODY = make_context().model_dump()


@pytest.fixture
def client():
    yield TestClient(app)
    app.dependency_overrides.clear()


def use_analyzer(analyzer):
    app.dependency_overrides[get_analyzer] = lambda: analyzer


class FailingAnalyzer:
    def __init__(self, error: Exception):
        self.error = error

    def analyze(self, context):
        raise self.error


def test_health(client):
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_analyze_returns_the_camel_case_contract_java_expects(client):
    llm, _ = make_llm([VALID_ANALYSIS])
    use_analyzer(RepositoryAnalyzer(llm))

    response = client.post("/api/v1/analyze", json=REQUEST_BODY)

    assert response.status_code == 200
    assert response.json() == VALID_ANALYSIS


def test_analyze_rejects_a_malformed_request(client):
    response = client.post("/api/v1/analyze", json={"owner": "octocat"})

    assert response.status_code == 422


def test_invalid_model_output_maps_to_502(client):
    use_analyzer(FailingAnalyzer(LLMOutputError("bad output")))

    response = client.post("/api/v1/analyze", json=REQUEST_BODY)

    assert response.status_code == 502
    assert response.json() == {"detail": "bad output"}


def test_unreachable_provider_maps_to_503(client):
    use_analyzer(FailingAnalyzer(LLMUnavailableError("provider down")))

    response = client.post("/api/v1/analyze", json=REQUEST_BODY)

    assert response.status_code == 503
    assert response.json() == {"detail": "provider down"}


def test_plan_returns_the_camel_case_contract_java_expects(client):
    llm, fake = make_llm([VALID_PLAN])
    app.dependency_overrides[get_planner] = lambda: TaskPlanner(llm)

    response = client.post(
        "/api/v1/plan",
        json={"context": REQUEST_BODY, "task": "Add validation.", "analysis": VALID_ANALYSIS},
    )

    assert response.status_code == 200
    assert response.json() == VALID_PLAN
    assert "Add validation." in fake.requests[0]["messages"][1]["content"]


def test_plan_rejects_a_blank_task(client):
    response = client.post("/api/v1/plan", json={"context": REQUEST_BODY, "task": ""})

    assert response.status_code == 422


def test_generate_returns_the_camel_case_contract_java_expects(client):
    llm, _ = make_llm(
        [
            {"content": "print('validated')\n", "explanation": "Validate first."},
            {"content": "def test_app():\n    assert True\n", "explanation": "Cover it."},
        ]
    )
    app.dependency_overrides[get_generator] = lambda: CodeGenerator(llm)

    response = client.post(
        "/api/v1/generate",
        json={"context": REQUEST_BODY, "task": "Add validation.", "plan": VALID_PLAN},
    )

    assert response.status_code == 200
    assert response.json() == {
        "changes": [
            {
                "path": "app.py",
                "action": "modify",
                "content": "print('validated')\n",
                "explanation": "Validate first.",
            },
            {
                "path": "tests/test_app.py",
                "action": "create",
                "content": "def test_app():\n    assert True\n",
                "explanation": "Cover it.",
            },
        ]
    }


def test_generate_refuses_a_plan_that_does_not_fit_the_repository(client):
    llm, fake = make_llm([])
    app.dependency_overrides[get_generator] = lambda: CodeGenerator(llm)
    bad_plan = {**VALID_PLAN, "filesToModify": ["missing.py"]}

    response = client.post(
        "/api/v1/generate",
        json={"context": REQUEST_BODY, "task": "Add validation.", "plan": bad_plan},
    )

    assert response.status_code == 422
    assert "missing.py" in response.json()["detail"]
    assert fake.requests == []


def solve(client, llm_replies, sandbox_results, **extra):
    llm, fake = make_llm(llm_replies)
    runner = FakeSandboxRunner(sandbox_results)
    app.dependency_overrides[get_planner] = lambda: TaskPlanner(llm)
    app.dependency_overrides[get_correction_loop] = lambda: CorrectionLoop(
        CodeGenerator(llm), runner, llm
    )
    app.dependency_overrides[get_tracer] = lambda: Tracer()
    body = {
        "context": REQUEST_BODY,
        "task": "Add validation.",
        "repository": {
            "cloneUrl": "https://github.com/octocat/demo.git",
            "baseBranch": "main",
            "accessToken": "ghs_secret_token_value",
        },
        **extra,
    }
    return client.post("/api/v1/solve", json=body), fake, runner


APP_EDIT = {"content": "print('validated')\n", "explanation": "Validate first."}
TEST_EDIT = {"content": "def test_app():\n    assert True\n", "explanation": "Cover it."}


def test_solve_plans_generates_and_tests(client):
    response, fake, runner = solve(
        client, [VALID_PLAN, APP_EDIT, TEST_EDIT], [sandbox_result("passed")]
    )

    assert response.status_code == 200
    body = response.json()
    assert body["plan"] == VALID_PLAN
    assert body["result"]["outcome"] == "passed"
    assert [c["path"] for c in body["result"]["finalChanges"]] == ["app.py", "tests/test_app.py"]
    assert body["result"]["attempts"][0]["sandboxResult"]["exitCode"] == 0
    assert body["result"]["sandboxSeconds"] == 10.0
    assert len(fake.requests) == 3
    # The token reaches the sandbox runner and nothing else.
    assert runner.opened[0][0].access_token == "ghs_secret_token_value"
    assert "ghs_secret_token_value" not in response.text
    assert all("ghs_secret_token_value" not in str(r["messages"]) for r in fake.requests)


def test_solve_uses_a_supplied_plan_without_planning_again(client):
    response, fake, _ = solve(
        client, [APP_EDIT, TEST_EDIT], [sandbox_result("passed")], plan=VALID_PLAN
    )

    assert response.status_code == 200
    assert len(fake.requests) == 2


def test_solve_reports_an_unavailable_sandbox_as_a_result_not_an_error(client):
    response, _, _ = solve(
        client,
        [VALID_PLAN, APP_EDIT, TEST_EDIT],
        [SandboxUnavailableError("E2B_API_KEY is not set")],
    )

    assert response.status_code == 200
    assert response.json()["result"]["outcome"] == "sandbox_unavailable"
    assert len(response.json()["result"]["finalChanges"]) == 2
