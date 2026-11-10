<!-- PLAN-REVIEW-REPORT -->
# Plan Review: Admin przywraca martwe posty (S-33)

- **Plan**: context/changes/restore-dead-posts/plan.md
- **Mode**: Deep
- **Date**: 2026-11-08
- **Verdict**: REVISE → SOUND after fixes
- **Findings**: 0 critical, 3 warnings, 1 observation

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| End-State Alignment | PASS |
| Lean Execution | PASS |
| Architectural Fitness | PASS |
| Blind Spots | WARNING |
| Plan Completeness | WARNING |

## Grounding
12/12 paths ✓, symbols ✓ (2 mismatches → F2, F3), brief↔plan ✓, Progress↔Phase ✓ (16/16)

## Findings

### F1 — "No longer post_dead" checked in the wrong IT; end to end only in the PR env

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Blind Spots
- **Location**: Phase 1 — §4 Tests of the route
- **Detail**: `PostsApiIT` is `@Transactional` (`:56,61`) and has neither a stubbed `LlmClient` nor a local Wykop. `POST /api/admin/pipeline-runs` there returns 202, but the run is never committed or executed: the queue thread does not see the row, a stray task is left behind, and the advisory lock is held until rollback. The 202 proves only that the dead check passed. Re-assessment was proven only in the PR env.
- **Fix**: In `PostsApiIT`, check "not dead" with `findIdsInStage(…, DEAD)`. Add one scenario to `PipelineRunControllerIT` (template `retryingADeadPostIsRefusedAndARetryableOneIsQueued`): dead → `post_dead` → restore → posts run → `awaitIdle` → `assessed` with one more assessment row.
- **Decision**: FIXED (Fix in plan)

### F2 — Roadmap renderer does not know the "planning" status

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 1 — §6 Roadmap in the renderer
- **Detail**: `STATUS` in `tools/render_roadmap_svg.py:29-36` has no `planning` / `in-progress`; "a status matching roadmap.md" would break generation.
- **Fix**: S-33 in the renderer gets `"progress"`.
- **Decision**: FIXED (Fix in plan)

### F3 — Existing retry tests and docs quote the button label exactly

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 2 — §4 Tests
- **Detail**: `AdminView.test.ts:355, :381, :409` find `'Ponów zaznaczone'` by exact text, and the "(N)" breaks them; the plan claimed they would still pass. The label is also quoted in `szczur-radar-api/README.md:133` and `pipeline/CLAUDE.md:364`.
- **Fix**: List the three tests and the two docs in the plan.
- **Decision**: FIXED (Fix in plan)

### F4 — Unspecified method of running SQL in the PR env

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 3 — §2 and criteria 3.2/3.3
- **Detail**: The `railway connect` tunnel drops (`deploy-plan.md:1081, :1593`); `railway ssh -s postgres -e szczur-radar-pr-<N> -- psql -U postgres -d railway -c "…"` works.
- **Fix**: Name that method in the plan.
- **Decision**: FIXED (Fix in plan)
