"""Login, logout and "who am I" under ``/api/auth``.

The session is an HS256 JWT in the ``sr_session`` cookie (HttpOnly, Secure,
SameSite=Strict, Path=/api, 30 days); the SPA never sees the token. Responses
only ever describe the logged-in account.
"""

from __future__ import annotations

import os
import uuid

from fastapi import APIRouter, Depends, HTTPException, Request, Response, status
from fastapi.concurrency import run_in_threadpool
from fastapi.responses import JSONResponse
from pydantic import BaseModel, ConfigDict, Field
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.auth.dependencies import SESSION_COOKIE, current_user
from app.auth.passwords import verify_password
from app.auth.rate_limit import client_ip, login_limiter
from app.auth.tokens import SESSION_TTL, issue_token
from app.database import get_session
from app.models import User, normalize_email

router = APIRouter(prefix="/api/auth", tags=["auth"])

COOKIE_PATH = "/api"


class LoginRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    email: str = Field(max_length=320)
    # Capped well above the policy maximum so huge inputs never reach argon2.
    password: str = Field(max_length=1024)


class UserOut(BaseModel):
    id: uuid.UUID
    email: str
    role: str

    @classmethod
    def of(cls, user: User) -> UserOut:
        return cls(id=user.id, email=user.email, role=user.role)


def cookie_secure() -> bool:
    """``Secure`` flag; ``COOKIE_SECURE=false`` only for plain-http local setups."""
    return os.environ.get("COOKIE_SECURE", "true").strip().lower() not in {
        "0",
        "false",
        "no",
    }


def _cookie_attributes() -> dict[str, object]:
    return {
        "path": COOKIE_PATH,
        "secure": cookie_secure(),
        "httponly": True,
        "samesite": "strict",
    }


@router.post("/login", response_model=UserOut)
async def login(
    body: LoginRequest,
    request: Request,
    response: Response,
    session: AsyncSession = Depends(get_session),
) -> UserOut | JSONResponse:
    email = normalize_email(body.email)
    ip = client_ip(request)

    retry_after = login_limiter.check(email, ip)
    if retry_after is not None:
        return JSONResponse(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            content={"detail": "too_many_attempts"},
            headers={"Retry-After": str(retry_after)},
        )

    user = await session.scalar(select(User).where(User.email == email))
    # argon2 is CPU-bound: keep it off the event loop. Unknown e-mail and
    # password-less accounts verify against a dummy hash (same timing).
    valid = await run_in_threadpool(
        verify_password, body.password, user.password_hash if user else None
    )
    if user is None or not valid:
        login_limiter.record_failure(email, ip)
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED, detail="invalid_credentials"
        )

    login_limiter.record_success(email)
    response.set_cookie(
        SESSION_COOKIE,
        issue_token(user.id, user.token_version),
        max_age=int(SESSION_TTL.total_seconds()),
        **_cookie_attributes(),
    )
    return UserOut.of(user)


@router.post("/logout", status_code=status.HTTP_204_NO_CONTENT)
async def logout() -> Response:
    """Always 204; clears the cookie. Needs no session (idempotent)."""
    response = Response(status_code=status.HTTP_204_NO_CONTENT)
    response.delete_cookie(SESSION_COOKIE, **_cookie_attributes())
    return response


@router.get("/me", response_model=UserOut)
async def me(user: User = Depends(current_user)) -> UserOut:
    return UserOut.of(user)
