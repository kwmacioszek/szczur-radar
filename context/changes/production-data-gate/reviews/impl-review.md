<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: F-01 production-data-gate

- **Plan**: context/changes/production-data-gate/plan.md
- **Scope**: Full plan
- **Reviewed phases**: 1, 2, 3, 4, 5
- **Date**: 2026-10-12
- **Verdict**: NEEDS ATTENTION (all 9 findings fixed in triage, 2026-10-12)
- **Findings**: 0 critical, 5 warnings, 4 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | WARNING |
| Pattern Consistency | WARNING |
| Success Criteria | PASS |

The plan-drift pass found no drift. Every planned change is implemented with the plan's intent. The approved adaptations are implemented consistently and documented in `railway.ts` comments and `deploy-plan.md` (F-01):
- `RESOURCE_LIMITS`;
- `jq` in the image;
- no `checkSuites` on `db-backup`;
- `Observed.limitSource`;
- a `Set<String>` for `limit_source`.

The unplanned files are `OpenRouterRawResponse.java` (justified) and two javadoc-only edits (harmless). "What We're NOT Doing" is respected.

The automated criteria were re-run on 2026-10-12, all green:
- 1.1 / 1.3;
- 2.1–2.4 (`test.sh` 9 × PASS);
- 3.1–3.2: PR-44 plan, and the production plan that the merge will apply, both pass the gate;
- 4.1, and 4.2 on CI `592cf1e`;
- 5.1–5.4.

Every manual item has evidence in the diff or in the conversation.

## Findings

### F1 — A hung backup run silently stops all later backups

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: tools/db-backup/backup.sh:104-177 (pg_dump at :126)
- **Detail**: Nothing in a run has a time limit. A run never exits if:
  - `pg_dump` waits behind an ACCESS EXCLUSIVE lock (e.g. `migrate` DDL at 02:00; there is no `--lock-wait-timeout`);
  - or a connection stalls mid-query (libpq keepalive defaults allow hours).

  The run then never ends `CRASHED` and sends no e-mail. Railway skips every later run while it is active (docs: cron-jobs, "if a previous execution is still running … Railway will skip the new cron job"), so backups stop without any alert.
- **Fix**: Add `pg_dump --lock-wait-timeout=5min` and a hard limit on the whole run: re-exec `backup.sh` under `timeout --kill-after=60s 2h` once (an env guard to avoid recursion). The limit must be far above today's ~5 s run.
  - Strength: turns every hang into a normal failure, with `CRASHED` and an e-mail, and frees the next schedule. `timeout` is in coreutils in the image.
  - Tradeoff: picking limits; too short a limit fails a healthy run once the database is large.
  - Confidence: HIGH — the skip behaviour is documented, and the lock wait is standard PostgreSQL behaviour.
  - Blind spot: whether Railway itself caps a cron container's run time is undocumented ("does not automatically terminate deployments").
- **Decision**: FIXED — `pg_dump --lock-wait-timeout=5min`; the whole run re-executes under `timeout --kill-after=60s ${BACKUP_TIMEOUT:-2h}` and logs `FAILED: the run exceeded …`; test.sh case `BACKUP_TIMEOUT=1s`.

### F2 — Retention trusts any file whose name sorts last (bucket and NAS)

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: tools/db-backup/backup.sh:82, 87-100; tools/db-backup/nas-retention.sh:43
- **Detail**: Both pickers accept any `szczur-radar-*.dump` and treat name order as age. A hand-copied `szczur-radar-before-restore.dump` sorts after the timestamped names (`b` > `2`), as would a future-dated name. Such a file is never pruned and permanently holds a slot. With enough of them, the dump uploaded in the same run is deleted while the run still logs `OK`. On the NAS, files injected into the bucket (by anyone holding its keys) could push out every real dump.
- **Fix**: Match the exact formats: daily `^szczur-radar-[0-9]{8}T[0-9]{6}Z\.dump$`, monthly `^szczur-radar-[0-9]{4}-[0-9]{2}\.dump$`. Never delete this run's own `$name.dump` or `$monthly.dump`. Log files that do not match instead of counting them. Add a `test.sh` case with a stray name.
- **Decision**: FIXED — strict names in backup.sh (per directory) and nas-retention.sh; this run's dump is never deleted; other names logged as "ignoring"; the NAS also skips future-dated dumps; test.sh cases for both.

### F3 — CI never runs `tools/db-backup/test.sh`, though the scripts hard-code the schema

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: .github/workflows/ci.yml (no job); tools/db-backup/restore-check.sh:12; tools/db-backup/backup.sh:21
- **Detail**: Two things in the scripts depend on the schema:
  - `REQUIRED_TABLES` and `COUNTED_TABLES` name the tables;
  - the count bracketing assumes those tables have no delete path.

  A migration that renames or drops one of these tables, or adds a delete path, passes CI. It breaks the production backup only at the next 02:00 run, which then fails loudly. `db-backup` also deploys without Wait for CI.
