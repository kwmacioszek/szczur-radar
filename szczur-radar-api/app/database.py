"""Async database access for readiness checks.

Only the connectivity probe used by ``GET /api/ready`` lives here for now.
Models, sessions, and migrations arrive with the data layer in a later change.
"""

from __future__ import annotations

import os

from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncEngine, create_async_engine

_engine: AsyncEngine | None = None


def _normalize_async_url(url: str) -> str:
    """Coerce a libpq-style DATABASE_URL to the async psycopg driver.

    Railway hands out ``postgresql://…`` (and some tools emit ``postgres://``);
    SQLAlchemy's async engine needs an explicit ``+psycopg`` driver.
    """
    if url.startswith("postgres://"):
        url = "postgresql://" + url[len("postgres://") :]
    if url.startswith("postgresql://"):
        url = "postgresql+psycopg://" + url[len("postgresql://") :]
    return url


def get_engine() -> AsyncEngine:
    """Return a lazily-created, process-wide async engine.

    Created on first use so importing this module never requires DATABASE_URL
    (keeps ``GET /api/health`` and the test suite independent of a database).
    """
    global _engine
    if _engine is None:
        url = os.environ["DATABASE_URL"]
        _engine = create_async_engine(_normalize_async_url(url), pool_pre_ping=True)
    return _engine


async def check_database() -> None:
    """Run a trivial query; raise if the database is unreachable."""
    engine = get_engine()
    async with engine.connect() as conn:
        await conn.execute(text("SELECT 1"))
