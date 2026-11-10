<!-- PLAN-REVIEW-REPORT -->
# Plan Review: Bezpiecznik serii odmów dostawcy LLM (S-32)

- **Plan**: `context/changes/provider-refusal-breaker/plan.md`
- **Mode**: Deep
- **Date**: 2026-11-08
- **Verdict**: REVISE → SOUND after triage (all four findings fixed in the plan)
- **Findings**: 0 critical, 3 warnings, 1 observation

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| End-State Alignment | PASS |
| Lean Execution | PASS (1 observation, fixed) |
| Architectural Fitness | PASS |
| Blind Spots | WARNING (1 finding, fixed) |
| Plan Completeness | WARNING (2 findings, fixed) |

## Grounding

12/12 paths ✓, 7/7 symbols ✓ (`OUTAGE_STREAK`/`CLIENT_ERROR_STREAK`, `PostTransitions.record`, `PipelineRunIT.answer`, `dispatchOnOneThread`, `aStoppedStepFailsTheRunAndSaysWhy`, `aRefusedCallEndsTheStreakOfOutages`, `aFailedCallIsFailedWithItsClassAfterOneCall`), brief↔plan ✓, Progress 14/14 ✓.

Codebase verification (one sub-agent), 5/5 risky claims confirmed:
1. `Assessor.failed` (`agents/assessor/Assessor.java:149-160`) is the only builder of a result with `PROVIDER_ERROR` / `TIMEOUT`; `OutputParser` yields only `INVALID_STRUCTURED_OUTPUT` / `SCHEMA_VIOLATION`, `RuleBasedValidator` only the D-rule classes (`validation/RuleId.java:3-5`, `ValidationResult.java:10-11`).
2. `PostTransitions.record` (`pipeline/state/PostTransitions.java:91-110`) updates only `WHERE stage = assessing AND assessing_run_id = :run`; the watchdog, shutdown, start-up recovery and orphan clean-up release the posts in the same transaction as the status change, so a stopped run's result is `NotTaken` — no `cancelled` check needed in the stored branch. Nuance outside scope: `PipelineRunService.close` (`:496-498`) does `fail` and `releaseAll` in two transactions; pre-existing, identical for the two existing counters.
3. Blast radius: methods only — `rules.json:31` `failureClass`, `SpaRulesTest.java:93`, `ClosedSetsIT.java:110`, `FailureClassConverter`, nine positional `new AssessmentResult(` sites in tests and measurement, `FailureClass.values()` loops in measurement — none needs a change. `assess(...)` has one caller (`ConcurrentAssessmentDispatcher.java:207-208`); `Outcome.Stored` consumers unchanged.
4. Test seams: `INVALID` → `response("no JSON here", 2, false)` (a stored contract violation, `rawText != null`, attempts 2); `REFUSED` → plain `LlmCallException(PROVIDER_ERROR, "HTTP 400: refused", 1, …)`; no existing test dispatches two `REFUSED` posts in a row (`:145`, `:707`, `:1091` only).
5. `PipelineRunIT` runs on the pool's default 4 threads (`AssessmentPool.java:26`, no property on the class or in `src/test/resources/config/application.properties`); `check()` on the `@MockitoBean` returns `null` without reaching `answer(...)`'s script (`PipelineRunIT.java:644`, `:1081-1083`).

## Findings

### F1 — Powtórka serii na tych samych postach: trzy czerwone noce

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Blind Spots
- **Location**: Phase 2 — #2 (PRD), #3 (deploy-plan runbook); brief „Open Risks”
- **Detail**: The brief accepts "RETRY next night takes them first and repeats the stop" but the plan did not spell out what the operator sees: a content-specific refusal of three posts stops the nightly RETRY on nights 2 and 3 (attempt 1 → 2 → `dead`), the cron exits 1 each of those nights (`tools/pipeline-trigger/trigger.sh:205`), the other leftovers of that RETRY wait until the three are `dead`; the RANGE run behind it goes on (separate queue run). `findLeftoverIds` has no `ORDER BY`, but in a small RETRY the three posts are taken anyway.
- **Fix**: Add the night 1 → nights 2–3 → night 4 sequence to the S-32 runbook (Phase 2 #3), one sentence to the PRD clarification (#2), and extend the brief's risk with the cron's exit 1 on two further nights.
  - Strength: The operator recognises the pattern without reading code; the mechanism and the owner's decisions are unchanged.
  - Tradeoff: Two more red nights are accepted instead of designing an exception for a repeat on the same posts.
  - Confidence: HIGH — queue order (RETRY before RANGE) and the cron's exit code confirmed in `PipelineRunService` and `trigger.sh`.
  - Blind spot: leftover order is unspecified; irrelevant for a small RETRY.
- **Decision**: FIXED — plan.md Phase 2 #2 and #3, plan-brief.md „Open Risks”

### F2 — Kryterium 1.5 czerwone przed commitem fazy

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 1 — Automated Verification, criterion 5 / Progress 1.5
- **Detail**: "after `python3 tools/render_roadmap_svg.py`, `git status --porcelain docs/visuals/roadmap.svg` prints nothing" is red before the phase's commit (the regenerated SVG differs from HEAD) — red exactly when done right.
- **Fix**: Idempotence on the index: renderer → `git add docs/visuals/roadmap.svg` → renderer again → `git diff --quiet -- docs/visuals/roadmap.svg` exits 0 (Progress title 1.5 unchanged).
- **Decision**: FIXED — plan.md Phase 1 Automated Verification

### F3 — Scenariusz IT zakłada 10 postów ocenionych przez model

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 2 — #1 (PipelineRunIT)
- **Detail**: `assessedDay()` yields 16 `assessed_ok` including prefiltered posts; existing scenarios take at most 4 from `modelAssessedPosts`. Whether ≥ 10 model-assessed posts exist was not verified — the scenario could fail on data, not on the rule.
- **Fix**: N = `modelAssessedPosts(16)` (all available), assert N ≥ 7; stored refusals in `3…6`, untouched posts ≥ N − 6; same wording in Key Discoveries and Testing Strategy.
- **Decision**: FIXED — plan.md Phase 2 #1, Key Discoveries, Testing Strategy

### F4 — Nowy test RunOutcomeTest powtarza istniejący

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Lean Execution
- **Location**: Phase 1 — #3 (Testy fazy 1), Testing Strategy
- **Detail**: `aStoppedStepFailsTheRunAndSaysWhy` already checks that any `stoppedBy` gives FAILED with its description; `RunOutcome` does not read the failure class, so a "three failed + stop" test checks nothing new.
- **Fix**: Drop `RunOutcomeTest` from Phase 1 (dispatcher tests and `AssessorTest` stay).
- **Decision**: FIXED — plan.md Phase 1 #3, Testing Strategy; plan-brief.md Scope

## Triage summary

- Fixed: F1, F2, F3, F4 (4)
- Skipped / Accepted / Dismissed: none
- Verdict after fixes: REVISE → SOUND
