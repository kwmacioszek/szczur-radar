<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Historia przebiegów, postęp i powiadomienia admina (S-06)

- **Plan**: context/changes/pipeline-run-history-dashboard/plan.md
- **Scope**: Full plan
- **Reviewed phases**: 1, 2, 3, 4, 5, 6
- **Date**: 2026-10-17
- **Verdict**: APPROVED
- **Findings**: 0 critical, 2 warnings, 6 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | PASS |

Automated (re-run 2026-10-17 on `f81393f`): `./mvnw -B verify` 611 unit + 203 IT green; `npm test` 373 green; `npm run build` clean; `tools/pipeline-trigger/test.sh` all checks passed; CI on `fa87f19` green (7 jobs). Manual: every Progress row ticked with evidence (local runs in the phase reports, PR env in `deploy-plan.md` S-06 and `ui-after/README.md`).

Plan drift: every "Changes Required" item MATCH; known adaptations hold; nothing from "What We're NOT Doing" built. Extras are small UI states and helpers (`runs.ts`, `labels.ts`), extra INFO lines, and the phase-6 gate fix (`PipelineAgent.summarize` closes its step before the run's final status).

## Findings

### F1 — A stalled run's zombie can hold threads for good

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/PipelineRunService.java:266-269; pipeline/assess/ConcurrentAssessmentDispatcher.java:216,238
- **Detail**: `stopStalled` cancels only the agent's future. The zombie action stays in `await()` (no deadline) on an Embabel thread, and its hung post calls keep threads of the shared `pipeline-llm-N` pool. The plan accepted a zombie bounded by `LLM_TIMEOUT`; an unbounded hang (JDBC has no timeout; the cause of the 22 h run is unknown) leaks those threads until a restart, and after about `PIPELINE_LLM_CONCURRENCY` such stalls every later run stalls too.
- **Fix A ⭐ Recommended**: Document the limit (next to "temporary" in `pipeline/CLAUDE.md` and the S-06 section of `deploy-plan.md`) and log the dispatcher pool's busy threads in the stall WARN; bound DB calls in a follow-up change.
  - Strength: tiny, honest, matches the plan's accepted risk while Embabel removal is parked; the WARN shows when a restart is needed.
  - Tradeoff: the leak stays possible until a restart.
  - Confidence: MED — no in-process hang has been confirmed in production.
  - Blind spot: the real cause of the 2026-10-09 run.
- **Fix B**: Bound every blocking call: cancel the run's post futures with `cancel(true)` from `stopStalled` and set a pgjdbc `socketTimeout` for the datasource.
  - Strength: removes the leak class.
  - Tradeoff: a global DB timeout changes every query (and `migrate`); more dispatcher code in S-06.
  - Confidence: MED — OkHttp honours interrupts, a blocked JDBC read does not without `socketTimeout`.
  - Blind spot: longest legitimate query time.
- **Decision**: FIXED (Fix B) — `AssessmentDispatcher.cancel(runId)` interrupts the run's post futures from `stopStalled`; pgjdbc `socketTimeout=300` bounds DB calls; tests in `ConcurrentAssessmentDispatcherTest`, `RunWatchdogIT`

### F2 — The bell keeps the API awake and refetches on every navigation

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-web/src/components/NotificationBell.vue:85-88; szczur-radar-web/src/components/AppHeader.vue:39
- **Detail**: Every 60 s while an admin's tab is visible the bell hits the API and Postgres on every page, so the API never sleeps (Hobby, $10 hard limit) — accepted in plan.md Performance. New: `AppHeader` is rendered by each view, so the bell remounts on every navigation, refetches and flashes its loading state.
- **Fix A ⭐ Recommended**: Keep the bell's state in a module-level store (no refetch per navigation), refresh on `visibilitychange` and on navigation, and poll every 60 s only on `/admin*` routes.
  - Strength: fixes the flash and the extra requests; the feed no longer keeps the API awake.
  - Tradeoff: on the feed the count updates only on navigation or tab focus.
  - Confidence: HIGH — small SPA change with existing test patterns.
  - Blind spot: none significant.
- **Fix B**: Only the module-level store (keep 60 s polling everywhere).
  - Strength: smallest change, removes the flash.
  - Tradeoff: an open feed tab still keeps the API awake.
  - Confidence: HIGH.
  - Blind spot: none significant.
- **Decision**: FIXED (Fix A) — `src/notificationsStore.ts`: shared state, refresh on navigation and tab focus (skipped when < 10 s old), 60 s polling only on `/admin*`, reset on logout

### F3 — Stop and release are not atomic

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/PipelineRunService.java:260-263
- **Detail**: `stall` commits, then `releaseAll` runs separately; a `claim` that read the run as running just before can commit after `releaseAll`, leaving a post `assessing` under a `failed` run until the next start-up (`releaseOrphans`). Same pattern in `onClose`.
- **Fix**: Run `releaseAll` inside `stall`'s (and `interrupt`'s) transaction, as recovery pairs `interruptAll` with `releaseOrphans`.
- **Decision**: FIXED — stall/interrupt and `releaseAll` in one transaction; the race is narrowed, not closed (a claim in flight at the stop's last round trip is put back by the task's own release or `releaseOrphans`; `FOR SHARE` in `claim` left out)

