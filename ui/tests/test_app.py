"""Renders the Streamlit page headlessly against a fake API."""

from pathlib import Path

import pytest
from streamlit.testing.v1 import AppTest

import api

APP = str(Path(__file__).resolve().parents[1] / "app.py")

PROJECT = {"id": 5, "name": "Demo", "githubOwner": "octocat", "githubRepoName": "demo"}

COMPLETED_RUN = {
    "id": "run-1",
    "task": "Add input validation",
    "status": "COMPLETED",
    "active": False,
    "analysis": {
        "summary": "A registration service.",
        "projectType": "FastAPI service",
        "keyComponents": ["app/main.py"],
        "dependencies": ["fastapi"],
        "architectureNotes": "One module per layer.",
        "relevantFiles": ["app/models.py"],
    },
    "plan": {
        "taskUnderstanding": "Validate registration input.",
        "filesToModify": ["app/models.py"],
        "filesToCreate": [],
        "steps": [{"order": 1, "description": "Add validators.", "files": ["app/models.py"]}],
    },
    "result": {
        "outcome": "passed",
        "finalChanges": [
            {
                "path": "app/models.py",
                "action": "modify",
                "content": "email: str\npassword: str  # min 8\n",
                "explanation": "Validate the password.",
            }
        ],
        "attempts": [
            {
                "number": 1,
                "diagnosis": {"rootCause": "Missing package.", "fixStrategy": "Add it."},
                "sandboxResult": {
                    "outcome": "tests_failed",
                    "exitCode": 2,
                    "command": "python -m pytest -q",
                    "stdout": "ImportError",
                    "stderr": "",
                },
            },
            {
                "number": 2,
                "diagnosis": None,
                "sandboxResult": {
                    "outcome": "passed",
                    "exitCode": 0,
                    "command": "python -m pytest -q",
                    "stdout": "5 passed",
                    "stderr": "",
                },
            },
        ],
    },
    "pullRequestNumber": 7,
    "pullRequestUrl": "https://github.com/octocat/demo/pull/7",
    "errorMessage": None,
    "sandboxSeconds": 27.4,
    "estimatedCostUsd": 0.0008,
}


class FakeApi:
    connected = True
    run_record = COMPLETED_RUN
    project_list = [PROJECT]
    connected_calls: list = []
    created: list = []

    def __init__(self, base_url, token=None, client=None):
        self.token = token

    def github_connection(self):
        return {"connected": self.connected, "githubUsername": "octocat"}

    def github_authorization_url(self):
        return "https://github.com/login/oauth/authorize?state=abc"

    def projects(self):
        return self.project_list

    def repositories(self):
        return [{"name": "demo", "full_name": "octocat/demo"}, {"full_name": "octocat/api"}]

    def connect_repository(self, project_id, owner, repository):
        self.connected_calls.append((project_id, owner, repository))

    def create_project(self, name, description):
        self.created.append(name)
        return {"id": 9, "name": name}

    def runs(self, project_id):
        return [self.run_record]

    def run(self, run_id):
        return self.run_record

    def file(self, project_id, path):
        return {"path": path, "content": "email: str\npassword: str\n", "sha": "s"}


@pytest.fixture
def fake_api(monkeypatch):
    monkeypatch.setattr(FakeApi, "connected", True)
    monkeypatch.setattr(FakeApi, "run_record", COMPLETED_RUN)
    monkeypatch.setattr(FakeApi, "project_list", [PROJECT])
    monkeypatch.setattr(FakeApi, "connected_calls", [])
    monkeypatch.setattr(FakeApi, "created", [])
    monkeypatch.setattr(api, "ForgeApi", FakeApi)
    return FakeApi


def render(**session) -> AppTest:
    app = AppTest.from_file(APP, default_timeout=20)
    for key, value in session.items():
        app.session_state[key] = value
    return app.run()


def text_of(elements) -> str:
    return "\n".join(str(element.value) for element in elements)


def test_signed_out_visitor_is_asked_to_sign_in(fake_api):
    app = render()

    assert not app.exception
    assert "Sign in to begin." in text_of(app.info)


def test_user_without_github_is_offered_the_connect_link(fake_api, monkeypatch):
    monkeypatch.setattr(FakeApi, "connected", False)

    app = render(token="jwt", email="a@b.com")

    assert not app.exception
    assert "Connect your GitHub account to continue." in text_of(app.info)


def test_completed_run_shows_plan_diff_attempts_and_pull_request(fake_api):
    app = render(token="jwt", email="a@b.com", run_id="run-1")

    assert not app.exception
    assert "Tests passed and a pull request is open." in text_of(app.success)

    page = text_of(app.markdown) + text_of(app.code) + text_of(app.caption)
    assert "Validate registration input." in page
    assert "1. Add validators." in page
    # The diff is computed against the file as it is on GitHub.
    assert "-password: str" in page
    assert "+password: str  # min 8" in page
    assert "**Root cause:** Missing package." in page
    assert "5 passed" in page
    assert "Estimated E2B credit used: $0.0008" in page


def test_failed_run_explains_why_there_is_no_pull_request(fake_api, monkeypatch):
    failed = {
        **COMPLETED_RUN,
        "status": "TESTS_FAILED",
        "pullRequestUrl": None,
        "pullRequestNumber": None,
        "errorMessage": "Tests still failing after 3 attempts.",
    }
    monkeypatch.setattr(FakeApi, "run_record", failed)

    app = render(token="jwt", email="a@b.com", run_id="run-1")

    assert not app.exception
    assert "No pull request." in text_of(app.error)
    assert "Tests still failing after 3 attempts." in text_of(app.caption)


# --- regressions from the first manual use of the UI -------------------------
# A mistyped repository name returned 404 from GitHub, and every retry created
# another empty project.


def test_repository_is_chosen_from_github_not_typed(fake_api):
    app = render(token="jwt", email="a@b.com")

    assert not app.exception
    choices = app.selectbox(key="new-repo").options
    assert choices == ["octocat/api", "octocat/demo"]
    # The only free-text field left in the form is the project name.
    assert [field.label for field in app.text_input] == ["Name"]


def test_project_without_a_repository_can_be_connected_instead_of_recreated(
    fake_api, monkeypatch
):
    unlinked = {"id": 3, "name": "Test", "githubOwner": None, "githubRepoName": None}
    monkeypatch.setattr(FakeApi, "project_list", [unlinked])

    app = render(token="jwt", email="a@b.com")

    assert not app.exception
    assert "has no repository yet" in text_of(app.caption)
    assert "Choose or create a project that has a repository connected" in text_of(app.info)

    app.selectbox(key="connect-repo").select("octocat/demo")
    connect_button = next(b for b in app.button if b.label == "Connect repository")
    connect_button.click().run()

    assert FakeApi.connected_calls == [(3, "octocat", "demo")]
    # No second project was created along the way.
    assert FakeApi.created == []


def test_new_project_needs_a_name(fake_api):
    app = render(token="jwt", email="a@b.com")

    create_button = next(b for b in app.button if b.label == "Create and connect")
    create_button.click().run()

    assert "Enter a name and choose a repository." in text_of(app.error)
    assert FakeApi.created == []

