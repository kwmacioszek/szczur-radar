# Repository Guidelines

Szczur Radar: a Python/FastAPI backend (`api/`) collects and LLM-classifies investment posts from Wykop/Reddit; a Vue 3 + TypeScript SPA (`web/`) displays results.

## Hard Rules

- Access requires a valid server-side session (single seeded user for MVP) — see `@context/foundation/prd.md` § Access Control. Never add public registration, password reset, roles, or OAuth.
- Session tokens: store only a hash server-side, never the raw value. All protected endpoints gate through one `get_current_user` dependency — never ad-hoc checks.
- All `web/` ↔ `api/` communication goes through the API boundary — no direct DB access from `web/`, no bypassing `api/`.
- Never overwrite `context/**` — it's the canonical planning trail. If code contradicts `@context/foundation/prd.md`, flag it, don't resolve silently.
- New ingestion sources touch the collection/mapping layer in `api/` — evaluation, persistence, and API layers stay unchanged (NFR in `@context/foundation/prd.md`).

## Project Structure & Module Organization

- `api/` — Python ≥3.13, `uv`-managed. `main.py` is the default `uv init` stub; FastAPI app not wired up yet.
- `web/` — Vite + Vue 3 + TypeScript SPA, no router/data layer by design (`@context/foundation/tech-stack-web.md`); add `vue-router` once a second view is needed.
- `context/foundation/` — `prd.md`, `shape-notes.md`, `tech-stack*.md`: source of truth.
- `context/changes/bootstrap-verification/` — scaffold audit logs.

## Build, Test, and Development Commands

- Backend: `cd api && uv run python main.py` — runs the current stub; no FastAPI dev server exists until wired up.
- Frontend: `cd web && npm run dev` (Vite dev server) · `npm run build` (type-checks via `vue-tsc -b`, then builds) · `npm run preview`.

## Coding Style & Naming Conventions

- `web/` build type-checks against `@web/tsconfig.app.json` — `npm run build` fails on violations.
- Neither module has lint config (ESLint/Ruff) or a test runner yet — don't assume `pytest`/`vitest` defaults exist.

## Security & Configuration Tips

- LLM extraction output must conform to a fixed schema and avoid hallucinated fields (guardrail in `@context/foundation/prd.md` § Success Criteria).
- Batch posts per LLM call where volume allows; don't add a second per-post LLM call (e.g. trend scoring) without checking cost (assumed — no budget set).
- Password hashing: `pwdlib[argon2]`, not `passlib`. State-changing endpoints validate Origin/Referer — `SameSite=Strict` alone isn't sufficient CSRF protection. Production serves `web/`+`api/` under one origin to avoid cross-origin cookie/CORS complexity.

## Architecture Overview

- Domain rule: judges whether a post is a valuable investment signal (thesis + sentiment + novelty), attached to company/topic — see `@context/foundation/prd.md` § Business Logic.
