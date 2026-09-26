---
project: szczur-radar
researched_at: 2026-09-26
recommended_platform: Railway
runner_up: Render
context_type: mvp
tech_stack:
  language: Python 3.13 (backend) + TypeScript (frontend)
  framework: FastAPI + LangGraph (szczur-radar-api) / Vue 3 SPA built with Vite (szczur-radar-web)
  runtime: Python 3.13 via uv (container) + static SPA; PostgreSQL
---

## Recommendation

**Deploy on Railway.**

Railway runs both modules from one repo as separate services: `szczur-radar-api` (FastAPI + uv, auto-detected by Railpack), `szczur-radar-web` (Vite SPA served with SPA fallback), a cron service for the daily LangGraph pipeline, and Postgres in the same project and the same EU region (Amsterdam). It passes the agent-accessible docs (`llms.txt`), stable deploy API and MCP (GA) criteria. In the scoring, **Render came first** (4 Pass + 1 Partial vs Railway's 3 Pass + 2 Partial) mainly because its Postgres is genuinely managed, with point-in-time restore on Hobby. The user **consciously overrode** that after the anti-bias check, preferring Railway's build DX (Railpack auto-detection) and built-in PR environments. The price of that choice is that Railway's Postgres is officially "unmanaged" and its backups are opt-in and still under development. That weakness is the first item in the risk register, with a mandatory mitigation.

## Platform Comparison

Hard filters: the backend needs a Python runtime able to run FastAPI + async SQLAlchemy + a Postgres driver + LangGraph, and a daily multi-minute job running **independently of the web process** (interview: no persistent client connections, but background jobs as a separate cron/job/worker). Interview weights: DX over cost; no prior platform experience; a single region is enough; database from the same vendor preferred.

| Platform | CLI-first | Managed/Serverless | Agent-readable docs | Stable deploy API | MCP / Integration | Total |
|---|---|---|---|---|---|---|
| Render | Partial | Pass | Pass | Pass | Pass (GA) | 4P + 1Pa |
| **Railway** | Partial | Partial | Pass | Pass | Pass (GA) | 3P + 2Pa |
| Vercel | Pass | Partial | Pass | Pass | Partial (beta) | 3P + 2Pa |
| Fly.io | Partial | Partial | Pass | Pass | Partial (experimental) | 2P + 3Pa |
| Cloudflare | — | — | — | — | — | dropped (stack) |
| Netlify | — | — | — | — | — | dropped (no Python) |

- **Render**
  - Native Python with uv (requires `uv.lock` in the service root). Static sites are free on a CDN.
  - Cron jobs run up to 12 h and have a manual "Trigger Run". Postgres is managed, with PITR (3 days on Hobby, 7 on Pro), in Frankfurt.
  - MCP is GA. The CLI has no rollback or env-var management. Previews need Pro. The plans were restructured in April 2026.
- **Railway**
  - Railpack (the default builder; Nixpacks is deprecated) detects uv + FastAPI and Vite (served with SPA fallback).
  - Per-service Root Directory and Watch Paths. PR environments, including "Focused" ones. MCP is GA. The only EU region is Amsterdam.
  - Postgres is an "unmanaged" template: backups are opt-in, labelled "a newer feature still under development", and possibly Pro-only. PITR is via pgBackRest, with status and plan unspecified.
  - Rollback is dashboard-only. Cron has no manual trigger and no documented runtime limit.
- **Vercel**
  - FastAPI + uv on Python 3.13 works, but as serverless functions (300 s, 800 s max on Pro).
  - Cron is an HTTP call with no retries and best-effort delivery. There is no first-party Postgres, only Marketplace providers (Neon/Supabase).
  - Hobby prohibits commercial use.
- **Fly.io**
  - Any container. Managed Postgres with HA/backups/PITR, but still not feature-complete by its own docs, from about $38/mo.
  - uv needs your own Dockerfile. Schedules have no exact hour. MCP is experimental. No free tier.
- **Cloudflare**
  - Python Workers (GA 2026-09-21) don't support async SQLAlchemy or psycopg.
  - Containers (GA) work but sleep after inactivity and can't be pinned to a region. There is no first-party Postgres, only PlanetScale billed through Cloudflare.
- **Netlify**
  - Functions run JS/TS only, with Go via Lambda-compat, deprecated from 2027-07. No Python runtime.

### Shortlisted Platforms

#### 1. Railway (Recommended)

The whole topology (api, web, cron, Postgres) in one project, one region and one bill, with the least build configuration: Railpack reads `szczur-radar-api/.python-version` (3.13) and `uv.lock`, and serves the Vite build without extra setup. PR environments on every plan and a GA MCP server suit agent-driven work. It was chosen by the user over the scoring leader because of build DX and PR environments.

#### 2. Render

Won the scoring: the only candidate combining native Python + uv, genuinely managed Postgres with PITR on Hobby, and cron jobs with a manual run, in Frankfurt. The gaps vs Railway: previews only on Pro, rollback from the dashboard disables autodeploy, and recent pricing churn. **The fallback if Railway's backup risk can't be closed (see the risk register).**

#### 3. Fly.io

The most mature Postgres of the three (HA, backups, PITR on every plan) and full control, but the most operational work (your own uv Dockerfile, a container for the SPA, workarounds for exact-time scheduling) and the highest cost (~$45–55/mo).

## Anti-Bias Cross-Check: Railway

### Devil's Advocate — Weaknesses

1. **Postgres is an "unmanaged" template** (a container with a volume; you patch it). Backups are opt-in and "still under development", possibly Pro-only; PITR has unspecified status and tier. This clashes directly with the PRD NFR that delegates backup to the platform.
2. **Cron has no manual trigger and no documented runtime limit.** Times are UTC, and a run is skipped if the previous one is still going. The manual pipeline run (FR-022) requires Railway's public API and a token stored in the backend.
3. **Rollback is dashboard-only.** `railway redeploy` only redeploys the latest build, so an agent can't roll back unattended.
4. **`railway.json`/`railway.toml` does not follow the Root Directory.** It needs an absolute path (`/szczur-radar-api/railway.toml`); a mistake silently leaves the service unconfigured.
5. **Railpack's default start command is `uvicorn main:app`.** The current `main.py` is not a FastAPI app, so without an explicit start command the first deploy will fail.

### Pre-Mortem — How This Could Fail

The team put api, web, cron and Postgres on Railway on the Hobby plan, and everything was up within an hour because Railpack detected uv and Vite on its own. They assumed "a database from the same vendor" meant a managed one, so nobody turned on backups, and on Hobby they might not have been available anyway. Three months later, an Alembic migration dropping a column went to production through auto-deploy on merge. The cron pipeline, built from an older commit, wrote broken records overnight. A dashboard rollback restored the code but not the data. There was no volume copy and PITR had never been configured. Three months of classified signals had to be reprocessed, paying for LLM calls again, which is exactly what the cost guardrail was meant to prevent. The root cause wasn't Railway; it was mistaking build convenience for a "managed" data layer.

### Unknown Unknowns

- Railpack reads the Python version from `.python-version`, not `requires-python`. The repo already has `3.13` in `szczur-radar-api/.python-version`; removing that file silently changes the runtime.
- `VITE_*` variables are compiled into the frontend build, so changing the API URL requires a rebuild of `szczur-radar-web`, not a restart.
- The frontend is not a free CDN: Railpack serves `dist/` with Caddy in a usage-billed container.
- Private networking is IPv6. Service-to-service connections require listening on `::` (Railway's FastAPI guide uses Hypercorn bound to `::`).
- Nixpacks is deprecated. Tutorials and config based on `nixpacks.toml` no longer apply; the builder is Railpack.

## Operational Story

- **Preview deploys**: Railway PR Environments enabled in project settings, in "Focused" mode (only services whose Watch Paths changed deploy). Bot PRs stay off. Before enabling, confirm the PR environment gets its own database, not a connection to production.
- **Secrets**: Railway variables per service. The LLM key and shared settings are a shared variable used by `szczur-radar-api` and the cron service. `DATABASE_URL` is a reference variable to the Postgres service. GitHub holds only a project-scoped `RAILWAY_TOKEN` (not the account-wide `RAILWAY_API_TOKEN`). Rotation: change the variable in Railway, then redeploy the api and cron services.
- **Rollback**: Railway dashboard → service → Deployments → Rollback on the previous deployment (the CLI has no rollback command). Rolling back code does not undo Alembic migrations: every migration must have a tested `downgrade`, or be fixed forward with a new migration.
- **Approval**: merging to `main` is the human gate for production (auto-deploy on merge). Human-only: deleting or restoring the database/volume, enabling or changing backups, rotating the LLM key, changing plan, region or domain. An agent may do, unattended: read logs and deployment lists, `railway up` to non-production environments, and read-only operations via MCP.
- **Logs**: `railway logs -n 200 --service szczur-radar-api` (likewise for web and the cron service), `railway deployment list --json`, plus the Railway MCP server (GA, `railway mcp local` or `mcp.railway.com`) for structured reads. Log retention: 7 days on Hobby, 30 days on Pro.

## Risk Register

| Risk | Source | Likelihood | Impact | Mitigation |
|---|---|---|---|---|
| No usable backups for the "unmanaged" Postgres (opt-in, under development, possibly Pro-only) | Devil's advocate | H | H | Before the first production data: check whether backups and PITR are available on the chosen plan; if not, move to Pro. Enable backups and do one test restore. If neither works, fall back to Render (managed Postgres with PITR). |
| Cron pipeline writes to a schema from an older or newer migration | Pre-mortem | M | H | Migrations run as a pre-deploy step of the api service. The cron service deploys from the same commit (same Watch Paths). The pipeline checks the Alembic revision at start and aborts on mismatch. |
| Manual pipeline run (FR-022) impossible without the Railway API | Devil's advocate | M | L | FR-022 is nice-to-have. When implemented, trigger through the Railway public API with a project-scoped token held only in backend variables, or as an internal job queued by the API. |
| Unattended rollback impossible (dashboard-only) | Devil's advocate | M | M | Rollback is a documented human step in the operational story. Migrations always have a `downgrade`. |
| Service config not applied (IaC field mismatch or wrong root directory) | Devil's advocate | M | M | Config is declared in `.railway/railway.ts` (Infrastructure as Code, replaces `railway.toml`). Run `railway config plan` and confirm it adds exactly the intended services and deletes nothing before `railway config apply`; after the first deploy, verify the api start command came from the file. |
| First api deploy fails on the default `uvicorn main:app` | Devil's advocate | H | L | Explicit start command declared in `.railway/railway.ts` (`uv run uvicorn main:app --host :: --port $PORT`); the real FastAPI app now exists (`main.py` exposes `app`). |
| API URL baked into the frontend build | Unknown unknowns | M | L | Avoided: the SPA calls `/api/*` on its own origin (same-origin Caddy proxy), so there is no build-time `VITE_API_URL` and no cross-origin CORS to maintain. |
| Service-to-service over IPv6 private network | Unknown unknowns | L | M | web→api goes over the private network via the Caddy `/api/*` reverse proxy, so the API binds `::` (`uvicorn --host ::`) and stays private (no public domain). For cron→Postgres, verify the driver connects to the IPv6 host. |
| Python version change if `.python-version` is removed | Unknown unknowns | L | M | Keep `szczur-radar-api/.python-version` in the repo; optionally also `RAILPACK_PYTHON_VERSION=3.13`. |
| Cost above the Hobby estimate (~$5–15/mo; derived, not quoted) | Research finding | M | L | Set a usage limit in Railway; the Pro plan (~$20/mo) is acceptable given the DX preference and possibly required for backups. |
| Only one EU region (Amsterdam) | Research finding | L | L | Acceptable (single region, small scale); all services and Postgres in the same region. |

## Getting Started

> **Implementation note (2026-09-26):** the first deployment follows `context/deployment/deploy-plan.md`, which refines a few specifics below. Service topology and variables are declared as code in `.railway/railway.ts` (Railway Infrastructure as Code) rather than per-service `railway.toml`; validate with `railway config plan` before `railway config apply`. The web service is the only public one and reverse-proxies `/api/*` to the private API over the private network (Caddy → `RAILWAY_PRIVATE_DOMAIN`, API binds `::`), so there is **no** public API domain, **no** `VITE_API_URL`, and no cross-origin CORS. The steps below remain the correct mental model; treat the deploy plan as the source of truth where they differ.

1. Install the Railway CLI (see https://docs.railway.com/cli), run `railway login`, create a project with the region set to EU West (Amsterdam), and add a Postgres service. **Right away**, check the availability of backups/PITR on your plan and enable them (the first item in the risk register).
2. Add two services from the GitHub repo:
   - `szczur-radar-api`: Root Directory `szczur-radar-api`, Watch Paths `/szczur-radar-api/**`, config `/szczur-radar-api/railway.toml`. Railpack picks up `.python-version` (3.13) and `uv.lock`.
   - `szczur-radar-web`: Root Directory `szczur-radar-web`, Watch Paths `/szczur-radar-web/**`. Served by the committed multi-stage Dockerfile (Caddy): SPA fallback plus an `/api/*` reverse proxy to the private API — not Railpack static serving.
3. In `szczur-radar-api/railway.toml`, set an explicit start command for the FastAPI app (e.g. `uv run uvicorn <module>:app --host 0.0.0.0 --port $PORT`). The current `main.py` is only a `uv init` stub, so this step makes sense once the first app module exists. Set `DATABASE_URL` as a reference variable to Postgres.
4. In `szczur-radar-web`, set `VITE_API_URL` to the public API domain (generate one in the api service — services have no public domain by default), and on the API side enable CORS for the frontend domain.
5. Once the pipeline exists: add a cron service with Root Directory `szczur-radar-api`, a daily schedule (UTC) and a start command that runs the pipeline and exits, sharing variables with the api. For CI: a project-scoped `RAILWAY_TOKEN` in GitHub Secrets, and either Railway's native GitHub autodeploy **or** `railway up --service <name> --detach` in Actions (not both, to avoid double deploys).

## Out of Scope

The following were not evaluated in this research:
- Docker image configuration
- CI/CD pipeline setup
- Production-scale architecture (multi-region, HA, DR)
