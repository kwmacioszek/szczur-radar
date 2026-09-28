"""Failed-login limiter: thresholds, sliding window, reset on success."""

from __future__ import annotations

from starlette.requests import Request

from app.auth.rate_limit import (
    EMAIL_LIMIT,
    IP_LIMIT,
    WINDOW_SECONDS,
    LoginRateLimiter,
    client_ip,
)


class FakeClock:
    def __init__(self) -> None:
        self.now = 1000.0

    def __call__(self) -> float:
        return self.now


def test_email_limit_blocks_after_threshold() -> None:
    clock = FakeClock()
    limiter = LoginRateLimiter(clock=clock)
    for i in range(EMAIL_LIMIT):
        assert limiter.check("a@example.com", f"10.0.0.{i}") is None
        limiter.record_failure("a@example.com", f"10.0.0.{i}")
    assert limiter.check("a@example.com", "10.0.0.99") == WINDOW_SECONDS
    assert limiter.check("b@example.com", "10.0.0.99") is None


def test_ip_limit_blocks_across_emails() -> None:
    limiter = LoginRateLimiter(clock=FakeClock())
    for i in range(IP_LIMIT):
        assert limiter.check(f"user{i}@example.com", "10.0.0.1") is None
        limiter.record_failure(f"user{i}@example.com", "10.0.0.1")
    assert limiter.check("fresh@example.com", "10.0.0.1") is not None
    assert limiter.check("fresh@example.com", "10.0.0.2") is None


def test_window_slides() -> None:
    clock = FakeClock()
    limiter = LoginRateLimiter(clock=clock)
    for _ in range(EMAIL_LIMIT):
        limiter.record_failure("a@example.com", "10.0.0.1")
        clock.now += 60
    blocked_for = limiter.check("a@example.com", "10.0.0.1")
    assert blocked_for == WINDOW_SECONDS - EMAIL_LIMIT * 60
    clock.now += blocked_for
    assert limiter.check("a@example.com", "10.0.0.1") is None


def test_success_clears_email_counter_only() -> None:
    limiter = LoginRateLimiter(clock=FakeClock(), ip_limit=EMAIL_LIMIT)
    for _ in range(EMAIL_LIMIT):
        limiter.record_failure("a@example.com", "10.0.0.1")
    limiter.record_success("a@example.com")
    assert limiter.check("a@example.com", "10.0.0.2") is None
    # The IP counter is not reset by a success.
    assert limiter.check("a@example.com", "10.0.0.1") is not None


def _request(headers: dict[str, str], peer: str = "172.16.0.9") -> Request:
    return Request(
        {
            "type": "http",
            "headers": [(k.lower().encode(), v.encode()) for k, v in headers.items()],
            "client": (peer, 12345),
        }
    )


def test_client_ip_prefers_x_real_ip() -> None:
    request = _request({"X-Real-IP": "203.0.113.7", "X-Forwarded-For": "10.1.1.1"})
    assert client_ip(request) == "203.0.113.7"


def test_client_ip_falls_back_to_peer() -> None:
    assert client_ip(_request({"X-Forwarded-For": "10.1.1.1"})) == "172.16.0.9"
