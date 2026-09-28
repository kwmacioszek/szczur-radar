"""Shared fixtures for database-backed tests.

Tests that need Postgres are marked ``@pytest.mark.db``. They run against
``TEST_DATABASE_URL`` (a DEDICATED test database — its ``public`` schema is
dropped and rebuilt at the start of the session):

- no ``TEST_DATABASE_URL``           -> ``db`` tests are skipped (local default);
- no ``TEST_DATABASE_URL`` but
  ``REQUIRE_DB_TESTS=1`` (CI)        -> the run fails; DB tests never silently skip.

Every ``db`` test automatically gets a migrated schema (``alembic upgrade
head``), ``DATABASE_URL`` pointed at the test database, empty tables before it
runs, and a disposed/reset application engine afterwards: async connections are
bound to the event loop that created them, and ``TestClient`` runs the app in
its own loop, so a cached engine must never leak into the next test.

Tests without the marker (e.g. ``tests/test_health.py``) touch none of this and
still run with no database and no ``DATABASE_URL``.
"""

from __future__ import annotations

import asyncio
import os
import uuid
from collections.abc import Callable, Iterator
from pathlib import Path

import pytest
from alembic import command
from alembic.config import Config
from sqlalchemy import Engine, create_engine, pool, text
from sqlalchemy.engine import make_url

from app import database
from app.auth.passwords import hash_password
from app.auth.rate_limit import login_limiter

API_ROOT = Path(__file__).resolve().parents[1]

# Tables emptied before every ``db`` test. Extend as migrations add tables.
TABLES = ("users",)

# 64 chars; only for tests. Real values come from Railway (sealed on production).
TEST_JWT_SECRET = "test-only-jwt-secret-0123456789abcdef0123456789abcdef0123456789"


def _test_database_url() -> str | None:
    return os.environ.get("TEST_DATABASE_URL") or None


def _db_tests_required() -> bool:
    return os.environ.get("REQUIRE_DB_TESTS") == "1"


def pytest_collection_modifyitems(
    config: pytest.Config, items: list[pytest.Item]
) -> None:
    if _test_database_url():
        return
    db_items = [item for item in items if item.get_closest_marker("db")]
    if not db_items:
        return
    if _db_tests_required():
        raise pytest.UsageError(
            "REQUIRE_DB_TESTS=1 but TEST_DATABASE_URL is not set: "
            f"{len(db_items)} database test(s) would be skipped."
        )
    skip = pytest.mark.skip(reason="needs Postgres: set TEST_DATABASE_URL")
    for item in db_items:
        item.add_marker(skip)


def alembic_config() -> Config:
    """Alembic config for ``szczur-radar-api``, independent of the cwd.

    ``migrations/env.py`` reads ``DATABASE_URL``; the ``db`` fixtures point it
    at the test database.
    """
    cfg = Config(str(API_ROOT / "alembic.ini"))
    cfg.set_main_option("script_location", str(API_ROOT / "migrations"))
    # Keep alembic.ini's logging config from reconfiguring pytest's handlers.
    cfg.attributes["configure_logger"] = False
    return cfg


def sync_test_engine(url: str) -> Engine:
    """Synchronous psycopg engine on the test DB (no pool, no event loop)."""
    return create_engine(database._normalize_async_url(url), poolclass=pool.NullPool)


@pytest.fixture(scope="session")
def migrated_database() -> Iterator[str]:
    """Rebuild the test DB schema from scratch and migrate it to ``head``.

    Sets ``DATABASE_URL = TEST_DATABASE_URL`` for the rest of the session so
    the application and Alembic both use the test database.
    """
    url = _test_database_url()
    assert url is not None  # db tests are skipped/failed at collection otherwise
    db_name = make_url(url).database or ""
    if "test" not in db_name:
        pytest.fail(
            f"TEST_DATABASE_URL must point at a dedicated test database "
            f"(name containing 'test'), got {db_name!r}: its schema is dropped."
        )

    engine = sync_test_engine(url)
    try:
        with engine.begin() as conn:
            conn.execute(text("DROP SCHEMA IF EXISTS public CASCADE"))
            conn.execute(text("CREATE SCHEMA public"))
    finally:
        engine.dispose()

    with pytest.MonkeyPatch.context() as mp:
        mp.setenv("DATABASE_URL", url)
        command.upgrade(alembic_config(), "head")
        yield url


def _reset_app_engine() -> None:
    engine = database._engine
    database._engine = None
    database._sessionmaker = None
    if engine is None:
        return
    try:
        asyncio.run(engine.dispose())
    except Exception:
        # Connections bound to an already-closed loop cannot be closed from a
        # new one; drop the pool without touching them.
        asyncio.run(engine.dispose(close=False))


@pytest.fixture
def db(migrated_database: str) -> Iterator[str]:
    """Clean tables before the test; dispose and reset the app engine after."""
    _reset_app_engine()
    engine = sync_test_engine(migrated_database)
    try:
        with engine.begin() as conn:
            conn.execute(text(f"TRUNCATE {', '.join(TABLES)} RESTART IDENTITY CASCADE"))
    finally:
        engine.dispose()
    try:
        yield migrated_database
    finally:
        _reset_app_engine()


@pytest.fixture(autouse=True)
def _db_marker(request: pytest.FixtureRequest) -> None:
    """Give every ``@pytest.mark.db`` test the ``db`` fixture automatically."""
    if request.node.get_closest_marker("db"):
        request.getfixturevalue("db")


@pytest.fixture(autouse=True)
def _auth_env(monkeypatch: pytest.MonkeyPatch) -> Iterator[None]:
    """Every test gets a valid JWT_SECRET and a fresh login rate limiter."""
    monkeypatch.setenv("JWT_SECRET", TEST_JWT_SECRET)
    login_limiter.reset()
    yield
    login_limiter.reset()


CreateUser = Callable[..., uuid.UUID]


@pytest.fixture
def create_user(db: str) -> CreateUser:
    """Insert a user directly (bypassing the app); returns its id.

    ``password=None`` creates an account without a password (S-07 activation).
    """

    def _create(
        email: str, password: str | None = None, role: str = "user"
    ) -> uuid.UUID:
        engine = sync_test_engine(db)
        try:
            with engine.begin() as conn:
                return conn.execute(
                    text(
                        "INSERT INTO users (email, password_hash, role) "
                        "VALUES (:email, :hash, :role) RETURNING id"
                    ),
                    {
                        "email": email,
                        "hash": hash_password(password) if password else None,
                        "role": role,
                    },
                ).scalar_one()
        finally:
            engine.dispose()

    return _create
