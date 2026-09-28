"""Session tokens: HS256 JWTs with ``sub`` (user id) and ``ver`` (token_version).

Tokens are stateless and expire a fixed ``SESSION_TTL`` after issue (no sliding
renewal). ``ver`` must match ``users.token_version`` on every request, so
bumping the version revokes all of a user's tokens.
"""

from __future__ import annotations

import os
import uuid
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta

import jwt

SESSION_TTL = timedelta(days=30)
MIN_SECRET_LENGTH = 32
_ALGORITHM = "HS256"


class InvalidToken(Exception):
    """The token is missing, malformed, expired, or not signed by us."""


@dataclass(frozen=True)
class TokenClaims:
    sub: uuid.UUID
    ver: int


def jwt_secret() -> str:
    """Read ``JWT_SECRET`` (lazily); refuse a missing or short secret."""
    secret = os.environ.get("JWT_SECRET", "")
    if len(secret) < MIN_SECRET_LENGTH:
        raise RuntimeError(
            f"JWT_SECRET must be set and at least {MIN_SECRET_LENGTH} characters long."
        )
    return secret


def issue_token(
    user_id: uuid.UUID, token_version: int, now: datetime | None = None
) -> str:
    issued_at = now or datetime.now(UTC)
    payload = {
        "sub": str(user_id),
        "ver": token_version,
        "iat": issued_at,
        "exp": issued_at + SESSION_TTL,
    }
    return jwt.encode(payload, jwt_secret(), algorithm=_ALGORITHM)


def decode_token(token: str) -> TokenClaims:
    """Verify ``token`` and return its claims; any problem → ``InvalidToken``."""
    secret = jwt_secret()
    try:
        payload = jwt.decode(
            token,
            secret,
            algorithms=[_ALGORITHM],
            options={"require": ["exp", "iat", "sub", "ver"]},
        )
        ver = payload["ver"]
        if not isinstance(ver, int) or isinstance(ver, bool):
            raise InvalidToken("ver must be an integer")
        return TokenClaims(sub=uuid.UUID(payload["sub"]), ver=ver)
    except InvalidToken:
        raise
    except (jwt.PyJWTError, ValueError, TypeError) as exc:
        raise InvalidToken(str(exc)) from exc
