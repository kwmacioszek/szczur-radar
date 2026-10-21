<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: S-19: dokumenty operatora mówią prawdę

- **Plan**: context/changes/ops-docs-debt/plan.md
- **Scope**: Full plan
- **Reviewed phases**: 1, 2, 3
- **Date**: 2026-10-21
- **Verdict**: NEEDS ATTENTION
- **Findings**: 0 critical, 5 warnings, 3 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | WARNING |
| Scope Discipline | PASS |
| Safety & Quality | PASS |
| Architecture | PASS |
| Pattern Consistency | WARNING |
| Success Criteria | PASS |

## Evidence

### Status lines in `deploy-plan.md` vs `gh` (agent, 2026-10-21; for criterion 1.6)

The data comes from `gh pr view <N> --json mergeCommit,mergedAt` and `gh issue view <N> --json state,closedAt`. Every Status line names the same PR, merge SHA and issue state as GitHub.

| Section (line) | PR | Merge SHA | Merged | Issue | Issue state |
|---|---|---|---|---|---|
| api-docker-image (:105) | #33 | `721bb8a` | 2026-10-06 | #32 | closed 2026-10-06 |
| S-02 (:189) | #37 | `1a33fcc` | 2026-10-09 | #14 | closed 2026-10-09 |
| F-01 (:256) | #44 | `b7ee254` | 2026-10-12 | #43 | closed 2026-10-12 |
| S-05 (:462) | #50 | `13e371a` | 2026-10-13 | #18 | closed 2026-10-15 |
| S-07 (:573) | #49 | `54c4fc2` | 2026-10-13 | #20 | closed 2026-10-13 |
| S-03 (:688) | #53 | `4fdd51a` | 2026-10-13 | #16 | closed 2026-10-13 |
| S-04 (:702) | #54 | `c80e846` | 2026-10-14 | #17 | closed 2026-10-18 |
| S-06 (:730) | #60 | `5b6eabb` | 2026-10-17 | #19 | closed 2026-10-18 |
| self-hosted runner (:876) | #83 | `35c47a2` | 2026-10-19 | #30 | closed 2026-10-19 (at the merge) |
| S-14 (:906) | #81 | `2abc64c` | 2026-10-19 | #73 | closed 2026-10-19 (at the merge) |
| S-15 (:1058) | #79 | `1a6d300` | 2026-10-19 | #74 | closed 2026-10-19 (at the merge) |
| S-08 (:1120) | #80 | `a168731` | 2026-10-19 | #67 | closed 2026-10-20 |

### JVM measurement (for criterion 2.6)

The raw output of the read-only `railway ssh` on production (2026-10-21 06:08 UTC) is in issue #78, comment `6322284179`. The table in `deploy-plan.md` (section api-docker-image) repeats those values. The arithmetic was checked: 1999998976 B ≈ 1907 MiB; 1501560832 B ≈ 1432 MiB = 75 % of the limit; 25 % ≈ 477 MiB.

## Success Criteria

Success Criteria: 1.1–1.5, 2.1–2.5, 2.7, 3.1, 3.2, 3.4 and 3.5 were re-run on HEAD `2f570c7` and all pass. All CI jobs on PR #93 are green, and `railway-config` reports 0 changes. The manual rows 1.6, 2.6 and 3.3 were confirmed by the human.

## Findings

### F1 — deploy-plan.md:56 still says the three variables stay declared

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: context/deployment/deploy-plan.md:56; context/foundation/infrastructure.md:15
- **Detail**: The current F-03 variable list says "`DATABASE_URL`, `JWT_SECRET` and `RAILPACK_JDK_VERSION` stay declared in `.railway/railway.ts` only because the CI gate refuses `variable.delete`". This branch removes all three, so this is the kind of stale operator text S-19 is meant to remove. The dated update in `infrastructure.md:15` also says "`RAILPACK_JDK_VERSION` stays declared, unused".
- **Fix**: Replace the sentence with "removed in S-19 (each environment first, then the file; section S-19)". Append "(removed in S-19)" to infrastructure.md:15.
- **Decision**: FIXED

### F2 — New lesson says "Osiem sekcji" but lists seven

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: context/foundation/lessons.md:123
- **Detail**: "Osiem sekcji (S-02, S-04, S-07, self-hosted runner, S-14, S-15, S-08)" lists seven items. The plan had the same miscount. `lessons.md` is append-only after merge, so this must be fixed on the branch.
- **Fix**: "Osiem" → "Siedem" in the new, not yet merged entry.
- **Decision**: FIXED

