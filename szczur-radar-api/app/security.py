"""Cross-origin write protection (CSRF defence beyond ``SameSite=Strict``).

State-changing requests under ``/api/`` carrying an ``Origin`` header whose
``host[:port]`` differs from the request's ``Host`` are rejected with 403.
Requests without ``Origin`` (curl, the CLI, server-to-server) pass. Caddy's
``reverse_proxy`` forwards the original ``Host``, so the comparison holds
behind the web proxy.
"""

from __future__ import annotations

from collections.abc import Awaitable, Callable
from urllib.parse import urlsplit

from fastapi import Request, Response
from fastapi.responses import JSONResponse

UNSAFE_METHODS = frozenset({"POST", "PUT", "PATCH", "DELETE"})


def is_cross_origin(origin: str, host: str) -> bool:
    return urlsplit(origin).netloc.lower() != host.strip().lower()


async def origin_guard(
    request: Request, call_next: Callable[[Request], Awaitable[Response]]
) -> Response:
    if request.method in UNSAFE_METHODS and request.url.path.startswith("/api/"):
        origin = request.headers.get("origin")
        if origin is not None and is_cross_origin(
            origin, request.headers.get("host", "")
        ):
            return JSONResponse(status_code=403, content={"detail": "cross_origin"})
    return await call_next(request)
