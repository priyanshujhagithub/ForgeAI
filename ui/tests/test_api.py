import httpx
import pytest

from api import ApiError, ForgeApi


def make_api(handler, token=None) -> ForgeApi:
    client = httpx.Client(base_url="http://forgeai.test", transport=httpx.MockTransport(handler))
    return ForgeApi("http://forgeai.test", token=token, client=client)


def test_login_stores_the_access_token():
    def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.path == "/api/v1/auth/login"
        assert "authorization" not in request.headers
        return httpx.Response(200, json={"accessToken": "jwt-123"})

    api = make_api(handler)

    assert api.login("a@b.com", "pw") == "jwt-123"
    assert api.token == "jwt-123"


def test_authenticated_calls_send_the_bearer_token():
    seen = {}

    def handler(request: httpx.Request) -> httpx.Response:
        seen["authorization"] = request.headers["authorization"]
        seen["path"] = request.url.path
        return httpx.Response(202, json={"id": "run-1", "status": "QUEUED"})

    run = make_api(handler, token="jwt-123").start_run(5, "Add validation")

    assert run["id"] == "run-1"
    assert seen == {"authorization": "Bearer jwt-123", "path": "/api/v1/projects/5/runs"}


def test_problem_detail_becomes_a_readable_error():
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            409,
            json={"status": 409, "title": "Conflict", "detail": "GitHub account is not connected"},
        )

    with pytest.raises(ApiError) as raised:
        make_api(handler, token="t").repositories()

    assert raised.value.status == 409
    assert raised.value.detail == "GitHub account is not connected"


def test_unreachable_api_is_reported_not_raised_as_a_transport_error():
    def handler(request: httpx.Request) -> httpx.Response:
        raise httpx.ConnectError("connection refused")

    with pytest.raises(ApiError, match="Cannot reach the ForgeAI API"):
        make_api(handler).projects()


def test_empty_success_body_returns_none():
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200)

    assert make_api(handler).register("a@b.com", "pw") is None