### F3 — Roadmap SVG legend counts are stale

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: tools/render_roadmap_svg.py:291
- **Detail**: The legend is hard-coded: `("progress", "0 w toku"), ("ready", "4 gotowe do planowania")`. With S-19 `progress`, the counts are 1 in progress and 3 ready (S-09, S-11, S-16). The regenerated SVG shows the stale counts.
- **Fix**: Set "1 w toku" and "3 gotowe do planowania", then regenerate `docs/visuals/roadmap.svg`.
- **Decision**: FIXED

### F4 — S-14 and S-15 status lines hold a pending state; their issues closed on merge

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Pattern Consistency
- **Location**: context/deployment/deploy-plan.md:906, :1058
- **Detail**: Both rewritten status lines say "No production verification is recorded yet … the runbook after the merge below is still open". That is a pending state written into the file, which the new lesson forbids, and it goes stale as soon as someone verifies. Issues #73 and #74 were closed automatically by the merge ("Closes #N"), while their "after the merge" runbooks say they are tracked in those issues and closed after verification.
- **Fix A ⭐ Recommended**: Rephrase both lines the S-19 way and reopen #73/#74 with a comment ("closed automatically at the merge; production verification pending per the runbook").
  - Strength: The file stays true after verification. Each runbook keeps its own open issue, as the workflow rules require ("never mark an issue done because code merged").
  - Tradeoff: Two issues reopened (an outward GitHub action, reversible).
  - Confidence: HIGH — the auto-close is confirmed (closed 1 s after merge, no later comments).
  - Blind spot: Whether S-14/S-15 production verification was done and recorded elsewhere has not been checked.
- **Fix B**: Rephrase both lines only, pointing to the closed issues; leave them closed.
  - Strength: File-only change, no GitHub actions.
  - Tradeoff: Nothing tracks the two open runbooks.
  - Confidence: MED — the work stays undone and invisible.
  - Blind spot: None significant.
- **Decision**: FIXED (Fix A: status lines rephrased; #73 and #74 reopened 2026-10-21)

### F5 — "No deployment" claim includes tools/, but two crons watch tools/ paths

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: context/deployment/deploy-plan.md:1213-1215
- **Detail**: The text says a PR changing only `context/`, `docs/`, `.railway/` or `tools/` gets "no deployment at all". But `tools/db-backup/**` and `tools/pipeline-trigger/**` are cron `watchPatterns` (`.railway/railway.ts:222, :264`), and the claim rests on two observations (pr-92, pr-93).
- **Fix**: Name the watched directories ("touches none of `szczur-radar-api/`, `szczur-radar-web/`, `tools/db-backup/`, `tools/pipeline-trigger/`"), drop "at all", and add "observed in pr-92 and pr-93 (2026-10-21)".
- **Decision**: FIXED

### F6 — S-19 status links to context/changes, which breaks after archive

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: context/deployment/deploy-plan.md:1191
- **Detail**: `context/changes/ops-docs-debt/ (in context/archive/ once archived)`. After `/10x-archive` on this branch, this is a dead `context/changes/` link, which is the very debt S-19 fixes, and criterion 1.2 would fail.
- **Fix**: In the `/10x-archive` commit, rewrite the link to `context/archive/<date>-ops-docs-debt/`.
- **Decision**: FIX QUEUED for the /10x-archive commit (follow-ups/review-fixes.md)

### F7 — Pre-merge runbook: several deletes may redeploy; #92 re-push

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: context/deployment/deploy-plan.md:1226-1236
- **Detail**: Step 1 runs three separate deletes on production. Each may redeploy the API, while `deploy-safety` is checked once (step 0). Deleting a sealed variable was not exercised in the PR env, although step 2's check would catch a failure. For #92, the runbook does not say that a push of #92 with the old `railway.ts` recreates the two variables in its PR env.
- **Fix**: Add to step 1 "expect up to three redeploys; re-check `deploy-safety` before each delete". Add to step 3 "a later push of that PR with the old file recreates them; delete again before it takes in `main`".
- **Decision**: FIXED

### F8 — Stale lines outside the plan's list

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Scope Discipline
- **Location**: context/deployment/deploy-plan.md:48; context/foundation/infrastructure.md:158
- **Detail**: F-03 still says the review fixes "ship in their own PR (branch `fix/java-api-rewrite-review-fixes`)", but PR #28 was merged 2026-10-03. Getting Started still mentions a "project-scoped `RAILWAY_TOKEN`", while CI uses the workspace `RAILWAY_API_TOKEN`.
- **Fix**: Past tense with "merged as PR #28" at :48, and `RAILWAY_API_TOKEN` (workspace) at infrastructure.md:158.
- **Decision**: FIXED
