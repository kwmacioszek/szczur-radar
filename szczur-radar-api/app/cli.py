"""Operator CLI (FR-026): bootstrap the first administrator account.

    uv run python -m app.cli create-admin --email admin@example.com

The password is read twice with a hidden prompt (never an argument, so it
stays out of shell history) and is never printed or logged. Refuses to run
when any admin already exists — further admins and recovery belong to S-07.

Exit codes: 0 created, 1 refused (admin exists, e-mail taken, password
mismatch or policy), 2 usage/configuration error.
"""

from __future__ import annotations

import argparse
import asyncio
import os
import sys
from getpass import getpass

from sqlalchemy import exists, select, text
from sqlalchemy.ext.asyncio import AsyncSession, create_async_engine

from app.auth.passwords import hash_password, validate_password_policy
from app.database import _normalize_async_url
from app.models import MAX_EMAIL_LENGTH, ROLE_ADMIN, User, normalize_email

EXIT_OK = 0
EXIT_REFUSED = 1
EXIT_USAGE = 2
# Stable PostgreSQL advisory lock key for the one-time admin bootstrap.
ADMIN_BOOTSTRAP_LOCK_ID = 0x53525F41444D494E  # "SR_ADMIN"


def _fail(message: str, code: int = EXIT_REFUSED) -> int:
    print(message, file=sys.stderr)
    return code


async def _create_admin(database_url: str, email: str) -> int:
    # A dedicated engine for this asyncio.run() loop, always disposed: the
    # application's cached engine must never cross event loops.
    engine = create_async_engine(_normalize_async_url(database_url))
    try:
        async with AsyncSession(engine, expire_on_commit=False) as session:
            # Serialize separate CLI processes through the check and insert.
            # The transaction releases this lock on commit or session close.
            await session.execute(
                text("SELECT pg_advisory_xact_lock(:lock_id)"),
                {"lock_id": ADMIN_BOOTSTRAP_LOCK_ID},
            )
            if await session.scalar(select(exists().where(User.role == ROLE_ADMIN))):
                return _fail("Admin already exists — use the recovery command (S-07).")
            if await session.scalar(select(exists().where(User.email == email))):
                return _fail(f"An account with e-mail {email} already exists.")

            password = getpass("Password: ")
            if password != getpass("Repeat password: "):
                return _fail("Passwords do not match. Nothing was created.")
            try:
                validate_password_policy(password)
            except ValueError as exc:
                return _fail(f"{exc} Nothing was created.")

            session.add(
                User(email=email, password_hash=hash_password(password), role=ROLE_ADMIN)
            )
            await session.commit()
    finally:
        await engine.dispose()

    print(f"Created admin {email}")
    return EXIT_OK


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="python -m app.cli")
    commands = parser.add_subparsers(dest="command", required=True)
    create_admin = commands.add_parser(
        "create-admin", help="create the first administrator account"
    )
    create_admin.add_argument("--email", required=True)
    return parser


def main(argv: list[str] | None = None) -> int:
    args = _parser().parse_args(argv)  # usage errors exit with code 2

    database_url = os.environ.get("DATABASE_URL")
    if not database_url:
        return _fail("DATABASE_URL is not set.", EXIT_USAGE)

    if args.command == "create-admin":
        email = normalize_email(args.email)
        if "@" not in email or len(email) > MAX_EMAIL_LENGTH:
            return _fail(f"Invalid e-mail address: {args.email!r}", EXIT_USAGE)
        return asyncio.run(_create_admin(database_url, email))

    return EXIT_USAGE  # unreachable: argparse enforces a known command


if __name__ == "__main__":
    sys.exit(main())
