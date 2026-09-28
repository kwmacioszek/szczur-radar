"""In-process limit of failed login attempts (sliding window).

5 failures / 15 min per normalized e-mail and 20 / 15 min per client IP. The
state lives in this process only, which is correct while the API runs a single
replica (``.railway/railway.ts``); more replicas need a shared store.
"""

from __future__ import annotations

import math
import time
from collections import deque
from collections.abc import Callable

from fastapi import Request

WINDOW_SECONDS = 15 * 60
EMAIL_LIMIT = 5
IP_LIMIT = 20


class LoginRateLimiter:
    def __init__(
        self,
        clock: Callable[[], float] = time.monotonic,
        email_limit: int = EMAIL_LIMIT,
        ip_limit: int = IP_LIMIT,
        window_seconds: float = WINDOW_SECONDS,
    ) -> None:
        self._clock = clock
        self._email_limit = email_limit
        self._ip_limit = ip_limit
        self._window = window_seconds
        self._by_email: dict[str, deque[float]] = {}
        self._by_ip: dict[str, deque[float]] = {}

    def _recent(self, buckets: dict[str, deque[float]], key: str) -> deque[float]:
        """Failures for ``key`` still inside the window (expired ones dropped)."""
        failures = buckets.get(key)
        if failures is None:
            return deque()
        cutoff = self._clock() - self._window
        while failures and failures[0] <= cutoff:
            failures.popleft()
        if not failures:
            del buckets[key]
            return deque()
        return failures

    def _retry_after(self, failures: deque[float], limit: int) -> int | None:
        if len(failures) < limit:
            return None
        # Allowed again once the oldest failure counted against the limit expires.
        oldest = failures[len(failures) - limit]
        return max(1, math.ceil(oldest + self._window - self._clock()))

    def check(self, email: str, ip: str) -> int | None:
        """Seconds to wait before another attempt, or ``None`` if allowed."""
        waits = [
            wait
            for wait in (
                self._retry_after(self._recent(self._by_email, email), self._email_limit),
                self._retry_after(self._recent(self._by_ip, ip), self._ip_limit),
            )
            if wait is not None
        ]
        return max(waits) if waits else None

    def record_failure(self, email: str, ip: str) -> None:
        now = self._clock()
        self._by_email.setdefault(email, deque()).append(now)
        self._by_ip.setdefault(ip, deque()).append(now)

    def record_success(self, email: str) -> None:
        self._by_email.pop(email, None)

    def reset(self) -> None:
        self._by_email.clear()
        self._by_ip.clear()


login_limiter = LoginRateLimiter()


def client_ip(request: Request) -> str:
    """Client address: ``X-Real-IP`` (set by Railway's edge), else the peer.

    Behind Caddy ``X-Forwarded-For`` names Railway's edge, not the client.
    """
    real_ip = request.headers.get("x-real-ip", "").strip()
    if real_ip:
        return real_ip
    return request.client.host if request.client else "unknown"
