"""Password hashing (argon2id via pwdlib) and the password policy.

The single source of truth for the login route, the CLI and S-07 (activation,
recovery). Plain passwords are never logged or returned.
"""

from __future__ import annotations

from pwdlib import PasswordHash

MIN_PASSWORD_LENGTH = 12
MAX_PASSWORD_LENGTH = 128

_hasher = PasswordHash.recommended()

# Verified against when the account is unknown or has no password, so that a
# wrong e-mail and a wrong password cost about the same time.
_DUMMY_HASH = _hasher.hash("szczur-radar-timing-equalizer")


def hash_password(plain: str) -> str:
    return _hasher.hash(plain)


def verify_password(plain: str, password_hash: str | None) -> bool:
    """Check ``plain`` against ``password_hash``.

    ``None`` (unknown account, or an account without a password) still runs a
    full verification against a dummy hash and returns ``False``.
    """
    if password_hash is None:
        _hasher.verify(plain, _DUMMY_HASH)
        return False
    try:
        return _hasher.verify(plain, password_hash)
    except Exception:
        # Malformed or unsupported hash in the database: treat as a mismatch.
        return False


def validate_password_policy(plain: str) -> None:
    """Raise ``ValueError`` with a user-facing message if ``plain`` is too weak."""
    if len(plain) < MIN_PASSWORD_LENGTH:
        raise ValueError(
            f"Password must be at least {MIN_PASSWORD_LENGTH} characters long."
        )
    if len(plain) > MAX_PASSWORD_LENGTH:
        raise ValueError(
            f"Password must be at most {MAX_PASSWORD_LENGTH} characters long."
        )
