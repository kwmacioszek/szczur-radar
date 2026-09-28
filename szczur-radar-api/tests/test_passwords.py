"""Password hashing and policy."""

from __future__ import annotations

import pytest

from app.auth.passwords import hash_password, validate_password_policy, verify_password

PASSWORD = "correct horse battery"


def test_hash_is_argon2_and_verifies() -> None:
    hashed = hash_password(PASSWORD)
    assert hashed != PASSWORD
    assert hashed.startswith("$argon2id$")
    assert verify_password(PASSWORD, hashed)
    assert not verify_password(PASSWORD + "!", hashed)


def test_missing_hash_never_verifies() -> None:
    assert verify_password(PASSWORD, None) is False


def test_malformed_hash_never_verifies() -> None:
    assert verify_password(PASSWORD, "not-a-hash") is False


@pytest.mark.parametrize("length", [12, 128])
def test_policy_accepts_bounds(length: int) -> None:
    validate_password_policy("a" * length)


@pytest.mark.parametrize("length", [0, 11, 129])
def test_policy_rejects_out_of_bounds(length: int) -> None:
    with pytest.raises(ValueError):
        validate_password_policy("a" * length)
