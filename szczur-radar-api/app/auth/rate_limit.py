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
from threading import Lock
from typing import Literal

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
        self._pending_email: dict[str, int] = {}
        self._pending_ip: dict[str, int] = {}
        self._lock = Lock()
        self._next_sweep = self._clock() + self._window

    def _recent(
        self, buckets: dict[str, deque[float]], key: str, now: float
    ) -> deque[float]:
        """Failures for ``key`` still inside the window (expired ones dropped)."""
        failures = buckets.get(key)
        if failures is None:
            return deque()
        cutoff = now - self._window
        while failures and failures[0] <= cutoff:
            failures.popleft()
        if not failures:
            del buckets[key]
            return deque()
        return failures

    def _sweep_if_due(self, now: float) -> None:
        if now < self._next_sweep:
            return
        for buckets in (self._by_email, self._by_ip):
            for key in list(buckets):
                self._recent(buckets, key, now)
        self._next_sweep = now + self._window

    def _retry_after(
        self, failures: deque[float], pending: int, limit: int, now: float
    ) -> int | None:
        if len(failures) + pending < limit:
            return None
        if len(failures) < limit:
            # A pending attempt may succeed or be cancelled shortly.
            return 1
        # Allowed again once the oldest failure counted against the limit expires.
        oldest = failures[len(failures) - limit]
        return max(1, math.ceil(oldest + self._window - now))

    def _check(self, email: str, ip: str, now: float) -> int | None:
        waits = [
            wait
            for wait in (
                self._retry_after(
                    self._recent(self._by_email, email, now),
                    self._pending_email.get(email, 0),
                    self._email_limit,
                    now,
                ),
                self._retry_after(
                    self._recent(self._by_ip, ip, now),
                    self._pending_ip.get(ip, 0),
                    self._ip_limit,
                    now,
                ),
            )
            if wait is not None
        ]
        return max(waits) if waits else None

    def check(self, email: str, ip: str) -> int | None:
        """Seconds to wait before another attempt, or ``None`` if allowed."""
        with self._lock:
            now = self._clock()
            self._sweep_if_due(now)
            return self._check(email, ip, now)

    def reserve(self, email: str, ip: str) -> int | None:
        """Atomically check the limits and count one attempt in progress."""
        with self._lock:
            now = self._clock()
            self._sweep_if_due(now)
            retry_after = self._check(email, ip, now)
            if retry_after is not None:
                return retry_after
            self._pending_email[email] = self._pending_email.get(email, 0) + 1
            self._pending_ip[ip] = self._pending_ip.get(ip, 0) + 1
            return None

    def finish(
        self, email: str, ip: str, outcome: Literal["failure", "success", "aborted"]
    ) -> None:
        """Release a reservation, recording only completed authentication attempts."""
        with self._lock:
            for pending, key in ((self._pending_email, email), (self._pending_ip, ip)):
                count = pending[key]
                if count == 1:
                    del pending[key]
                else:
                    pending[key] = count - 1
            if outcome == "failure":
                now = self._clock()
                self._by_email.setdefault(email, deque()).append(now)
                self._by_ip.setdefault(ip, deque()).append(now)
            elif outcome == "success":
                self._by_email.pop(email, None)

    def record_failure(self, email: str, ip: str) -> None:
        with self._lock:
            now = self._clock()
            self._by_email.setdefault(email, deque()).append(now)
            self._by_ip.setdefault(ip, deque()).append(now)

    def record_success(self, email: str) -> None:
        with self._lock:
            self._by_email.pop(email, None)

    def reset(self) -> None:
        with self._lock:
            self._by_email.clear()
            self._by_ip.clear()
            self._pending_email.clear()
            self._pending_ip.clear()
            self._next_sweep = self._clock() + self._window


login_limiter = LoginRateLimiter()


def client_ip(request: Request) -> str:
    """Client address: ``X-Real-IP`` (set by Railway's edge), else the peer.

    Behind Caddy ``X-Forwarded-For`` names Railway's edge, not the client.
    """
    real_ip = request.headers.get("x-real-ip", "").strip()
    if real_ip:
        return real_ip
    return request.client.host if request.client else "unknown"
