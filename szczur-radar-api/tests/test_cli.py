"""``python -m app.cli create-admin`` against a real database."""

from __future__ import annotations

from collections.abc import Iterator

import pytest
from sqlalchemy import text

from app import cli
from app.auth.passwords import verify_password
from tests.conftest import CreateUser, sync_test_engine

pytestmark = pytest.mark.db

PASSWORD = "correct horse battery"


def _answers(monkeypatch: pytest.MonkeyPatch, *answers: str) -> list[str]:
    """Replace the hidden prompt with scripted answers; returns prompts seen."""
    prompts: list[str] = []
    remaining: Iterator[str] = iter(answers)

    def _fake_getpass(prompt: str = "") -> str:
        prompts.append(prompt)
        return next(remaining)

    monkeypatch.setattr(cli, "getpass", _fake_getpass)
    return prompts


def _users(db: str) -> list[tuple[str, str | None, str]]:
    engine = sync_test_engine(db)
    try:
        with engine.connect() as conn:
            return [
                (row.email, row.password_hash, row.role)
                for row in conn.execute(
                    text("SELECT email, password_hash, role FROM users ORDER BY email")
                )
            ]
    finally:
        engine.dispose()


def test_creates_admin_with_hashed_password(
    db: str, monkeypatch: pytest.MonkeyPatch, capsys: pytest.CaptureFixture[str]
) -> None:
    prompts = _answers(monkeypatch, PASSWORD, PASSWORD)

    code = cli.main(["create-admin", "--email", "  Admin@Example.COM "])

    assert code == 0
    assert len(prompts) == 2
    [(email, password_hash, role)] = _users(db)
    assert email == "admin@example.com"
    assert role == "admin"
    assert password_hash is not None and password_hash != PASSWORD
    assert verify_password(PASSWORD, password_hash)
    out, err = capsys.readouterr()
    assert "Created admin admin@example.com" in out
    assert PASSWORD not in out + err


def test_refuses_second_admin(
    db: str, monkeypatch: pytest.MonkeyPatch, capsys: pytest.CaptureFixture[str]
) -> None:
    _answers(monkeypatch, PASSWORD, PASSWORD)
    assert cli.main(["create-admin", "--email", "first@example.com"]) == 0
    prompts = _answers(monkeypatch, PASSWORD, PASSWORD)

    code = cli.main(["create-admin", "--email", "second@example.com"])

    assert code == 1
    assert prompts == []  # refused before asking for a password
    assert [role for _, _, role in _users(db)] == ["admin"]
    assert "Admin already exists" in capsys.readouterr().err


def test_refuses_taken_email(
    db: str, create_user: CreateUser, monkeypatch: pytest.MonkeyPatch
) -> None:
    create_user("taken@example.com", "some password!!", role="user")
    _answers(monkeypatch, PASSWORD, PASSWORD)

    assert cli.main(["create-admin", "--email", "Taken@example.com"]) == 1
    assert [(email, role) for email, _, role in _users(db)] == [
        ("taken@example.com", "user")
    ]


def test_mismatched_passwords_create_nothing(
    db: str, monkeypatch: pytest.MonkeyPatch, capsys: pytest.CaptureFixture[str]
) -> None:
    _answers(monkeypatch, PASSWORD, PASSWORD + "x")

    assert cli.main(["create-admin", "--email", "admin@example.com"]) == 1
    assert _users(db) == []
    assert "do not match" in capsys.readouterr().err


def test_too_short_password_creates_nothing(
    db: str, monkeypatch: pytest.MonkeyPatch
) -> None:
    _answers(monkeypatch, "short", "short")

    assert cli.main(["create-admin", "--email", "admin@example.com"]) == 1
    assert _users(db) == []


def test_usage_errors_exit_2(db: str) -> None:
    with pytest.raises(SystemExit) as exc_info:
        cli.main(["create-admin"])
    assert exc_info.value.code == 2
    assert cli.main(["create-admin", "--email", "not-an-email"]) == 2