- **Fix A ⭐ Recommended**: Add a CI job that runs `tools/db-backup/test.sh`. It already applies the repo migrations and needs only Docker.
  - Strength: a schema change that breaks the backup turns the PR red, before the merge.
  - Tradeoff: about 1–2 more minutes per CI run. Not path-filtered, per the repo rule that Wait for CI needs the whole workflow.
  - Confidence: HIGH — the test passes locally on Docker, and GitHub runners have Docker (the Testcontainers ITs prove it).
  - Blind spot: the `postgres-ssl:18` pull on the runner is untested; it adds a little more time.
- **Fix B**: Only document the dependency in `szczur-radar-api/README.md` (Migrations) and `tools/db-backup/README.md`.
  - Strength: no CI time.
  - Tradeoff: depends on someone remembering; the failure surfaces only at 02:00 on production.
  - Confidence: MEDIUM — the documentation helps only if it is read.
  - Blind spot: none significant.
- **Decision**: FIXED via Fix A — CI job `db-backup-test` runs `tools/db-backup/test.sh`.

### F4 — PR envs without a database send a failure e-mail every night

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Architecture
- **Location**: .railway/railway.ts:169-198; tools/db-backup/backup.sh:104-110
- **Detail**: Every PR env gets the cron. A PR env of a PR that changes neither the API nor the web has no database (seen in `szczur-radar-pr-44`), so its `db-backup` fails at every scheduled run and Railway e-mails each failure. With a few such PRs open, the only alert channel for production backups fills with noise.
- **Fix A ⭐ Recommended**: In `backup.sh`, when `RAILWAY_ENVIRONMENT_NAME` is not `production` and the database never answers, log `skipped: no database in <env>` and exit 0. Production keeps failing loudly.
  - Strength: small and local. PR envs with a database still verify the backup end to end, as in F-01.
  - Tradeoff: a real failure in a PR env without a database is hidden, but such an environment has nothing to back up.
  - Confidence: HIGH — `RAILWAY_ENVIRONMENT_NAME` is a Railway-provided variable in every service.
  - Blind spot: the variable's exact value in production (`production`) is assumed from the environment list.
- **Fix B**: Declare the cron only for production in IaC (`cronSchedule` only when `branch === "main"`).
  - Strength: PR envs never run the backup.
  - Tradeoff: loses PR-env verification of backup changes, and a service without a schedule runs once per deploy (semantics differ). The IaC context does not reliably carry the environment (`railway.ts` comment).
  - Confidence: LOW — the behaviour of an unscheduled `restartPolicyType: NEVER` service in PR envs is unverified.
  - Blind spot: whether the plan then shows a `cronSchedule` diff between environments.
- **Decision**: FIXED (user: backups only in production) — backup.sh skips (exit 0, "skipped") when `RAILWAY_ENVIRONMENT_NAME` is set and not `production`, unless `BACKUP_IN_ANY_ENVIRONMENT=true`; IaC unchanged (removing the service from PR envs would be a delete the CI gate refuses); test.sh case; docs updated.

### F5 — The restore runbook's DROP SCHEMA path is not atomic, and the safety dump is optional

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: context/deployment/deploy-plan.md (F-01, "Runbook — restore the database from a dump", steps 3–5)
- **Detail**: Two weaknesses:
  - The newer-schema path runs `DROP SCHEMA public CASCADE; CREATE SCHEMA public;` as its own statement, outside `pg_restore --single-transaction`. If the restore then fails, the database is left empty, yet step 3 (a dump before the restore) is "optional".
  - The runbook uses the host's `pg_dump`/`pg_restore`. An older client fails on PostgreSQL 18 archives in the middle of an incident.
- **Fix**: Make step 3 mandatory. Replace the separate DROP with one transaction:

  ```
  pg_restore -f - <dump> | psql -1 -v ON_ERROR_STOP=1 -c 'DROP SCHEMA public CASCADE; CREATE SCHEMA public;' -f -
  ```

  Run the clients from the `szczur-radar-db-backup` image (`docker run --rm -i … -h host.docker.internal -p 15432`).
- **Decision**: FIXED — restore runbook: the pre-restore dump (now step 4) is mandatory; the newer-schema path runs `pg_restore -f - | psql -1 … -c 'DROP SCHEMA …' -f -` in one transaction; every client runs from the `szczur-radar-db-backup` image (PostgreSQL 18), `PGPASSWORD` from the shell.

### F6 — restore-check: bucket keys reach the throwaway server; small secret-hygiene gaps

- **Severity**: 💬 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: tools/db-backup/restore-check.sh:70-78, 119-125; tools/db-backup/backup.sh:156
- **Detail**: Three gaps:
  - The throwaway server inherits `backup.sh`'s environment, including the S3 keys. A dump from a compromised database could run code during the restore as superuser (e.g. `COPY … TO PROGRAM`) and use those keys against the backups.
  - `--serve` passes `RESTORE_CHECK_PASSWORD` on psql's argv.
  - The comment "Later lines win" is wrong for `pg_hba.conf`, which uses the first matching line. The loopback `trust` lines from `initdb` stay first. That is safe with Docker port publishing, but not with `--network host`.
