"""Login / me / logout against a real database (auth contract, plan phase 2)."""

from __future__ import annotations

import uuid

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import text

from app.auth.dependencies import SESSION_COOKIE
from app.auth.rate_limit import EMAIL_LIMIT, IP_LIMIT
from app.auth.tokens import issue_token
from main import app
from tests.conftest import CreateUser, sync_test_engine

pytestmark = pytest.mark.db

EMAIL = "admin@example.com"
PASSWORD = "correct horse battery"


@pytest.fixture
def client() -> TestClient:
    # https so the client stores and sends the Secure cookie.
    return TestClient(app, base_url="https://testserver")


def _login(client: TestClient, email: str, password: str, **headers: str):
    return client.post(
        "/api/auth/login", json={"email": email, "password": password}, headers=headers
    )


def test_login_sets_session_cookie_and_returns_user(
    client: TestClient, create_user: CreateUser
) -> None:
    user_id = create_user(EMAIL, PASSWORD, role="admin")

    resp = _login(client, EMAIL, PASSWORD)

    assert resp.status_code == 200
    assert resp.json() == {"id": str(user_id), "email": EMAIL, "role": "admin"}
    set_cookie = resp.headers["set-cookie"]
    assert set_cookie.startswith(f"{SESSION_COOKIE}=")
    attributes = {part.strip().lower() for part in set_cookie.split(";")[1:]}
    assert {"httponly", "secure", "samesite=strict", "path=/api", "max-age=2592000"} <= attributes


def test_login_normalizes_email(client: TestClient, create_user: CreateUser) -> None:
    create_user(EMAIL, PASSWORD)
    assert _login(client, "  Admin@Example.COM ", PASSWORD).status_code == 200


def test_unknown_email_and_wrong_password_look_identical(
    client: TestClient, create_user: CreateUser
) -> None:
    create_user(EMAIL, PASSWORD)

    wrong_password = _login(client, EMAIL, "wrong password!!")
    unknown_email = _login(client, "nobody@example.com", PASSWORD)

    for resp in (wrong_password, unknown_email):
        assert resp.status_code == 401
        assert resp.json() == {"detail": "invalid_credentials"}
        assert "set-cookie" not in resp.headers


def test_account_without_password_cannot_log_in(
    client: TestClient, create_user: CreateUser
) -> None:
    create_user(EMAIL, password=None)
    resp = _login(client, EMAIL, PASSWORD)
    assert resp.status_code == 401
    assert resp.json() == {"detail": "invalid_credentials"}


def test_me_returns_only_own_account(
    client: TestClient, create_user: CreateUser
) -> None:
    create_user("other@example.com", "another password!")
    user_id = create_user(EMAIL, PASSWORD)
    assert _login(client, EMAIL, PASSWORD).status_code == 200

    resp = client.get("/api/auth/me")

    assert resp.status_code == 200
    assert resp.json() == {"id": str(user_id), "email": EMAIL, "role": "user"}


def test_me_without_session_is_401(client: TestClient) -> None:
    resp = client.get("/api/auth/me")
    assert resp.status_code == 401
    assert resp.json() == {"detail": "not_authenticated"}


def test_token_for_deleted_user_is_401(client: TestClient) -> None:
    client.cookies.set(SESSION_COOKIE, issue_token(uuid.uuid4(), 0))
    assert client.get("/api/auth/me").status_code == 401


def test_bumping_token_version_revokes_session(
    client: TestClient, create_user: CreateUser, db: str
) -> None:
    user_id = create_user(EMAIL, PASSWORD)
    assert _login(client, EMAIL, PASSWORD).status_code == 200
    assert client.get("/api/auth/me").status_code == 200

    engine = sync_test_engine(db)
    try:
        with engine.begin() as conn:
            conn.execute(
                text("UPDATE users SET token_version = token_version + 1 WHERE id = :id"),
                {"id": user_id},
            )
    finally:
        engine.dispose()

    assert client.get("/api/auth/me").status_code == 401


def test_logout_clears_cookie(client: TestClient, create_user: CreateUser) -> None:
    create_user(EMAIL, PASSWORD)
    assert _login(client, EMAIL, PASSWORD).status_code == 200

    resp = client.post("/api/auth/logout")

    assert resp.status_code == 204
    set_cookie = resp.headers["set-cookie"].lower()
    assert set_cookie.startswith(f"{SESSION_COOKIE}=")
    assert "max-age=0" in set_cookie
    assert "path=/api" in set_cookie
    assert client.get("/api/auth/me").status_code == 401


def test_email_limit_returns_429_on_sixth_failure(
    client: TestClient, create_user: CreateUser
) -> None:
    create_user(EMAIL, PASSWORD)
    for _ in range(EMAIL_LIMIT):
        assert _login(client, EMAIL, "wrong password!!").status_code == 401

    resp = _login(client, EMAIL, PASSWORD)  # even the right password is blocked

    assert resp.status_code == 429
    assert resp.json() == {"detail": "too_many_attempts"}
    assert int(resp.headers["retry-after"]) > 0


def test_ip_limit_returns_429_across_emails(client: TestClient) -> None:
    for i in range(IP_LIMIT):
        resp = _login(client, f"user{i}@example.com", PASSWORD, **{"X-Real-IP": "203.0.113.7"})
        assert resp.status_code == 401

    blocked = _login(client, "fresh@example.com", PASSWORD, **{"X-Real-IP": "203.0.113.7"})
    other_ip = _login(client, "fresh@example.com", PASSWORD, **{"X-Real-IP": "203.0.113.8"})

    assert blocked.status_code == 429
    assert other_ip.status_code == 401


def test_successful_login_resets_email_counter(
    client: TestClient, create_user: CreateUser
) -> None:
    create_user(EMAIL, PASSWORD)
    for _ in range(EMAIL_LIMIT - 1):
        assert _login(client, EMAIL, "wrong password!!").status_code == 401
    assert _login(client, EMAIL, PASSWORD).status_code == 200

    for _ in range(EMAIL_LIMIT - 1):
        assert _login(client, EMAIL, "wrong password!!").status_code == 401
    assert _login(client, EMAIL, PASSWORD).status_code == 200


def test_form_encoded_login_is_rejected(
    client: TestClient, create_user: CreateUser
) -> None:
    create_user(EMAIL, PASSWORD)
    resp = client.post("/api/auth/login", data={"email": EMAIL, "password": PASSWORD})
    assert resp.status_code in (415, 422)
    assert "set-cookie" not in resp.headers


def test_unknown_fields_are_rejected(client: TestClient, create_user: CreateUser) -> None:
    create_user(EMAIL, PASSWORD)
    resp = client.post(
        "/api/auth/login", json={"email": EMAIL, "password": PASSWORD, "role": "admin"}
    )
    assert resp.status_code == 422
