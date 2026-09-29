"""SQLAlchemy ORM models.

``Base.metadata`` is Alembic's ``target_metadata`` (``migrations/env.py``), but
the schema itself is owned by explicit migrations in ``migrations/versions/`` —
every change ships as a new revision with a working ``downgrade()``.
"""

from __future__ import annotations

import uuid
from datetime import datetime

from sqlalchemy import CheckConstraint, DateTime, Integer, Text, UniqueConstraint, text
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column

ROLE_USER = "user"
ROLE_ADMIN = "admin"
MAX_EMAIL_LENGTH = 320


def normalize_email(email: str) -> str:
    """Canonical form of an e-mail address: trimmed and lower-cased.

    The single normalization used by the CLI and login, matching the
    ``ck_users_email_lower`` constraint on ``users.email``.
    """
    return email.strip().lower()


class Base(DeclarativeBase):
    pass


class User(Base):
    __tablename__ = "users"
    __table_args__ = (
        UniqueConstraint("email", name="uq_users_email"),
        CheckConstraint("email = lower(email)", name="ck_users_email_lower"),
        CheckConstraint("role IN ('user', 'admin')", name="ck_users_role"),
    )

    id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        primary_key=True,
        server_default=text("gen_random_uuid()"),
    )
    email: Mapped[str] = mapped_column(Text, nullable=False)
    # NULL = account without a password (activation pending, S-07); it cannot
    # log in.
    password_hash: Mapped[str | None] = mapped_column(Text, nullable=True)
    role: Mapped[str] = mapped_column(
        Text, nullable=False, server_default=text("'user'")
    )
    # Embedded in session tokens as ``ver``; bumping it invalidates every
    # token issued to this user.
    token_version: Mapped[int] = mapped_column(
        Integer, nullable=False, server_default=text("0")
    )
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, server_default=text("now()")
    )
