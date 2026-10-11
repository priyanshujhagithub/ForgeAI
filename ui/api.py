"""HTTP client for the ForgeAI Java API. The UI talks to nothing else."""

from typing import Any

import httpx


class ApiError(Exception):
    def __init__(self, status: int, detail: str):
        super().__init__(detail)
        self.status = status
        self.detail = detail


class ForgeApi:
    def __init__(
        self,
        base_url: str,
        token: str | None = None,
        client: httpx.Client | None = None,
    ):
        self.token = token
        self._client = client or httpx.Client(base_url=base_url, timeout=60.0)

    # --- auth ---------------------------------------------------------------

    def register(self, email: str, password: str) -> None:
        self._request("POST", "/api/v1/auth/register", json={"email": email, "password": password})

    def login(self, email: str, password: str) -> str:
        body = self._request(
            "POST", "/api/v1/auth/login", json={"email": email, "password": password}
        )
        self.token = body["accessToken"]
        return self.token

    # --- GitHub -------------------------------------------------------------

    def github_connection(self) -> dict:
        return self._request("GET", "/api/v1/github/connection")

    def github_authorization_url(self) -> str:
        return self._request("GET", "/api/v1/auth/github/url")["authorizationUrl"]

    def repositories(self) -> list[dict]:
        return self._request("GET", "/api/v1/github/repositories")

    def file(self, project_id: int, path: str) -> dict:
        return self._request(
            "GET", f"/api/v1/github/projects/{project_id}/file", params={"path": path}
        )

    # --- projects -----------------------------------------------------------

    def projects(self) -> list[dict]:
        return self._request("GET", "/api/v1/projects")

    def create_project(self, name: str, description: str) -> dict:
        return self._request(
            "POST", "/api/v1/projects", json={"name": name, "description": description}
        )

    def delete_project(self, project_id: int) -> None:
        self._request("DELETE", f"/api/v1/projects/{project_id}")

    def connect_repository(self, project_id: int, owner: str, repository: str) -> dict:
        return self._request(
            "POST",
            f"/api/v1/projects/{project_id}/github",
            json={"owner": owner, "repository": repository},
        )

    # --- agent runs ---------------------------------------------------------

    def start_run(self, project_id: int, task: str) -> dict:
        return self._request("POST", f"/api/v1/projects/{project_id}/runs", json={"task": task})

    def run(self, run_id: str) -> dict:
        return self._request("GET", f"/api/v1/runs/{run_id}")

    def runs(self, project_id: int) -> list[dict]:
        return self._request("GET", f"/api/v1/projects/{project_id}/runs")

    # ------------------------------------------------------------------------

    def _request(self, method: str, path: str, **kwargs: Any) -> Any:
        headers = {"Authorization": f"Bearer {self.token}"} if self.token else {}
        try:
            response = self._client.request(method, path, headers=headers, **kwargs)
        except httpx.HTTPError as exc:
            raise ApiError(0, f"Cannot reach the ForgeAI API: {exc}") from exc

        if response.is_success:
            return response.json() if response.content else None
        raise ApiError(response.status_code, _detail(response))


def _detail(response: httpx.Response) -> str:
    """The API reports errors as RFC 9457 problem documents with a `detail`."""
    try:
        body = response.json()
    except ValueError:
        return response.text or f"HTTP {response.status_code}"
    if isinstance(body, dict) and body.get("detail"):
        return str(body["detail"])
    return f"HTTP {response.status_code}"
