"""Szczur Radar API entrypoint.

Exposes the ASGI ``app`` so the container start command
``hypercorn main:app --bind "[::]:$PORT"`` binds it dual-stack on Railway:
IPv6 for private networking AND IPv4 for the platform healthcheck (uvicorn's
``::`` is IPv6-only from the CLI). ``python main.py`` runs a local dev server.
"""

from __future__ import annotations

import os
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.auth.routes import router as auth_router
from app.auth.tokens import jwt_secret
from app.routes import router
from app.security import origin_guard


@asynccontextmanager
async def lifespan(_: FastAPI) -> AsyncIterator[None]:
    # Fail fast: without a valid JWT_SECRET the API must not start (a PR env
    # missing the secret fails its deploy loudly instead of serving 500s).
    jwt_secret()
    yield


app = FastAPI(title="Szczur Radar API", lifespan=lifespan)
app.middleware("http")(origin_guard)
app.include_router(router)
app.include_router(auth_router)


def main() -> None:
    import uvicorn

    uvicorn.run(
        "main:app",
        host="0.0.0.0",
        port=int(os.environ.get("PORT", "8000")),
        reload=True,
    )


if __name__ == "__main__":
    main()
