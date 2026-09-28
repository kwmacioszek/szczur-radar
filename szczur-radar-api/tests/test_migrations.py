"""Every migration must be reversible: upgrade -> downgrade -> upgrade.

This is the pattern for all future migrations: a new revision is only done
when this round trip passes on a real Postgres (CI runs it with
``REQUIRE_DB_TESTS=1``).
"""

from __future__ import annotations

import pytest
from alembic import command
from alembic.script import ScriptDirectory
from sqlalchemy import Engine, inspect, text
from sqlalchemy.exc import IntegrityError

from tests.conftest import alembic_config, sync_test_engine


def _has_users_table(engine: Engine) -> bool:
    return inspect(engine).has_table("users", schema="public")


def _assert_insert_rejected(engine: Engine, sql: str, constraint: str) -> None:
    with pytest.raises(IntegrityError) as exc_info:
        with engine.begin() as conn:
            conn.execute(text(sql))
    assert constraint in str(exc_info.value)


def test_single_migration_head() -> None:
    # Needs no database: reads the revision graph from migrations/versions/.
    heads = ScriptDirectory.from_config(alembic_config()).get_heads()
    assert len(heads) == 1, f"expected exactly one alembic head, got {heads}"


@pytest.mark.db
def test_upgrade_downgrade_upgrade_round_trip(db: str) -> None:
    cfg = alembic_config()
    engine = sync_test_engine(db)
    try:
        command.upgrade(cfg, "head")
        assert _has_users_table(engine)

        constraints = {
            c["name"] for c in inspect(engine).get_check_constraints("users")
        }
        assert {"ck_users_role", "ck_users_email_lower"} <= constraints
        uniques = {c["name"] for c in inspect(engine).get_unique_constraints("users")}
        assert "uq_users_email" in uniques

        _assert_insert_rejected(
            engine,
            "INSERT INTO users (email, role) VALUES ('root@example.com', 'root')",
            "ck_users_role",
        )
        _assert_insert_rejected(
            engine,
            "INSERT INTO users (email) VALUES ('Admin@Example.com')",
            "ck_users_email_lower",
        )
        with engine.begin() as conn:
            row = conn.execute(
                text(
                    "INSERT INTO users (email) VALUES ('user@example.com') "
                    "RETURNING id, role, token_version, password_hash, created_at"
                )
            ).one()
        assert row.id is not None
        assert row.role == "user"
        assert row.token_version == 0
        assert row.password_hash is None
        assert row.created_at is not None
        _assert_insert_rejected(
            engine,
            "INSERT INTO users (email) VALUES ('user@example.com')",
            "uq_users_email",
        )

        command.downgrade(cfg, "base")
        assert not _has_users_table(engine)

        command.upgrade(cfg, "head")
        assert _has_users_table(engine)
    finally:
        engine.dispose()
