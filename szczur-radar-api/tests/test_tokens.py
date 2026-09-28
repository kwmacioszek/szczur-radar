"""Session token contract: HS256 only, required claims, secret policy."""

from __future__ import annotations

import uuid
from datetime import UTC, datetime, timedelta

import jwt
import pytest

from app.auth.tokens import (
    SESSION_TTL,
    InvalidToken,
    decode_token,
    issue_token,
    jwt_secret,
)
from tests.conftest import TEST_JWT_SECRET

USER_ID = uuid.UUID("11111111-2222-3333-4444-555555555555")


def _now() -> datetime:
    return datetime.now(UTC)


def test_round_trip_returns_sub_and_ver() -> None:
    claims = decode_token(issue_token(USER_ID, 3))
    assert claims.sub == USER_ID
    assert claims.ver == 3


def test_token_expires_after_session_ttl() -> None:
    payload = jwt.decode(
        issue_token(USER_ID, 0), TEST_JWT_SECRET, algorithms=["HS256"]
    )
    assert payload["exp"] - payload["iat"] == int(SESSION_TTL.total_seconds())


def test_expired_token_is_rejected() -> None:
    token = issue_token(USER_ID, 0, now=_now() - SESSION_TTL - timedelta(seconds=5))
    with pytest.raises(InvalidToken):
        decode_token(token)


def test_tampered_signature_is_rejected() -> None:
    header, payload, signature = issue_token(USER_ID, 0).split(".")
    tampered = signature[:-2] + ("AA" if not signature.endswith("AA") else "BB")
    with pytest.raises(InvalidToken):
        decode_token(f"{header}.{payload}.{tampered}")


def test_token_signed_with_another_secret_is_rejected() -> None:
    token = jwt.encode(
        {"sub": str(USER_ID), "ver": 0, "iat": _now(), "exp": _now() + SESSION_TTL},
        "another-secret-another-secret-another-secret",
        algorithm="HS256",
    )
    with pytest.raises(InvalidToken):
        decode_token(token)


def test_alg_none_is_rejected() -> None:
    token = jwt.encode(
        {"sub": str(USER_ID), "ver": 0, "iat": _now(), "exp": _now() + SESSION_TTL},
        None,
        algorithm="none",
    )
    with pytest.raises(InvalidToken):
        decode_token(token)


@pytest.mark.parametrize("missing", ["ver", "sub", "exp"])
def test_missing_claim_is_rejected(missing: str) -> None:
    claims = {"sub": str(USER_ID), "ver": 0, "iat": _now(), "exp": _now() + SESSION_TTL}
    del claims[missing]
    token = jwt.encode(claims, TEST_JWT_SECRET, algorithm="HS256")
    with pytest.raises(InvalidToken):
        decode_token(token)


@pytest.mark.parametrize("sub,ver", [("not-a-uuid", 0), (str(USER_ID), "0")])
def test_malformed_claims_are_rejected(sub: str, ver: object) -> None:
    token = jwt.encode(
        {"sub": sub, "ver": ver, "iat": _now(), "exp": _now() + SESSION_TTL},
        TEST_JWT_SECRET,
        algorithm="HS256",
    )
    with pytest.raises(InvalidToken):
        decode_token(token)


def test_garbage_is_rejected() -> None:
    with pytest.raises(InvalidToken):
        decode_token("not-a-jwt")


def test_missing_secret_raises(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.delenv("JWT_SECRET")
    with pytest.raises(RuntimeError):
        jwt_secret()
    with pytest.raises(RuntimeError):
        issue_token(USER_ID, 0)


def test_short_secret_raises(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("JWT_SECRET", "x" * 31)
    with pytest.raises(RuntimeError):
        jwt_secret()
