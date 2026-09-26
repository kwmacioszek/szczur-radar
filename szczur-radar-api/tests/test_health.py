"""Probe-route tests. No real database is required.

Readiness is exercised through ``app.database.check_database`` because
``routes.py`` calls it as a module attribute (``database.check_database()``),
so monkeypatching the attribute swaps the DB round-trip for a stub.
"""

from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

from app import database
from main import app

client = TestClient(app)


def test_health_is_ok() -> None:
    resp = client.get("/api/health")
    assert resp.status_code == 200
    assert resp.json() == {"status": "ok"}


def test_ready_ok_when_database_reachable(monkeypatch: pytest.MonkeyPatch) -> None:
    async def _ok() -> None:
        return None

    monkeypatch.setattr(database, "check_database", _ok)
    resp = client.get("/api/ready")
    assert resp.status_code == 200
    assert resp.json() == {"status": "ready"}


def test_ready_returns_503_when_database_unavailable(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    async def _boom() -> None:
        raise RuntimeError("connection refused")

    monkeypatch.setattr(database, "check_database", _boom)
    resp = client.get("/api/ready")
    assert resp.status_code == 503
    assert resp.json() == {"status": "unavailable"}
