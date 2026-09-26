# Repository Guidelines

Szczur Radar collects investment posts (Wykop, later Reddit), classifies them with an LLM, and serves them to invited users. One repo, two independently deployed modules: a FastAPI backend and a Vue 3 + TypeScript SPA.

## Hard Rules

- `context/` is the planning trail: never overwrite or reformat it; never write to `context/archive/` (@CLAUDE.md). `context/foundation/` docs are edited in place (@context/foundation/README.md); `lessons.md` is append-only.
- If code contradicts @context/foundation/prd.md, flag it — don't silently resolve it in code.
- `szczur-radar-web/` talks to the backend only through its REST API: no direct DB access and no LLM calls from the browser (FR-032).
- No public registration and no automated email in MVP. Accounts are created, bootstrapped, and recovered via backend CLI commands (PRD § Access Control, FR-026–028).
- A new data source changes only acquisition and mapping to the shared model; evaluation, persistence, and API layers stay untouched (PRD § Non-Functional Requirements).
- Check an FR's priority in the PRD before building it: `must-have` first, `nice-to-have` only when asked.

## Project Structure & Module Organization

- `szczur-radar-api/` — Python ≥3.13, managed by `uv`. `main.py` is still the `uv init` stub; no FastAPI app is wired yet. Planned stack (FastAPI, Pydantic, LangGraph, PostgreSQL, SQLAlchemy 2, Alembic, FastAPI Users): @context/foundation/tech-stack-api.md.
- `szczur-radar-web/` — Vite + Vue 3 + TypeScript SPA with no router, store, or tests yet: @context/foundation/tech-stack-web.md.
- `context/foundation/` — `prd.md` (requirements, FR priorities), `shape-notes.md`, `tech-stack-*.md`, `lessons.md`.
- `context/changes/bootstrap-verification/` — scaffold and audit logs per module.

## Build, Test, and Development Commands

- Backend: `cd szczur-radar-api && uv sync`; add packages with `uv add <pkg>` (never `pip install`); run with `uv run python main.py`.
- Frontend: `cd szczur-radar-web && npm install`, then `npm run dev`, `npm run build`, `npm run preview`.
- `npm run build` runs `vue-tsc -b` first, so it is the frontend type-check gate.

## Coding Style & Testing

- No linter, formatter, or test runner is configured in either module, and there is no CI yet. Don't assume `pytest`, `vitest`, `ruff`, or ESLint exist — add them explicitly with the change that needs them.
- Frontend TypeScript settings (including `noUnusedLocals`/`noUnusedParameters`): @szczur-radar-web/tsconfig.app.json.

## Deployment

- Target: Railway, with a separate deploy per module via GitHub Actions. Nothing is configured yet; the platform decision is pending `/10x-infra-research`.
