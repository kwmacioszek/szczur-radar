"""Authorization completeness: EVERY API route requires a session.

The rule for every new route: it either depends on ``current_user`` /
``require_admin`` (``app/auth/dependencies.py``), or it is added to
``PUBLIC_ROUTES`` below together with a comment justifying why it is public.
Admin-only routes are additionally listed in ``ADMIN_ROUTES``.

The test walks all API routes of the real application and calls each method
without a cookie, so a new route that forgets its guard fails CI. Path
parameters are filled with a dummy of the right type so path validation does
not answer 422 before the guard answers 401.
"""

from __future__ import annotations

import re
import uuid
from typing import get_origin

import pytest
from fastapi import Depends, FastAPI
from fastapi.routing import APIRoute, RouteContext, iter_route_contexts
from fastapi.testclient import TestClient

from app.auth.dependencies import SESSION_COOKIE, current_user, require_admin
from app.auth.tokens import issue_token
from app.models import User
from main import app
from tests.conftest import CreateUser

PUBLIC_ROUTES: set[tuple[str, str]] = {
    ("GET", "/api/health"),  # liveness probe (Railway healthcheck)
    ("GET", "/api/ready"),  # readiness probe, reveals only up/down
    ("POST", "/api/auth/login"),  # obtaining a session cannot require one
    ("POST", "/api/auth/logout"),  # idempotent cookie removal
}

# (method, path) of routes guarded by ``require_admin``. Empty until an admin
# route exists (S-06); each entry is checked to answer 403 to a plain user.
ADMIN_ROUTES: set[tuple[str, str]] = set()

_PARAM = re.compile(r"\{(\w+)(?::\w+)?\}")


def _api_routes() -> list[RouteContext]:
    """Every API route with its effective (prefixed) path.

    ``app.routes`` holds included routers lazily (FastAPI >= 0.141), so walk
    them the way the OpenAPI generator does.
    """
    return [
        context
        for context in iter_route_contexts(app.routes)
        if isinstance(context.original_route, APIRoute)
    ]


def _declared_routes() -> set[tuple[str, str]]:
    return {
        (method, route.path) for route in _api_routes() for method in route.methods
    }


def _dummy(annotation: object) -> str:
    annotation = get_origin(annotation) or annotation
    if annotation is uuid.UUID:
        return str(uuid.UUID(int=0))
    if annotation is int:
        return "1"
    return "x"


def _concrete_path(route: RouteContext) -> str:
    types = {
        param.name: param.field_info.annotation for param in route.dependant.path_params
    }
    return _PARAM.sub(lambda m: _dummy(types.get(m.group(1))), route.path)


def _call(client: TestClient, method: str, path: str) -> int:
    return client.request(method, path).status_code


def test_allowlists_reference_existing_routes() -> None:
    declared = _declared_routes()
    assert PUBLIC_ROUTES <= declared, f"dead PUBLIC_ROUTES: {PUBLIC_ROUTES - declared}"
    assert ADMIN_ROUTES <= declared, f"dead ADMIN_ROUTES: {ADMIN_ROUTES - declared}"
    assert not PUBLIC_ROUTES & ADMIN_ROUTES


@pytest.mark.db
def test_every_non_public_route_requires_a_session() -> None:
    client = TestClient(app, base_url="https://testserver")
    unprotected = []
    checked = 0
    for route in _api_routes():
        for method in sorted(route.methods):
            if (method, route.path) in PUBLIC_ROUTES:
                continue
            checked += 1
            status = _call(client, method, _concrete_path(route))
            if status != 401:
                unprotected.append((method, route.path, status))
    assert checked > 0, "no protected routes found — is the app wired up?"
    assert not unprotected, (
        "routes answering without a session (method, path, status): "
        f"{unprotected}. Guard them with current_user/require_admin or add them "
        "to PUBLIC_ROUTES with a justification."
    )


@pytest.mark.db
def test_admin_routes_reject_plain_users(create_user: CreateUser) -> None:
    user_id = create_user("user@example.com", "correct horse battery", role="user")
    client = TestClient(app, base_url="https://testserver")
    client.cookies.set(SESSION_COOKIE, issue_token(user_id, 0), path="/api")
    routes = {route.path: route for route in _api_routes()}
    for method, path in sorted(ADMIN_ROUTES):
        status = _call(client, method, _concrete_path(routes[path]))
        assert status == 403, f"{method} {path} answered {status} to a plain user"


def _admin_probe_app(role: str) -> FastAPI:
    probe = FastAPI()

    @probe.get("/api/admin-probe")
    async def admin_probe(user: User = Depends(require_admin)) -> dict[str, str]:
        return {"email": user.email}

    async def _fake_current_user() -> User:
        return User(id=uuid.uuid4(), email="probe@example.com", role=role, token_version=0)

    probe.dependency_overrides[current_user] = _fake_current_user
    return probe


def test_require_admin_rejects_plain_user() -> None:
    resp = TestClient(_admin_probe_app("user")).get("/api/admin-probe")
    assert resp.status_code == 403
    assert resp.json() == {"detail": "forbidden"}


def test_require_admin_allows_admin() -> None:
    resp = TestClient(_admin_probe_app("admin")).get("/api/admin-probe")
    assert resp.status_code == 200
    assert resp.json() == {"email": "probe@example.com"}
