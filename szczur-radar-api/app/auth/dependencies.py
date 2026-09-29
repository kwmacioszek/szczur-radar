"""FastAPI dependencies that protect routes.

Every API route either depends on ``current_user`` (or ``require_admin``) or is
listed in ``PUBLIC_ROUTES`` in ``tests/test_authz_completeness.py`` — that test
walks all routes of the app and fails otherwise.
"""

from __future__ import annotations

from fastapi import Depends, HTTPException, Request, status
from sqlalchemy.ext.asyncio import AsyncSession

from app.auth.tokens import InvalidToken, decode_token
from app.database import get_session
from app.models import ROLE_ADMIN, User

SESSION_COOKIE = "sr_session"


def _not_authenticated() -> HTTPException:
    return HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED, detail="not_authenticated"
    )


async def current_user(
    request: Request, session: AsyncSession = Depends(get_session)
) -> User:
    """The user owning the session cookie; 401 for anything else."""
    token = request.cookies.get(SESSION_COOKIE)
    if not token:
        raise _not_authenticated()
    try:
        claims = decode_token(token)
    except InvalidToken:
        raise _not_authenticated() from None
    user = await session.get(User, claims.sub)
    if user is None or user.token_version != claims.ver:
        raise _not_authenticated()
    return user


async def require_admin(user: User = Depends(current_user)) -> User:
    if user.role != ROLE_ADMIN:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="forbidden")
    return user
