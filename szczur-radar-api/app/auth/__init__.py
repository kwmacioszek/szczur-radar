"""Authentication: password hashing, session tokens, route guards, login routes.

A thin, project-owned layer (no FastAPI Users). The contract lives in
``context/changes/admin-bootstrap-and-login/plan.md`` ("Kontrakt
uwierzytelniania"):

- ``passwords`` — argon2 hashing and the password policy;
- ``tokens`` — HS256 JWT session tokens carrying ``sub`` and ``ver``;
- ``dependencies`` — ``current_user`` / ``require_admin`` for protecting routes;
- ``rate_limit`` — in-process limit of failed login attempts;
- ``routes`` — ``/api/auth/login``, ``/api/auth/logout``, ``/api/auth/me``.
"""
