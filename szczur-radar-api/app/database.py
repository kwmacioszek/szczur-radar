"""Async database access: engine, sessions, and the readiness probe.

The engine is created lazily so importing this module never requires
DATABASE_URL. Tests that touch the database must ``dispose()`` the engine and
reset ``_engine`` (and ``_sessionmaker``) after each test: async connections are
bound to the event loop that created them (see ``tests/conftest.py``).
"""

from __future__ import annotations

import os
from collections.abc import AsyncIterator

from sqlalchemy import text
from sqlalchemy.ext.asyncio import (
    AsyncEngine,
    AsyncSession,
    async_sessionmaker,
    create_async_engine,
)

_engine: AsyncEngine | None = None
_sessionmaker: async_sessionmaker[AsyncSession] | None = None


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


def get_sessionmaker() -> async_sessionmaker[AsyncSession]:
    """Return a lazily-created session factory bound to ``get_engine()``.

    Rebuilt whenever the engine was reset, so it never outlives the engine it
    was bound to.
    """
    global _sessionmaker
    engine = get_engine()
    if _sessionmaker is None or _sessionmaker.kw.get("bind") is not engine:
        _sessionmaker = async_sessionmaker(engine, expire_on_commit=False)
    return _sessionmaker


async def get_session() -> AsyncIterator[AsyncSession]:
    """FastAPI dependency yielding one ``AsyncSession`` per request."""
    async with get_sessionmaker()() as session:
        yield session


async def check_database() -> None:
    """Run a trivial query; raise if the database is unreachable."""
    engine = get_engine()
    async with engine.connect() as conn:
        await conn.execute(text("SELECT 1"))
