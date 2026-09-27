"""Szczur Radar API entrypoint.

Exposes the ASGI ``app`` so the container start command
``hypercorn main:app --bind "[::]:$PORT"`` binds it dual-stack on Railway:
IPv6 for private networking AND IPv4 for the platform healthcheck (uvicorn's
``::`` is IPv6-only from the CLI). ``python main.py`` runs a local dev server.
"""

from __future__ import annotations

import os

from fastapi import FastAPI

from app.routes import router

app = FastAPI(title="Szczur Radar API")
app.include_router(router)


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
