<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Obraz Dockera dla `szczur-radar-api` i wyłączenie PITR

- **Plan**: context/changes/api-docker-image/plan.md
- **Scope**: Phase 1 of 2
- **Reviewed phases**: 1
- **Date**: 2026-10-06
- **Verdict**: APPROVED
- **Findings**: 0 critical, 1 warning, 3 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | PASS |

Notes:
- Plan Adherence: Dockerfile, `.dockerignore`, IaC (`start` removed, bucket removed, comments, `RAILPACK_JDK_VERSION` comment) and the `api-container-smoke` job match their contracts. `.dockerignore` adds `.vscode` and `.git` (harmless). Fallbacks (`startCommand: null`, `preDeploy: "migrate"`) were not needed: the PR-env plan cleared `deploy.startCommand` and the pre-deploy replaced `ENTRYPOINT`.
- Scope Discipline: the first commit also carries `REPOSITORY_GUIDELINES.md` and roadmap v2.7 (+ renderer, image) — planning output of this change, staged by the user's decision.
- Success Criteria: 1.1–1.3 and 1.12 re-checked locally (1.2, 1.12 re-run 2026-10-06), 1.4–1.6 green on run 37366485659 (attempt 4). 1.10 adapted by user decision: `railway ssh` logs in as root, the app process runs as `app` (recorded in `change.md` and the PR body). Manual items 1.7–1.11, 1.13 have evidence in Railway logs, operator output and the PR body.

## Findings

### F1 — Production PITR is still active and archiving WAL

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: N/A (production `postgres`; plan § Runbook cleanupu, steps 0–2)
- **Detail**: `railway postgres pitr status -e production` (read-only, 2026-10-06): `enabled: true`, `bucketWired: true`, `archiverHealthy: true`, `backupSetCount: 9`, `archiverLastArchivedAt: 2026-10-06 06:26:48`. `railway bucket list -e production` returns `[]`. The bucket the operator deleted is no longer listed in the project, yet Postgres still archives to its storage — the runbook warns about exactly this ("potwierdzić faktyczne usunięcie, a nie tylko staged deletion"). The cost the user wanted to stop likely continues until PITR is disabled on production.
- **Fix**: Run the production part of the runbook before merge (independent of the code): step 0 decision on the Daily volume backup (or the user's explicit "no backup until subscription", since the DB holds no data), then `railway postgres pitr disable` on production, wait for the Postgres deploy, confirm `enabled: false` / `bucketWired: false`, and check billing/storage in the dashboard that the bucket storage is gone.
  - Strength: Stops WAL archiving and its storage cost; matches the plan's order (cleanup before applying IaC without the bucket).
  - Tradeoff: Production Postgres redeploys (brief unavailability); the 9 backup sets become unusable.
  - Confidence: HIGH — status read directly from production.
  - Blind spot: Whether Railway still bills the hidden bucket after `pitr disable`; check billing afterwards.
- **Decision**: FIXED (2026-10-06) — operator ran `railway postgres pitr disable` on production without the Daily backup (empty DB; user decision: no backup until subscription). Verified: `enabled: false`, `bucketWired: false`, `/api/ready` via production web = 200. CLI: "existing backups are kept" — check billing that the hidden bucket storage stops being charged.

### F2 — CI smoke runs `migrate` differently from the Railway pre-deploy

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Success Criteria
- **Location**: .github/workflows/ci.yml (job `api-container-smoke`, step "Run migrations (Railway pre-deploy command)")
- **Detail**: CI appends `migrate` to the image `ENTRYPOINT` (absolute jar path, `-XX:MaxRAMPercentage=75`), while Railway's pre-deploy replaces the `ENTRYPOINT` with `java -jar target/szczur-radar-api.jar migrate` (relative to `WORKDIR /app`). The relative path and `WORKDIR` — the pre-deploy contract — are only verified manually in the PR env.
- **Fix**: Run the exact pre-deploy command in CI: `docker run --rm --network host --entrypoint java … szczur-radar-api -jar target/szczur-radar-api.jar migrate`.
- **Decision**: FIXED (2026-10-06) — CI runs `--entrypoint java szczur-radar-api -jar target/szczur-radar-api.jar migrate` (exact Railway preDeploy); verified locally: exit 0, `started by app in /app`, migrations v1–v2.

### F3 — Stale PITR bucket example in the CI gate comment

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: .github/workflows/ci.yml:284
- **Detail**: `# Never destroy anything automatically (e.g. the Postgres-PITR bucket).` refers to a resource that no longer exists in IaC.
- **Fix**: Change the example to a generic one, e.g. `(e.g. a database, bucket or variable)`.
- **Decision**: FIXED (2026-10-06) — comment now says `(e.g. a database, bucket or variable)`.

### F4 — Hikari pool loses all connections while the API is idle (runtime, outside the diff)

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: N/A (PR env runtime logs 2026-10-06 06:17:00, 06:22:00)
- **Detail**: `HikariPool-1 - Failed to validate connection … (This connection has been closed.)` for all 10 pooled connections when the Spring Session cleanup task runs, a few minutes after start. Hikari drops and reopens them, so it is not a failure. Probable cause: the sleeping Postgres or the private network closing idle connections. Not related to the Docker image; unknown whether it also happened under Railpack.
- **Fix**: Follow-up outside this change: check production logs from before the merge; if it appears there too, a separate change with `keepaliveTime`/`maxLifetime` (or a smaller `minimumIdle`).
- **Decision**: FIXED (2026-10-06) — queued as a separate change in `follow-ups/review-fixes.md` (no code in this change).
