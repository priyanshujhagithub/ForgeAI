import pytest
from fastapi.testclient import TestClient

from app.main import app, store


@pytest.fixture
def client():
    store.clear()
    return TestClient(app)


def test_register_returns_created_user(client):
    response = client.post(
        "/register", json={"email": "ada@example.com", "password": "correct-horse"}
    )

    assert response.status_code == 201
    assert response.json() == {"id": 1, "email": "ada@example.com"}


def test_registered_users_are_listed(client):
    client.post("/register", json={"email": "ada@example.com", "password": "correct-horse"})
    client.post("/register", json={"email": "alan@example.com", "password": "enigma-1234"})

    assert client.get("/users").json() == ["ada@example.com", "alan@example.com"]


def test_register_requires_email_and_password(client):
    response = client.post("/register", json={"email": "ada@example.com"})

    assert response.status_code == 422
