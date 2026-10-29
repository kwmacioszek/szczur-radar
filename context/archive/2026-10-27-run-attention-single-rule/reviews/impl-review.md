<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: S-22: „wymaga uwagi” liczone jedną regułą

- **Plan**: context/changes/run-attention-single-rule/plan.md
- **Scope**: Full plan
- **Reviewed phases**: 1, 2
- **Date**: 2026-10-28
- **Verdict**: APPROVED
- **Findings**: 0 critical, 0 warnings, 2 observations

Commits reviewed: `45db4d3` (p1), `5d3297a` (p2), `36acd85` (epilogue), base `e5ab295`. Every planned file is in the diff. Outside the plan, only planning artifacts changed: the change folder, plus `roadmap.md`, `render_roadmap_svg.py` and `roadmap.svg`, which only carry the S-22 status. Nothing on the "What We're NOT Doing" list was touched: `notifications/`, `NotificationMenu`, `deploy-plan.md`, `context/domain/` and the audit are all unchanged.

Behaviour equivalence:
- **API:** the new code table gives a non-empty list in exactly the cases where the old boolean rule gave `true`, for every status and counter combination.
- **SPA:** the texts equal the old ones for the same runs.
- **Visible change:** the only one is the tone of a deploy interruption, `neutral` → `warning`, for both the run and its step.

Automated criteria were re-run during this review, and all passed:

| Criterion | Result |
| --- | --- |
| 1.1 `PipelineRunTest` | exit 0 |
| 1.2 `./mvnw -B verify` | 843 unit + 270 IT, BUILD SUCCESS |
| 1.3 `rg` | no match |
| 2.1 `npm test` | 418 passed |
| 2.2 `npm run build` | exit 0 |
| 2.3 `rg` | no match |

Manual checks 2.4 (mock screenshots) and 2.5 (PR env) are unchecked and pending, which is correct: they need a human and a PR env.

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | PASS |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | PASS |

## Findings

### F1 — The tone and status label still read the skipped counter

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Architecture
- **Location**: szczur-radar-web/src/runs.ts:31-33, 52-64
- **Detail**: Two functions still derive "skipped elements" from the counters (`skipped > 0`), not from the API's codes:
  - `runTone`: a `succeeded` run with `skipped > 0` gets `warning`;
  - `runStatusLabel` / `hasSkipped`: the "pominięte: N" suffix.

  The plan left tone and status text in the SPA on purpose. The bell's payload (`NotifiedRun`) also carries no codes, and changing it was out of scope. The reason texts no longer copy the rule, but the tone and label still repeat the "skipped" part of it. This is harmless today: the tone is presentation, and it agrees with the rule.
- **Fix**: No code change. Write the remaining copy down in the S-22 issue as a known follow-up candidate, e.g. for S-24 (`api-contract-guard`) or a later `NotifiedRun.attentionReasons`.
- **Decision**: FIXED — queued as a follow-up in `follow-ups/review-fixes.md` (no code change, by design); issue S-22 comment when the PR opens

### F2 — The compiler does not catch a new code without a text

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: szczur-radar-web/src/runs.ts:92-94
- **Detail**: In `attentionReasonText`, the `default` branch receives `code` typed as `never`. Adding a member to the TS `AttentionReason` union without a `case` therefore still compiles, and that code would quietly show as the raw code. The unknown-code fallback (`String(code)`) is correct at runtime; the gap is only an exhaustiveness check at build time.
- **Fix**: In `default`, assign the code to a `never`-typed local before returning it (e.g. `const unknown: never = code; return String(unknown)`). The build then fails when a union member has no text, and a code arriving at runtime still shows as it came.
- **Decision**: FIXED — exhaustiveness check in the `default` branch of `attentionReasonText` (`const unknown: never = code`); `npm test` 418 passed, `npm run build` passes, and a union code without a text fails the build (TS2322)
