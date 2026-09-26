"""Public probe routes, served under the ``/api`` prefix.

The web service reverse-proxies ``/api/*`` to this service preserving the path,
so these endpoints are reachable at ``/api/health`` and ``/api/ready`` through
the single public origin. Data and admin routes stay absent until auth lands.
"""

from __future__ import annotations

from fastapi import APIRouter
from fastapi.responses import JSONResponse

from app import database

router = APIRouter(prefix="/api", tags=["ops"])


@router.get("/health")
async def health() -> dict[str, str]:
    """Liveness: the process is up and serving. No dependencies checked."""
    return {"status": "ok"}


@router.get("/ready")
async def ready() -> JSONResponse:
    """Readiness: succeeds only if a PostgreSQL query round-trips.

    Returns 503 (not 500) on failure so orchestrators treat it as
    "not ready yet" rather than a crash.
    """
    try:
        await database.check_database()
    except Exception:
        return JSONResponse(status_code=503, content={"status": "unavailable"})
    return JSONResponse(status_code=200, content={"status": "ready"})
