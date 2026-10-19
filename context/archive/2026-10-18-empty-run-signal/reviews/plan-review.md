<!-- PLAN-REVIEW-REPORT -->
# Plan Review: Pusty przebieg daje sygnał (S-08)

- **Plan**: context/changes/empty-run-signal/plan.md
- **Mode**: Deep
- **Date**: 2026-10-18
- **Verdict**: SOUND
- **Findings**: 0 critical, 2 warnings, 3 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| End-State Alignment | PASS |
| Lean Execution | PASS |
| Architectural Fitness | PASS |
| Blind Spots | PASS |
| Plan Completeness | WARNING |

## Grounding

19/19 paths ✓ (1 new file expected: `pipeline/NoPostsFetchedException.java`), 6/6 symbols ✓, brief↔plan ✓.

Verified against the code:
- The run is still `running` when `count` is called after the failed step (`RunRecorder.java:101-119,146-160`).
- The error text becomes `NoPostsFetchedException: …`: Embabel 1.5.2 rethrows the original exception, unwrapped at `PipelineRunService.java:319-322`.
- `InternalPipelineRunIT` and `PipelineRunControllerIT` wait for a terminal status, not for a step.
- The busy tests hold the run with a gate inside fetch.

## Findings

### F1 — A two-source SourceIngestTest case cannot be written

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 1 §7, Testing Strategy
- **Detail**: `Source` has only `WYKOP` (`sources/Source.java:11`), and `SourceIngest` keys its fetchers by `Source` (`SourceIngest.java:29`). The `fetcher(boolean)` helper returns a fixed `Fetched(1, 3, 2)`. In `PipelineAgentIT` a new case needs a flag like `wykopDown` and `runs.getLast()`.
- **Fix**: Drop the two-source case. The helper takes a per-test `Fetched`, and `PipelineAgentIT` gets `wykopEmpty`/`wykopUnmappable` flags.
- **Decision**: FIXED (fixed differently). The user wants several sources in the future. The rule stays per source, and the requirement that the second source adds the "one empty source, the other with posts" test goes into `sources/CLAUDE.md` (Phase 1 §9), together with a note on isolation (Parked).

### F2 — The SPA tests that break are not named

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 3 §4
- **Detail**: The default `run()` in `AdminView.test.ts` has `skipped: 2` (`:51`), which breaks `:291`, `:292` and `:492`. The literal objects in `runs.test.ts` (`:72, 76-78, 95-96, 102-103`) fail typecheck, as do `notification()` in `KitchenSinkView.vue:81-91` and `NotificationBell.test.ts:33-43`. `mock-api.js` is not typechecked.
- **Fix**: Name them in Phase 3 §4.
- **Decision**: FIXED

### F3 — An admin range with no posts is also failed

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Desired End State, Phase 4 §1–2
- **Detail**: The form allows `from=to=today` (`PipelineRunController.java:145`). Just after midnight such a range can legitimately have 0 posts, which ends `failed` and notifies every admin.
- **Fix**: One sentence in the PRD clarification and in the S-08 runbook in deploy-plan.
- **Decision**: FIXED

### F4 — The new failure path without FIRE_ONCE and freshness assertions

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Phase 1 §7, case (a)
- **Detail**: The lesson "Akcje Embabel… FIRE_ONCE" requires a test that an action which throws ran once. `FreshnessIT` tests freshness only on rows inserted by SQL.
- **Fix**: In case (a), assert one round of Wykop searches and an unchanged `findLastUpdate()`.
- **Decision**: FIXED

### F5 — The first logger in pipeline/step

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Architectural Fitness
- **Location**: Phase 1 §2
- **Detail**: No class in `step/` or `flow/` logs. Per-source counts exist only in the `SourceIngest.fetch` loop. The empty fetch is already logged by Embabel and `execute`.
- **Fix**: Keep the WARN in `SourceIngest`, write the reason in the plan, and log only when `skipped > 0`.
- **Decision**: FIXED
