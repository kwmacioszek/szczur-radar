<!-- PLAN-REVIEW-REPORT -->
# Plan Review: Strażnicy zapisu i końca przebiegu liczą zbiory z kodu (S-39)

- **Plan**: context/changes/derived-persistence-guards/plan.md
- **Mode**: Deep (claims checked inline, no sub-agent)
- **Date**: 2026-11-06
- **Verdict**: REVISE → SOUND after fixes
- **Findings**: 0 critical, 3 warnings, 4 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| End-State Alignment | WARNING |
| Lean Execution | PASS |
| Architectural Fitness | PASS |
| Blind Spots | WARNING |
| Plan Completeness | WARNING |

## Grounding
9/9 paths ✓, 8/8 symbols ✓, Progress↔Phase 3/3/4 ✓, brief↔plan ✗ (F6)

## Findings

### F1 — Parser fixtures cover 4 of the 8 compound CHECKs

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 2, change 2 and change 3
- **Detail**: NOT_A_VALUE_LIST has 13 entries; 8 are compound CHECKs with an enum literal (interruption_status, requested_posts, assessing_owner, run_sources_error, notifications_run `kind <> 'run_attention'`, pipeline_runs_range, posts_stage_attempts, assessments_outcome), 5 are patterns/open sets. The plan fixes the parser on 4 definitions and says "rules without an enum literal" stay as exceptions, but none of the 13 is such a rule. The `<>` form and column-vs-column comparisons are the shapes most likely to break the parser.
- **Fix**: Name all 8 as parser fixtures and cut the exception text to the 5 pattern/open-set CHECKs.
- **Decision**: FIXED (Fix in plan)

### F2 — `pipeline_runs.sources` stays an X1-style hole

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: End-State Alignment
- **Location**: Phase 1, change 1; Critical Implementation Details
- **Detail**: The plan names `pipeline_runs.sources` an exception and compares `Source::value` with `SourceConverter`; a writer changed to `Source::name` would stay green. The writer is `PipelineRun`'s constructor (`PipelineRun.java:142`), reachable through the public factory `PipelineRun.range(...)` (`:153`).
- **Fix**: Read the written values from `PipelineRun.range(...)` instead of naming an exception.
- **Decision**: FIXED (Fix in plan)

### F3 — The plan assumes RunRecorder is the only status writer

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Blind Spots
- **Location**: Phase 3; What We're NOT Doing
- **Detail**: Every `UPDATE pipeline_runs SET status` is in RunRecorder today and the entity has no status setter, but the plan states the assumption nowhere; a new class with its own UPDATE would be an ending path the guard cannot see.
- **Fix A ⭐ Recommended**: Record the assumption in "What We're NOT Doing" and Open Risks, no mechanism.
  - Strength: Matches the lesson "justify scope before adding a mechanism"; costs nothing.
  - Tradeoff: A second writer is still caught only by review.
  - Confidence: HIGH — grep shows one writer today and the audit says the same.
  - Blind spot: A future writer through a JPA query is not covered by grep.
- **Fix B**: Add a check to RunEndingTest that `UPDATE pipeline_runs SET status` appears only in RunRecorder.java.
  - Strength: Closes the hole mechanically.
  - Tradeoff: A source scan of src/main, which phase 3 rejected.
  - Confidence: MED — the string match breaks if the SQL is split or reformatted.
  - Blind spot: Writers through JPA @Modifying queries.
- **Decision**: FIXED (Fix A)

### F4 — R-23 guard proves the named tests exist, not that they check runEnded

- **Severity**: 🔍 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: End-State Alignment
- **Location**: Phase 3, change 2
- **Detail**: Deleting `verify(notifications).runEnded(run)` from a named test keeps the guard green; only the one-off break run (3.3) shows the link works.
- **Fix**: Add a line to Open Risks naming the limit.
- **Decision**: FIXED (Fix in plan)

### F5 — The documentation change in Phase 3 has no success criterion

- **Severity**: 🔍 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 3, change 4
- **Detail**: The `pipeline/CLAUDE.md` and `ClosedSetsIT` javadoc edits have no Automated row, so Progress could reach "done" without them.
- **Fix**: Add `grep -n RunEndingTest szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/CLAUDE.md` as Automated 3.5, in the phase block and Progress.
- **Decision**: FIXED (Fix in plan)

### F6 — Brief says 3–4 new test files, the plan creates 5

- **Severity**: 🔍 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: plan-brief.md, "Estimated effort"
- **Detail**: The plan creates ClosedSetsGuard, ClosedSetsGuardTest, RunEndingGuard, RunEndingTest and RunEndingGuardTest.
- **Fix**: Change the brief to "5 new test files".
- **Decision**: FIXED (Fix in plan)

### F7 — Break-run commands for X1 and X2 run the whole unit suite first

- **Severity**: 🔍 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 1 and 2, break runs
- **Detail**: `./mvnw -B verify -Dit.test=ClosedSetsIT` runs every `*Test` first; a red unit test would stop the build before `ClosedSetsIT` and hide which guard fired.
- **Fix**: Add `-Dtest=ClosedSetsGuardTest -Dsurefire.failIfNoSpecifiedTests=false` to both break-run commands.
- **Decision**: FIXED (Fix in plan)
