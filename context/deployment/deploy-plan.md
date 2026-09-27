# Railway integration and first deployment

## Summary

Deploy the scaffold first: a neutral Vue page, a minimal FastAPI app, and PostgreSQL in one Railway project in Amsterdam. Only the web service gets a public domain. It proxies /api/\* to the API over Railway’s private network, so the browser uses one origin. No collection job or protected data endpoint goes live in this release.

During implementation, update the existing context/deployment/deploy-plan.md in place. Use its checkboxes to record completion, deployment IDs, test results, and the web URL—never secret values.

## Phases

### [x] 1. Prepare the application

**Status (2026-09-26): done, verified locally.**

- Backend: FastAPI app in `szczur-radar-api/` — `main.py` exposes `app`; `app/routes.py` serves `GET /api/health` (liveness) and `GET /api/ready` (async SQLAlchemy 2 + psycopg `SELECT 1`, returns 503 on failure); `app/database.py` builds the async engine lazily from `DATABASE_URL`. Deps added via uv: `sqlalchemy[asyncio]`, `psycopg[binary]`; dev: `pytest`, `httpx`. Data/admin routes intentionally absent until auth.
- Tests: `tests/test_health.py` — **3 passed** (`/api/health` → 200; `/api/ready` → 200 when DB reachable, 503 when unavailable). `uv sync --locked` is clean.
- Frontend: neutral "W przygotowaniu" page (`src/App.vue`, trimmed `src/style.css`, `<title>Szczur Radar`); starter demo component/assets removed. `npm run build` (vue-tsc typecheck + vite) passes.
- Serving: `szczur-radar-web/Dockerfile` (multi-stage: `npm ci && npm run build` → Caddy), `Caddyfile` (SPA fallback, `/healthz` web health endpoint, `/api/*` reverse proxy preserving path via `API_PRIVATE_DOMAIN`/`API_PORT` env), `.dockerignore`. No DB/LLM creds in the frontend build. Container smoke test NOT run locally (Docker daemon down) — it runs in CI (phase 2).

Replace the backend stub with FastAPI. Add GET /api/health (process is running) and GET /api/ready (PostgreSQL query succeeds; return 503 on failure). Add SQLAlchemy 2 and the PostgreSQL driver through uv; keep data and admin routes absent until authentication is implemented.

