import pytest
from fastapi.testclient import TestClient

from app.analyzer import RepositoryAnalyzer
from app.generator import CodeGenerator
from app.llm import LLMOutputError, LLMUnavailableError
from app.main import app, get_analyzer, get_generator, get_planner
from app.planner import TaskPlanner
from tests.fakes import VALID_ANALYSIS, VALID_PLAN, make_context, make_llm

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
