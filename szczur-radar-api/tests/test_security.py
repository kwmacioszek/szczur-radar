"""Origin check on state-changing API requests (CSRF defence).

Uses ``POST /api/auth/logout``: public, needs no database, always 204.
"""

from __future__ import annotations

from fastapi.testclient import TestClient

from main import app

client = TestClient(app, base_url="https://testserver")


def test_foreign_origin_is_rejected() -> None:
    resp = client.post(
        "/api/auth/logout", headers={"Origin": "https://evil.example"}
    )
    assert resp.status_code == 403
    assert resp.json() == {"detail": "cross_origin"}


def test_same_origin_passes() -> None:
    resp = client.post("/api/auth/logout", headers={"Origin": "https://testserver"})
    assert resp.status_code == 204


def test_same_host_different_port_is_rejected() -> None:
    resp = client.post(
        "/api/auth/logout", headers={"Origin": "https://testserver:8443"}
    )
    assert resp.status_code == 403


def test_null_origin_is_rejected() -> None:
    resp = client.post("/api/auth/logout", headers={"Origin": "null"})
    assert resp.status_code == 403


def test_missing_origin_passes() -> None:
    assert client.post("/api/auth/logout").status_code == 204


def test_safe_methods_are_not_checked() -> None:
    resp = client.get("/api/health", headers={"Origin": "https://evil.example"})
    assert resp.status_code == 200
