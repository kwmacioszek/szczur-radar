<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Pusty przebieg daje sygnał (S-08)

- **Plan**: context/changes/empty-run-signal/plan.md
- **Scope**: Full plan
- **Reviewed phases**: 1, 2, 3, 4
- **Date**: 2026-10-19
- **Verdict**: NEEDS ATTENTION
- **Findings**: 0 critical, 3 warnings, 3 observations

Diff reviewed: `git diff 2abc64c...HEAD` (merge-base with `main` after the S-14 merge; branch head `55b66a5`). Every planned file is in the diff; nothing is MISSING. Extras: a `needsAttention` doc comment in `szczur-radar-web/src/api/pipeline.ts`, extra SPA tests, extra log assertions in `PipelineAgentIT` (c), and the S-08 roadmap/svg status flip (repo rule) — all harmless. "What We're NOT Doing" respected: no new status or migration, cron exit codes unchanged, no `needsAttention` in `/internal`, no threshold, freshness query untouched, `SourceFetcher.Fetched` unchanged.

Automated criteria (local, after merging S-14): `./mvnw -B verify` 621 + 217 BUILD SUCCESS; `tools/pipeline-trigger/test.sh` all checks passed; `tools/deploy-gate/test.sh` all checks passed; `npm test` 398/398; `npm run build` OK. 4.2 (CI on PR #80) pending: runs for `23d7262` and `55b66a5` queued (GitHub Actions outage). Manual rows 1.5, 2.2, 3.3, 3.4, 4.3, 4.4, 4.5 unchecked (pending, none falsely ticked).

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | WARNING |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | WARNING |
| Success Criteria | WARNING |

## Findings

### F1 — PRD NFR note contradicts FR-030 note and code on freshness

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: context/foundation/prd.md:262
- **Detail**: The S-08 note under the NFR says skipped elements next to good posts leave "status przebiegu, kod wyjścia crona i data świeżości" unchanged. The S-08 note under FR-030 (`prd.md:226`) says such a run "datę przesuwa", and so does the code (the freshness query counts `succeeded`/`partial`) and the plan's Desired End State ("świeżość się przesuwa"). The PRD is the source of truth (hard rule), so it must not contradict itself.
- **Fix**: Reword `prd.md:262` to "status przebiegu i kod wyjścia crona się nie zmieniają, a data świeżości przesuwa się jak dotąd".
- **Decision**: FIXED (Fix now)

### F2 — Branch behind main; CI on PR #80 not yet green

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Success Criteria
- **Location**: N/A (branch `feature/empty-run-signal` vs `origin/main`)
- **Detail**: Progress 4.2 is open: CI runs for `23d7262` and `55b66a5` are queued. Meanwhile `origin/main` gained `llm-config-fail-fast` and `backup-check-completeness`; `git merge-tree` shows conflicts in `context/deployment/deploy-plan.md` and `tools/render_roadmap_svg.py`, and `PipelineAgentIT.java` / `pipeline/CLAUDE.md` auto-merge, so the merged code has not been tested.
- **Fix**: Merge `origin/main` again (keep both sides' sections; recompute the roadmap legend), rerun `./mvnw -B verify`, `tools/pipeline-trigger/test.sh`, `tools/deploy-gate/test.sh`, `npm test && npm run build`, push, and tick 4.2 when CI is green.
- **Decision**: PENDING

### F3 — A failing counter write hides the empty-source error

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/flow/PipelineAgent.java:89-90
- **Detail**: In the `NoPostsFetchedException` catch, if `recorder.count(...)` throws (e.g. a lost DB connection), that exception replaces the original, and the run's error reads e.g. `CannotGetJdbcConnectionException: …` instead of the empty-source reason. `pipeline/CLAUDE.md` already treats a failed `count` in the dispatcher as a WARN that leaves the outcome. (A run interrupted in between is harmless: `count` only writes a running run.)
- **Fix**: Wrap the `count` in try/catch; on failure `ex.addSuppressed(countFailure)` and log a WARN, then rethrow the original `NoPostsFetchedException`.
- **Decision**: FIXED (Fix now)

### F4 — NoPostsFetchedException sits in the root package

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/NoPostsFetchedException.java
- **Detail**: `pipeline/CLAUDE.md:180` says the root package holds only what other packages call. The other root exceptions are there because `PipelineRunExceptionHandler` maps them to HTTP answers; this one is thrown by `step/SourceIngest` and caught by `flow/PipelineAgent`, like `state/RunNoLongerRunningException`. The plan chose the root package deliberately (next to `SourceNotConfiguredException`), so this is a plan-vs-rule tension, not a slip.
- **Fix**: Move it to `pipeline/step/` (the simple name, and so the run's error text, stays the same) and update `pipeline/CLAUDE.md`.
- **Decision**: FIXED (Fix now)

### F5 — Web test fixture: step error carries the class prefix

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: szczur-radar-web/src/views/RunDetailView.test.ts:291
- **Detail**: The test uses `NoPostsFetchedException: wykop gave no post to store (…)` for the fetch step's error too. The real step error is only the message (`RunRecorder.step` stores `ex.getMessage()`); the class prefix belongs to the run's `error`. `dev/mock-api.js` (r-empty) gets it right. The test passes either way.
- **Fix**: Use the message without the class name for the step's `error` in that fixture.
- **Decision**: FIXED (Fix now)

### F6 — Every empty night logs an ERROR with a stack trace

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/PipelineRunService.java:236
- **Detail**: An empty range (an expected business outcome) is logged like any real failure: `log.error("Pipeline run {} failed", runId, ex)` with a full stack trace, plus Embabel's WARN. The plan accepts this (Phase 1 §2). Worth remembering when log-based alerting arrives (F-04).
- **Fix**: No change now; note it for F-04 (e.g. log a `NoPostsFetchedException` without its stack trace).
- **Decision**: ACCEPTED — no code change; note for F-04 (out-of-app alert channel): an empty range logs `ERROR Pipeline run <id> failed` with a stack trace, so a log-based alert must not page on it as a crash (or the log should drop the stack for `NoPostsFetchedException`).