- **Fix**: Have `backup.sh` call `env -u RCLONE_S3_SECRET_ACCESS_KEY -u S3_SECRET_ACCESS_KEY -u RCLONE_S3_ACCESS_KEY_ID -u S3_ACCESS_KEY_ID restore-check.sh …`. Set the password with `\getenv` in the stdin script. In `--serve`, rewrite `pg_hba.conf` (`local all all trust` plus `host all all all scram-sha-256`) and fix the comment.
- **Decision**: FIXED — backup.sh runs restore-check.sh with the S3 keys removed from the environment (`env -u …`); `--serve` sets the password with psql `\getenv`; `pg_hba.conf` rewritten (socket trust, TCP scram); checked by hand: right password → 39 posts, wrong password refused.

### F7 — A few stale sentences outside the plan's list

- **Severity**: 💬 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: context/deployment/deploy-plan.md:128, :479; .railway/railway.ts:21-23, :52-54
- **Detail**: Four places still describe the state before F-01:
  - `deploy-plan.md:128`: step 0 of the PITR-cleanup runbook still says "Daily schedule + manual backup".
  - `deploy-plan.md:479`: the `railway-config` description says the gate refuses any `source` change, without noting that creates now pass.
  - `railway.ts:52-54`: "refuses ANY source change".
  - `railway.ts:21-23`: `sleepApplication` "on all three", though there are now four services.
- **Fix**: Add a one-line "Update 2026-10-11 (F-01)" note in `deploy-plan.md` and correct both `railway.ts` comments. In the gate note, mention that it relies on `.kind`, and that a create carries no details today.
- **Decision**: FIXED — F-01 notes at deploy-plan.md (PITR cleanup step 0, railway-config gate description with the `.kind` caveat); railway.ts comments ("these three", "source change of an existing service").

### F8 — backup.sh and test.sh robustness nits

- **Severity**: 💬 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: tools/db-backup/backup.sh:75-77, 166-173; tools/db-backup/restore-check.sh:70-77; tools/db-backup/test.sh:46-49
- **Detail**: Small gaps that do not affect a normal run:
  - If the monthly manifest copy fails after the dump copy, that month never gets a manifest.
  - "Missing directory" is detected by matching rclone's error text, not its exit code 3.
  - The restore writes full WAL; `wal_level=minimal` would halve the writes as the database grows.
  - After Ctrl-C, `test.sh` can leak an unnamed backup container together with its network and volume.
- **Fix**: Copy whichever monthly file is missing. Use rclone's exit code 3. Add `wal_level = minimal` and `max_wal_senders = 0`. Name the test containers and remove them in `cleanup`.
- **Decision**: FIXED — monthly pair replaced when half of it is missing; rclone exit code 3 for a missing directory; `wal_level = minimal`, `max_wal_senders = 0` in the throwaway cluster; test.sh names its containers `$id-…` and removes them in cleanup.

### F9 — OpenRouter tests: two uncovered cases and duplicated helpers

- **Severity**: 💬 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: szczur-radar-api/src/test/java/pl/szczurradar/api/llm/openrouter/OpenRouterClientTest.java:221-412; szczur-radar-api/src/main/java/pl/szczurradar/api/llm/openrouter/OpenRouterClient.java:247
- **Detail**: The classification is correct on both paths. Two cases are not tested:
  - a 429 with `limit_source=openrouter_key_limit` must stay `RATE_LIMITED`;
  - a budget 403 in `choices[0].error`.

  The new `forbidden`/`inBody` helpers duplicate the existing `inBodyError(...)`. `statusFailure` repeats `TransportException.ofStatus`'s `"HTTP <status>: "` format by hand.
- **Fix**: Add the two tests and reuse a single in-body helper. Optionally, add a `TransportException.ofStatus(status, kind, …)` overload so the format lives in one place.
- **Decision**: FIXED — tests: a 429 naming `openrouter_key_limit` is still retried; a spent key limit in `choices[0].error` is the configuration (break-check: without `status == 403` the 429 test goes red); one `errorBody(...)` helper behind `inBodyError`/`forbidden`; `TransportException.ofStatus(status, kind, …)` overload used by `statusFailure`.

## Triage (2026-10-12)

All nine findings were fixed on the branch. Checks afterwards:
- `tools/db-backup/test.sh`: 11 × PASS, including the new cases (other names, a time limit, outside production);
- `./mvnw -B verify`: 508 unit and 95 integration tests;
- break-check on the 403 status guard;
- `restore-check.sh --serve` checked by hand.

F4 follows the user's decision: backups run in production only. Removing the service from PR envs in IaC would be a delete, which the CI gate refuses, so `backup.sh` skips other environments.