### F4 — A stored post can be counted as a technical error

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/assess/ConcurrentAssessmentDispatcher.java:129-133,223-229
- **Detail**: If `recorder.count` throws after `record` stored the assessment, `await` turns the post into `Outcome.Error`: the run becomes `partial` with a processing error and the admins get a false notification; a second throw there escapes `dispatch`.
- **Fix**: Catch around `count` inside the task (WARN, keep the real outcome); keep the `await` catch for tasks the pool never ran.
- **Decision**: FIXED — a failed `count` is a WARN and keeps the post's outcome; test `aFailedCountLeavesAStoredPostStored`

### F5 — Spurious ERRORs when a deploy stops a run

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/PipelineRunService.java:234-243,309-316
- **Detail**: `onClose` calls `shutdownNow()` without waiting; the run thread's `finally` (`close`, `releaseAll`, `runEnded`) then races the DataSource shutdown and can log "could not be closed" / "admins could not be notified" on each deploy during a run.
- **Fix**: When `execute` was interrupted, skip `close` and `runEnded` in `finally` (`onClose` already did both).
- **Decision**: FIXED — `execute` skips `close`/`runEnded` after an `InterruptedException`; `PipelineAgentIT` asserts no end line

### F6 — Tight timings in RunWatchdogIT

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Success Criteria
- **Location**: szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/RunWatchdogIT.java
- **Detail**: With `PIPELINE_STALL_TIMEOUT=2s` the setup run must never pause 2 s between activity marks, and the "slow but progressing" case has 1 s of margin; a slow CI runner could flake it.
- **Fix**: Use a 4–5 s timeout and scale the scripted pauses to match.
- **Decision**: FIXED — `PIPELINE_STALL_TIMEOUT=5s`, `PIPELINE_LLM_CONCURRENCY=1`, slow run answers every 1.5 s

### F7 — An Error stops the watchdog silently

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/RunWatchdog.java:87-96
- **Detail**: `check()` catches only `RuntimeException`; an `Error` escaping it makes `scheduleWithFixedDelay` drop every later check without a log line.
- **Fix**: Catch `Throwable` around the check and log it at ERROR.
- **Decision**: FIXED — `RunWatchdog.check` catches `Throwable`; tests in `RunWatchdogTest`

### F8 — Two doc gaps in the S-06 deploy notes

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: context/deployment/deploy-plan.md:726
- **Detail**: (a) The sentence says V8's data fix closes running steps "in runs that already ended as `interrupted`"; the SQL closes them in every run that is not queued/running. (b) Not documented: a run started before the merge has `deployment_id` NULL, so the merge's own deploy labels its interruption `restart`, not `deploy`. (Also future: `uq_notifications_recipient_kind_run` treats NULL `run_id` as distinct — a kind without a run would need `NULLS NOT DISTINCT`.)
- **Fix**: Reword (a), add (b) to the runbook's "Migrations and start" step, and note the NULL uniqueness in `pipeline/CLAUDE.md` or the V9 comment.
- **Decision**: FIXED — deploy-plan S-06 V8 sentence and runbook step 2 (`restart` on the merge's own deploy), socket timeout line; NULL-distinct note in `pipeline/CLAUDE.md`
