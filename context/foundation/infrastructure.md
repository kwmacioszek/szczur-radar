---
project: szczur-radar
researched_at: 2026-09-26
recommended_platform: Railway
runner_up: Render
context_type: mvp
tech_stack:
  language: Java 25 (backend, since F-03 — was Python 3.13) + TypeScript (frontend)
  framework: Spring Boot 4 + Embabel (szczur-radar-api) / Vue 3 SPA built with Vite (szczur-radar-web)
  runtime: Java 25 from szczur-radar-api/Dockerfile (eclipse-temurin 25 -noble, ./mvnw build; Railpack until 2026-10-06) + static SPA; PostgreSQL
---

> **Update 2026-10-01 (F-03, `java-api-rewrite`):** the backend is now Spring Boot 4 on Java 25. Railpack detects Java from `pom.xml`, builds with `./mvnw` (tests skipped; CI runs them) and uses the JDK from `RAILPACK_JDK_VERSION` (`25`, declared in `.railway/railway.ts`; Railpack's default is 21). Start `java $JAVA_OPTS -jar target/szczur-radar-api.jar`, pre-deploy `java -jar target/szczur-radar-api.jar migrate` (Flyway). The JVM binds the wildcard address dual-stack, so the IPv4 healthcheck and the IPv6 private proxy both reach it. The platform decision below is unchanged; Python/uv specifics in the original research are historical where this note differs.

> **Update 2026-10-06 (`api-docker-image`):** the API no longer builds with Railpack. Railway auto-detects `szczur-radar-api/Dockerfile`, like the web service: `eclipse-temurin:25-jdk-noble` builds with `./mvnw`, `eclipse-temurin:25-jre-noble` runs the jar as a non-root user with `-XX:MaxRAMPercentage=75`. `.railway/railway.ts` sets no start command (the image `ENTRYPOINT` starts the server); the pre-deploy `migrate` is unchanged. The JDK is pinned by the image tag, so the Railpack JDK-21 trap is gone (`RAILPACK_JDK_VERSION` stays declared, unused). PITR is disabled and its bucket deleted (cost, user decision): **Backup bazy: brak do czasu subskrypcji** — before real data, Railway's daily volume backup and a restore test (risk #1 below). Measurements and runbooks: `context/deployment/deploy-plan.md`, section `api-docker-image`.

> **Update 2026-10-09 (S-02, `first-assessed-wykop-feed`):** there is **no separate cron service** for now. The pipeline runs **inside the API process**, in the background: an admin starts a run with `POST /api/admin/pipeline-runs` (202), runs are queued and executed one at a time, their state lives in Postgres (`pipeline_runs`, `posts`, `assessments`, `raw_payloads`), and on server start unfinished runs are marked interrupted and their posts put back. This replaces "a cron service for the daily pipeline, independent of the web/API lifecycle" below; how the daily run is triggered (cron container calling the API, or other) is decided by S-05. A deploy or sleep of the API interrupts a running run (operator rule: no merge to `main` while a run is `RUNNING`). Real Wykop and LLM calls are allowed in PR environments; the PR env and production use the same Wykop application keys (`WYKOP_KEY`, `WYKOP_SECRET`) — a deliberate exception to F-01's "secrets unavailable in PR environments". Details: `context/deployment/deploy-plan.md`, section `first-assessed-wykop-feed`.

> **Update 2026-10-11 (F-01, `production-data-gate`):** production runs on Railway **Hobby**, which has no built-in volume backups and no PITR (both are Pro, `railway.com/pricing`). The database is backed up by the cron service `db-backup` (`tools/db-backup/`, declared in `.railway/railway.ts`). It is the only cron service; the pipeline still runs in the API.
> - **Each run** (daily at 02:00 UTC): `pg_dump` without the session rows, a full restore check in a throwaway cluster, then the upload to the bucket `db-backups`.
> - **Storage:** one bucket per environment, region `ams`; 14 daily and 12 monthly copies. The operator's Synology pulls the bucket with Cloud Sync, so a copy also lives outside Railway.
> - **Other settings:** the Postgres volume is 5 GB, and the workspace usage limits are $5 (alert) and $10 (hard). Production's OpenRouter key has a $10/month limit: when it is spent, a run stops as a configuration error.
>
> The service, the restore runbook and the PR-env drill are in `context/deployment/deploy-plan.md`, section F-01.

## Recommendation

**Deploy on Railway.**

Railway runs both modules from one repo as separate services: `szczur-radar-api` (a Spring Boot jar built from its own Dockerfile since `api-docker-image`; built by Railpack from `pom.xml` after F-03; originally FastAPI + uv), `szczur-radar-web` (Vite SPA served with SPA fallback), a cron service for the daily LangGraph pipeline, and Postgres in the same project and the same EU region (Amsterdam). It passes the agent-accessible docs (`llms.txt`), stable deploy API and MCP (GA) criteria. In the scoring, **Render came first** (4 Pass + 1 Partial vs Railway's 3 Pass + 2 Partial) mainly because its Postgres is genuinely managed, with point-in-time restore on Hobby. The user **consciously overrode** that after the anti-bias check, preferring Railway's build DX (Railpack auto-detection) and built-in PR environments. The price of that choice is that Railway's Postgres is officially "unmanaged" and its backups are opt-in and still under development. That weakness is the first item in the risk register, with a mandatory mitigation.

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

The whole topology (api, web, cron, Postgres) in one project, one region and one bill, with the least build configuration: the API builds from its own multi-stage Dockerfile (JDK 25 pinned by the image, since `api-docker-image`; before: Railpack with `RAILPACK_JDK_VERSION`, originally `.python-version` + `uv.lock`), and the Vite build is served by its Dockerfile (Caddy). PR environments on every plan and a GA MCP server suit agent-driven work. It was chosen by the user over the scoring leader because of build DX and PR environments.

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

- Historical (since `api-docker-image` the JDK is pinned by the Dockerfile's base image): Railpack picks the JDK from `RAILPACK_JDK_VERSION`, defaulting to 21. The API needs 25 (`<java.version>25</java.version>` in `pom.xml`); dropping the variable from `.railway/railway.ts` silently builds with an older JDK and fails. (Before F-03 the same trap was `szczur-radar-api/.python-version`.)
- `VITE_*` variables are compiled into the frontend build, so changing the API URL requires a rebuild of `szczur-radar-web`, not a restart.
- The frontend is not a free CDN: Railpack serves `dist/` with Caddy in a usage-billed container.
- Private networking is IPv6 (environments created after 2025-10-16 also resolve IPv4). Service-to-service connections require a dual-stack listener; the JVM's default wildcard bind is dual-stack (before F-03 the Python API needed a dual-stack server explicitly).
- Nixpacks is deprecated. Tutorials and config based on `nixpacks.toml` no longer apply; the builder is Railpack.
- Long-lived RAW payloads grow independently from the query workload. Railway Hobby currently exposes 5 GB of Volume Storage for a service, while Buckets allow up to 1 TB combined storage on Hobby; storing every source JSON inline with Postgres can therefore make the volume limit arrive years earlier than the actual relational workload requires.

## Operational Story

- **Preview deploys**: Railway PR Environments enabled in project settings, in "Focused" mode (only services whose Watch Paths changed deploy). Bot PRs stay off. Before enabling, confirm the PR environment gets its own database, not a connection to production. Since F-01: a PR env of a PR that changes neither the API nor the web may come up with no database at all, with no Postgres volume and no deploys (`szczur-radar-pr-44`). Attach a volume and deploy by hand (`deploy-plan.md`, section F-01).
- **Secrets**: Railway variables per service. The LLM key and shared settings are a shared variable used by `szczur-radar-api` and the cron service. `DATABASE_URL` is a reference variable to the Postgres service. GitHub holds only a project-scoped `RAILWAY_TOKEN` (not the account-wide `RAILWAY_API_TOKEN`). Rotation: change the variable in Railway, then redeploy the api and cron services.
- **Rollback**: Railway dashboard → service → Deployments → Rollback on the previous deployment (the CLI has no rollback command). Rolling back code does not undo migrations: Flyway migrations are fix-forward (a new migration repairs a bad one; Flyway Community has no undo), and restoring a database backup is the last resort for data (since F-01: the daily dump and the restore runbook in `deploy-plan.md`, section F-01 — risk #1).
- **Approval**: merging to `main` is the human gate for production (auto-deploy on merge). Human-only: deleting or restoring the database/volume, enabling or changing backups, rotating the LLM key, changing plan, region or domain. An agent may do, unattended: read logs and deployment lists, `railway up` to non-production environments, and read-only operations via MCP.
- **Backups** (since F-01): the cron service `db-backup`, daily at 02:00 UTC.
  - Each run dumps the database, checks the dump by restoring it, and uploads it to the environment's bucket `db-backups` (14 daily + 12 monthly copies); the NAS keeps a copy outside Railway.
  - A failed run ends `CRASHED` and Railway e-mails it.
  - A restore is a human runbook (`deploy-plan.md`, section F-01), and up to about 24 h of data can be lost.
- **Logs**: `railway logs -n 200 --service szczur-radar-api` (likewise for web and the cron service), `railway deployment list --json`, plus the Railway MCP server (GA, `railway mcp local` or `mcp.railway.com`) for structured reads. Log retention: 7 days on Hobby, 30 days on Pro.

## Risk Register

| Risk | Source | Likelihood | Impact | Mitigation |
|---|---|---|---|---|
| No usable backups for the "unmanaged" Postgres (opt-in, under development, possibly Pro-only) | Devil's advocate | H | H | Before the first production data: check whether backups and PITR are available on the chosen plan; if not, move to Pro. Enable backups and do one test restore. If neither works, fall back to Render (managed Postgres with PITR). **Update 2026-10-06 (`api-docker-image`):** PITR (enabled 2026-09-27) is disabled and its bucket deleted for cost; **Backup bazy: brak do czasu subskrypcji**. Mitigation now: after subscribing and before the first real data (S-05), enable Railway's daily volume backup (Daily, kept 6 days; lost if the volume is wiped, restorable only in the same environment), take a manual backup and pass a query-verified restore test; otherwise fall back to Render. **Update 2026-10-11 (F-01):** Railway Hobby has no volume backups or PITR (Pro only), and the plan stays Hobby. Mitigation now: the cron `db-backup` takes a daily `pg_dump` and checks it by a full restore before upload, to a bucket per environment (14 + 12 copies) plus a NAS copy. The restore drill passed in a PR env (a test with the application on a restored dump and the restore runbook). Fall back to Render only if the backup stops working. |
| Cron pipeline writes to a schema from an older or newer migration | Pre-mortem | M | H | Migrations run as a pre-deploy step of the api service. The cron service deploys from the same commit (same Watch Paths). The pipeline checks the Flyway schema version at start and aborts on mismatch. |
| Manual pipeline run (FR-022) impossible without the Railway API | Devil's advocate | M | L | FR-022 is nice-to-have. When implemented, trigger through the Railway public API with a project-scoped token held only in backend variables, or as an internal job queued by the API. |
| Unattended rollback impossible (dashboard-only) | Devil's advocate | M | M | Rollback is a documented human step in the operational story. Migrations are fix-forward (Flyway); a database backup covers data (risk #1). |
| Service config not applied (IaC field mismatch or wrong root directory) | Devil's advocate | M | M | Config is declared in `.railway/railway.ts` (Infrastructure as Code, replaces `railway.toml`). Run `railway config plan` and confirm it adds exactly the intended services and deletes nothing before `railway config apply`; after the first deploy, verify the api start command came from the file. |
| First api deploy fails on Railpack's default start command | Devil's advocate | H | L | Closed (`api-docker-image`): the API builds from its own Dockerfile, whose `ENTRYPOINT` starts the server; `.railway/railway.ts` declares only the pre-deploy `java -jar target/szczur-radar-api.jar migrate`; the jar has a fixed name (`<finalName>`). |
| API URL baked into the frontend build | Unknown unknowns | M | L | Avoided: the SPA calls `/api/*` on its own origin (same-origin Caddy proxy), so there is no build-time `VITE_API_URL` and no cross-origin CORS to maintain. |
| Service-to-service over IPv6 private network | Unknown unknowns | L | M | web→api goes over the private network via the Caddy `/api/*` reverse proxy, so the API listens dual-stack (JVM default) and stays private (no public domain). For cron→Postgres, verify the driver connects to the IPv6 host. |
| JDK version change if `RAILPACK_JDK_VERSION` is removed | Unknown unknowns | L | M | Closed (`api-docker-image`): the JDK comes from the Dockerfile's base image (`eclipse-temurin:25-*-noble`); `RAILPACK_JDK_VERSION` is unused. Remaining form: the moving `25-jdk-noble`/`25-jre-noble` tags pick up Temurin 25 updates on rebuild — CI `api-container-smoke` builds the same image before Railway. |
| JVM cold start after `sleepApplication` (first request waits for Spring + Embabel) | F-03 | H | L | Caddy retries the upstream for up to 30 s while the API wakes (no 502); measured in `context/deployment/deploy-plan.md`. If too slow: AOT cache/CDS (Java 25) or disable sleep for the API — a separate decision. |
| RAW payload growth consumes the Postgres volume even though RAW is rarely queried | Research finding | M | M | Keep RAW behind a separate storage boundary, preserve it losslessly, and make compression transparent. Do not store full RAW JSON inline in the primary post row. Before production ingest, decide whether the initial backend is a separate DB representation or Railway Bucket; Bucket is the preferred candidate if it avoids consuming the 5 GB Hobby Postgres volume. Monitor RAW bytes and relational DB bytes separately. |
| Cost above the Hobby estimate (~$5–15/mo; derived, not quoted) | Research finding | M | L | Set a usage limit in Railway; the Pro plan (~$20/mo) is acceptable given the DX preference and possibly required for backups. **Update 2026-10-11 (F-01):** workspace usage limits set: $5 alert and $10 hard. The hard limit takes every service offline, PR envs included. The plan stays Hobby (backups by `pg_dump`, not Pro). Production's OpenRouter key has a $10/month limit; LLM costs are not covered by Railway's limits. |
| Only one EU region (Amsterdam) | Research finding | L | L | Acceptable (single region, small scale); all services and Postgres in the same region. |

### Storage note (checked 2026-09-27)

Railway documents Hobby Volume Storage at 5 GB and volume storage billing at $0.15/GB-month. Railway Buckets are S3-compatible, billed at $0.015/GB-month, and have a combined 1 TB capacity limit on Hobby. Buckets use the public network, so uploads from a Railway service count as service egress. Sources: https://docs.railway.com/volumes/reference, https://docs.railway.com/pricing/plans, https://docs.railway.com/storage-buckets/billing.

## Getting Started

> **Implementation note (2026-09-26):** the first deployment follows `context/deployment/deploy-plan.md`, which refines a few specifics below. Service topology and variables are declared as code in `.railway/railway.ts` (Railway Infrastructure as Code) rather than per-service `railway.toml`; validate with `railway config plan` before `railway config apply`. The web service is the only public one and reverse-proxies `/api/*` to the private API over the private network (Caddy → `RAILWAY_PRIVATE_DOMAIN`, API binds `::`), so there is **no** public API domain, **no** `VITE_API_URL`, and no cross-origin CORS. The steps below remain the correct mental model; treat the deploy plan as the source of truth where they differ.

1. Install the Railway CLI (see https://docs.railway.com/cli), run `railway login`, create a project with the region set to EU West (Amsterdam), and add a Postgres service. **Right away**, check the availability of backups/PITR on your plan and enable them (the first item in the risk register).
2. Add two services from the GitHub repo:
   - `szczur-radar-api`: Root Directory `szczur-radar-api`, Watch Paths `/szczur-radar-api/**`, config in `.railway/railway.ts`. Railway detects `szczur-radar-api/Dockerfile` (JDK 25 build, JRE 25 runtime, non-root user); before `api-docker-image` Railpack built it with `RAILPACK_JDK_VERSION`.
   - `szczur-radar-web`: Root Directory `szczur-radar-web`, Watch Paths `/szczur-radar-web/**`. Served by the committed multi-stage Dockerfile (Caddy): SPA fallback plus an `/api/*` reverse proxy to the private API — not Railpack static serving.
3. The pre-deploy command (`migrate`) is declared in `.railway/railway.ts` and the server starts from the image `ENTRYPOINT`; the API reads the Postgres reference variables `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD` and the operator-set `LLM_API_PROVIDER`, `LLM_MODEL`, `LLM_API_KEY`.
4. In `szczur-radar-web`, set `VITE_API_URL` to the public API domain (generate one in the api service — services have no public domain by default), and on the API side enable CORS for the frontend domain.
5. Once the pipeline exists: add a cron service with Root Directory `szczur-radar-api`, a daily schedule (UTC) and a start command that runs the pipeline and exits, sharing variables with the api. For CI: a project-scoped `RAILWAY_TOKEN` in GitHub Secrets, and either Railway's native GitHub autodeploy **or** `railway up --service <name> --detach` in Actions (not both, to avoid double deploys).

## Out of Scope

The following were not evaluated in this research:
- Docker image configuration
- CI/CD pipeline setup
- Production-scale architecture (multi-region, HA, DR)