Replace the Vue starter screen with a neutral “in preparation” page. Build it with npm ci && npm run build in a multi-stage Dockerfile, then serve it with Caddy. Configure SPA fallback, a web health endpoint, and a /api/\* proxy that preserves the path. Railway documents this Caddy proxy pattern (https://docs.railway.com/guides/spa-routing-configuration).

Keep DATABASE_URL in the API service. Pass the API’s private hostname and port to the web service through Railway reference variables; Caddy reads those variables rather than hardcoded upstream coordinates. Do not put database or future LLM credentials in the frontend build.

### [x] 2. Define infrastructure and CI

**Status (2026-09-26): artifacts written; IaC validation and autodeploy toggles are operator steps in phase 3/4.**

- `.railway/railway.ts` (SDK `railway@3.11.0` pinned in the repo-root `package.json`; the CLI resolves `railway/iac` from the root) declares: `postgres`; `szczur-radar-api` (Railpack, root `szczur-radar-api`, watch `szczur-radar-api/**`, start `uv run hypercorn main:app --bind "[::]:$PORT"` (dual-stack — see phase 4), healthcheck `/api/health`, `DATABASE_URL` → Postgres ref, region `europe-west4`); `szczur-radar-web` (detected Dockerfile, root `szczur-radar-web`, watch `szczur-radar-web/**`, healthcheck `/healthz`, `API_PRIVATE_DOMAIN` → api `RAILWAY_PRIVATE_DOMAIN`, `API_PORT` → api `PORT`, region `europe-west4`).
- `.github/workflows/ci.yml` runs on push to `main` + PR (NO path filter, per Wait for CI): backend `uv sync --locked` + `pytest`, and a web container smoke test whose image build (real Dockerfile) also covers frontend typecheck/build.
- Docs corrected: AGENTS.md CI/deploy lines; `infrastructure.md` risk rows + Getting Started note now reflect the private `/api/*` proxy (no `VITE_API_URL`, no public API domain, API binds `::`) and `.railway/railway.ts` IaC (replaces `railway.toml`).
- Configured variable NAMES (no values): api `DATABASE_URL`; web `API_PRIVATE_DOMAIN`, `API_PORT`. GitHub secret to add (operator): project-scoped `RAILWAY_TOKEN`.
- ⚠️ Operator to validate: `railway/iac` field names are written to the documented DSL but unverified against a live install — `railway config plan` (phase 3) is the safe gate; reconcile any rejected field there before `apply`. Enabling native autodeploy + Wait for CI are dashboard toggles (phase 3/4).

Add one .railway/railway.ts for szczur-radar-api, szczur-radar-web, and Postgres, with pinned TypeScript SDK dependencies. Set each service’s GitHub source and root directory; use Railpack for the API and the detected web Dockerfile. Make the API bind to Railway’s PORT, set its healthcheck, and reference Postgres. Reference the API service’s private domain and PORT from the web service. Railway’s Infrastructure as Code (https://docs.railway.com/infrastructure-as-code) replaces railway.toml for new services.

Add a GitHub Actions workflow that runs on every push to main and on PRs: locked backend dependency sync and health/readiness tests, frontend typecheck/build, and a web container smoke test. Avoid path filtering the required push workflow because Railway’s Wait for CI (https://docs.railway.com/deployments/github-autodeploys) evaluates whole workflow conclusions.

Use Railway native GitHub autodeploy for each service, gated by Wait for CI. GitHub Actions performs checks and does not issue a second deploy. Configure service watch paths for their respective module directories.

Make targeted corrections to the infrastructure and stack guidance for the selected private API route and current IaC flow. Correct the stale deployment line in AGENTS.md while preserving the user’s other working changes.

### [ ] 3. Provision and protect production

**Status (2026-09-26): partially done by operator — the remainder is a genuine operator-only gate.**

Done by operator: `railway config apply` → **SUCCESS**. Project `szczur-radar`, environment `production`; plan `3 to add, 0 to change, 0 to destroy` → `+ postgres`, `+ szczur-radar-api`, `+ szczur-radar-web`. This also validates the `.railway/railway.ts` IaC field names (the plan accepted them). CLI is logged in and the project linked (on the operator's machine).

**SDK-location fix (2026-09-27):** `railway config plan` (CLI v5.62.1) resolves the `railway/iac` import from `node_modules` at the **repo root**, not `.railway/`. An earlier code-review cleanup had moved the SDK under `.railway/`, so `plan` reported "SDK not installed". Fixed: committed root `package.json` + `package-lock.json` pinning `railway@3.11.0` (`node_modules/` gitignored), duplicate `.railway/package.json`/`tsconfig.json` removed. Operator: `npm install` at the repo root, then re-run `railway config plan`.

Remaining items are **operator-only Railway production operations**. The agent cannot perform them here: this session has no `railway` CLI on PATH, and the harness blocks Railway operations as `[Production Deploy]`. Operator checklist:
- [~] Billing guardrails **DEFERRED (trial)** — `railway usage limit set` → "Usage limits require an active subscription" (verified 2026-09-27); soft/hard limits aren't settable without a subscription. Set **$15** alert + **$25** hard limit **immediately after subscribing**; interim, rely on the trial budget + monitor `railway usage`. See incident note below and `lessons.md` (Railway trial gating).
- [x] Verify all 3 services + Postgres are in Amsterdam (`europe-west4`) — confirmed by operator (2026-09-27).
- [x] Public domain generated for **web only**: `szczur-radar-web-production.up.railway.app` (live, verified 2026-09-27). `szczur-radar-api` + `postgres` confirmed **no** public domain ✓.
- [x] Reference variables verified (`railway variables`, 2026-09-26): api `DATABASE_URL` → `postgres.railway.internal` ✓; web `API_PRIVATE_DOMAIN` → `szczur-radar-api.railway.internal` ✓. **`API_PORT` resolved EMPTY** ✗ — Railway does not expose a service's `PORT` as a referenceable variable. **Fixed in `.railway/railway.ts`**: pinned `API_INTERNAL_PORT=8000` (api binds it via `PORT`; web `API_PORT` is the matching literal). Needs `railway config plan` + `apply` to take effect, and must land **before** merge/deploy or the web→api proxy 502s.
- [x] **Postgres password rotated** by operator (2026-09-27) — the earlier plaintext `DATABASE_URL` exposure is now dead. DB connectivity confirmed intact afterwards: `/api/ready` → 200 (api → Postgres query OK).
- [~] **PITR enabled (2026-09-27)** — `railway postgres pitr enable` succeeded on the **trial**: bucket wired (`Postgres-PITR`), base backup present (`backupSetCount:1`, `archiverHealthy:true`), live WAL archiving. Backups work on trial → risk #1 (backups unavailable) **not** triggered. Automatic backup **schedule** is gated (`pitr schedule set` → "You do not have access"; revisit post-subscription).
- [~] **Restore drill (2026-09-27): PARTIAL — mechanism verified, restored instance crashed.** `railway postgres pitr restore --at 2026-09-27T09:59:39Z --new-service-name postgres-restored` → recovery **reached consistent state at the target** (`recovery stopping before commit of transaction 766, time 09:59:39`), which proves the backup + WAL archive are valid and restorable. **But the restored instance then CRASHED**: repeated `[28] No space left on device` writing `pg_wal`, ending `FATAL: could not write ... xlogtemp: No space left on device` — the restored-service volume was too small for the WAL replay (a trial/default-volume limit). Production `postgres` untouched (Online). So restore is mechanically proven but a **healthy, queryable** restored instance was not achieved on the trial. **Gate before Phase 6 (data collection):** re-run the drill with a larger restored volume (likely on subscription) until the restored instance comes up and is query-verified; if a clean restore can't be achieved with real data → **stop, reopen the Render fallback** (risk register #1). Cleanup: **delete the crashed `postgres-restored`** service (dashboard).

Deploy state (2026-09-27): `szczur-radar-api` **Online** (Hypercorn, deployment `78d0dfbb`), `szczur-radar-web` **Online** at `szczur-radar-web-production.up.railway.app`, `postgres` **Online**. api + postgres have **no** public domain ✓. All services + Postgres confirmed in Amsterdam (`europe-west4`).

Incident note — raising the $25 hard limit: the hard limit can take services offline when reached. Raise or temporarily remove it in the Railway usage / cost-control settings (https://docs.railway.com/pricing/cost-control), then redeploy/restart the affected services so they resume.

Operator: connect the Railway account to kwmacioszek/szczur-radar, choose the plan, and configure a $15 compute usage email alert and a $25 monthly hard compute usage limit before provisioning. The hard limit can take workloads offline when reached; record how to raise it during an incident. Install/login to the CLI, create or link the production project, run railway config plan, review that it adds exactly the intended services and deletes nothing, then run railway config apply. Verify all services and Postgres are in Amsterdam. Railway documents the usage-limit behavior (https://docs.railway.com/pricing/cost-control).

Generate a Railway domain only for web; leave API and Postgres without public domains or TCP proxies. Set Postgres DATABASE_URL by service reference. Confirm the web proxy reaches the API using its referenced private hostname and port.

Before collecting production data, enable scheduled Postgres volume backups and PITR, wait for the first restorable point, and perform a restore drill into a separate database service. Record the restored test result and recovery steps. Railway notes that PITR begins only after its first base backup (https://docs.railway.com/guides/postgres-backups-restores).

### [x] 4. Release and verify

**Status (2026-09-27): released AND verified — web page + both API probes work through one HTTPS domain; api + DB private.**

- PR #1 (scaffold) merged → autodeploy. First `szczur-radar-api` deploy **FAILED**: build OK and the app came up on `[::]:8000`, but Railway's **IPv4** healthcheck to `/api/health` got "service unavailable" for 5 min — uvicorn's `::` bind is IPv6-only from the CLI, so it rejects the IPv4 healthcheck.
- Fix (PR #2, merged): API start command → **Hypercorn** (`hypercorn main:app --bind "[::]:$PORT"`), which is dual-stack (IPv6 for the web→api private proxy + IPv4 for the healthcheck); verified locally that both IPv4 and IPv6 reach the `::` bind.
- **Config-apply gotcha (lesson):** merging PR #2 alone did NOT fix it — the deploy still ran uvicorn, because in the IaC model the start command lives in the service config and changes **only** via `railway config apply`. git merge + autodeploy rebuilds **code**, not **config**. After the operator ran `railway config apply` + redeploy, `szczur-radar-api` deployment **`78d0dfbb` SUCCESS** (log: Hypercorn `Running on http://[::]:8000`), healthcheck passed.
- Verified read-only (2026-09-27): api + web + postgres **Online**; api + postgres have **no** public domain ✓. Project `szczur-radar`, env `production`.
- ✅ Verified through `https://szczur-radar-web-production.up.railway.app` (2026-09-27): `/` → 200 (SPA, `<title>Szczur Radar`); `/api/health` → 200 `{"status":"ok"}`; `/api/ready` → 200 `{"status":"ready"}` (api → Postgres OK, incl. after the password rotation); `/api/nope` → 404 `{"detail":"Not Found"}` as JSON — API errors pass through the proxy, not the SPA page. api + postgres remain private. The "readiness 503 when DB down" path is covered by the local test (`szczur-radar-api/tests/test_health.py`).

Merge the checked scaffold to main, confirm GitHub Actions succeeds, then enable autodeploy and Wait for CI for both services. Check the deployed commit and deployment status for each service.

Verify /, /api/health, and /api/ready through the web domain; verify API and Postgres have no public URL. Test that readiness returns 503 with the database unavailable in a local test, and that the proxy does not turn API errors into the SPA page.

Record the web URL, Railway project/environment and deployment IDs, configured variable names, backup/restore result, and smoke-check results in the deployment plan. Railway healthchecks run at deployment, not as continuous monitoring (https://docs.railway.com/deployments/healthchecks), so include a routine operational check of logs and readiness.

### [ ] 5. Enable safe PR previews and document recovery

**Status (2026-09-26): support-steps runbook DONE (below). PR-preview enablement is operator-only and blocked until production works.**

#### Support steps / recovery runbook

- **Build failure — api:** Railpack didn't detect the app or Python version. Confirm `szczur-radar-api/.python-version` (3.13) and `uv.lock` are committed, and that the start command is the IaC one (`uv run hypercorn main:app --bind "[::]:$PORT"`), not the Railpack default `uvicorn main:app`. Inspect `railway logs --service szczur-radar-api`.
- **Build failure — web:** Dockerfile not detected, or `npm ci` / `vue-tsc` failed. Confirm `szczur-radar-web/Dockerfile` sits at the service root directory and `package-lock.json` is committed. The identical build is green in CI, so a Railway-only failure points at root-directory / build-context config.
- **Proxy 502 (web → api):** Caddy can't reach the backend. Check the web service's `API_PRIVATE_DOMAIN` / `API_PORT` reference variables resolve to the api's `RAILWAY_PRIVATE_DOMAIN` / `PORT`; confirm the api is up (Hypercorn binds `::` dual-stack); confirm the api deployment is live and healthy.
- **Readiness 503:** `/api/ready` failed its `SELECT 1`. Check the api `DATABASE_URL` reference resolves and Postgres is up; read `railway logs --service szczur-radar-api`. Health 200 while ready 503 = app up, DB unreachable.
- **CI wait / deploy stuck in `WAITING`:** Wait for CI is waiting on a workflow conclusion. Confirm the GitHub Actions run **concluded** (not skipped — do not path-filter the workflow) and the Railway GitHub app has repo permissions.
- **Bad release:** roll back via dashboard → service → Deployments → Rollback (the CLI has no rollback). Code rollback does **not** roll back data — assess Postgres / migration state separately; every migration needs a tested downgrade or a fix-forward.
- **Backups/PITR can't be enabled or restore-tested:** **stop before collecting production data** and reopen the documented Render fallback (managed Postgres with PITR) — risk register #1.
- **IaC config change didn't take effect (config ≠ merge):** changing `.railway/railway.ts` (start command, healthcheck, variables, region) needs `railway config apply` — git merge + autodeploy rebuilds **code**, not service **config**. Symptom: the deploy keeps running the OLD setting after a merged config change (here: the deploy still ran uvicorn until `railway config apply` pushed the Hypercorn start command). Fix: `railway config apply`, then redeploy.
- **Healthcheck "service unavailable" while the app logs look healthy:** Railway's healthcheck uses **IPv4**. uvicorn `--host ::` is IPv6-only from the CLI and rejects it; bind dual-stack (Hypercorn on `::`) or `0.0.0.0` (but `0.0.0.0` breaks the IPv6 web→api private proxy). Also confirm the app listens on `$PORT` and does not block the `healthcheck.railway.app` host.

Before enabling PR previews, seal all production-only secrets. Verify that preview environments cannot inherit those values; add separate preview credentials only when needed. Railway does not copy sealed values into PR environments (https://docs.railway.com/variables).

Enable full isolated PR environments after production works. Verify a preview has its own Postgres and that its /api/\* proxy cannot reach production. Keep bot PR previews off. This replaces the Focused setting in infrastructure.md: Railway’s guide says a Focused web preview can reuse the production backend (https://docs.railway.com/guides/preview-deployments-with-pr-environments).

Add support steps to the plan: for a build failure check Python version or Dockerfile detection; for a proxy 502 check the referenced hostname, port, and API bind address; for readiness 503 check the database reference and API logs; for a CI wait inspect GitHub permissions and workflow conclusions. For a bad release, use Railway’s deployment rollback, then assess database state separately. If backups or PITR cannot be enabled and restore-tested, stop before data collection and reopen the documented Render fallback decision.

### [ ] 6. Gate later external integrations

When the MVP pipeline exists, add it as a separate Railway cron service from the backend codebase. Give it only the database and required Wykop/LLM credentials, make it exit after each run, and check its schema revision before writing. Railway uses UTC and skips a run while the previous run remains active (https://docs.railway.com/cron-jobs).

Enable real outbound Wykop and LLM calls only in production after credentials, spend limits, failure visibility, and a dry run are checked. Keep them out of PR previews. CLI account bootstrap and recovery remain backend operations; the MVP adds no public registration or automated email.

## Acceptance and assumptions

The first release passes when the web page and both API probes work through one HTTPS domain, the API and database remain private, CI gates deployments, the usage alert and hard limit are active, and a Postgres restore drill succeeds. The daily pipeline, accounts, Wykop, and LLM integration are later phases.

This plan follows the chosen Railway platform and the confirmed scaffold-first, private-proxy, native-autodeploy, full-preview decisions. No repository or Railway changes are made while this plan is under review.
