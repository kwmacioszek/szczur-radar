<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: S-49: logowanie i aktywacja na wspólnym fundamencie

- **Plan**: context/changes/login-on-shared-foundation/plan.md
- **Scope**: Phase 4 of 4
- **Reviewed phases**: 4
- **Date**: 2026-11-22
- **Verdict**: APPROVED
- **Findings**: 0 critical, 2 warnings, 1 observation

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | PASS |
| Architecture | PASS |
| Pattern Consistency | WARNING |
| Success Criteria | PASS |

Success criteria re-run locally on 8a0d5e3: `npm test` (36 files, 1090 tests), `npm run build`, `npm run e2e` (27 passed), `design-check app` (OK, 73 pairs), the doc grep (no `login-form` / `until S-49` / `login-view-ui-hardening`, `AppBrand` present), the PR body (`Refs #181`, all five baseline groups, a note on merge order vs S-47). `npm run visual:docker`: 64 passed, with no baseline file changed. CI on 2123d07 is green (4.8); the run on 7c5ace8 is red only in `mutation` (F3); the run on 8a0d5e3 was still in progress during the review. 4.10: four PR-env screenshots in `evidence/` (eceb50d). 4.11: the owner's acceptance is recorded in Progress (8a0d5e3).

## Findings

### F1 — Uncommitted roadmap change without the SVG

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: context/foundation/roadmap.md:126, :279; tools/render_roadmap_svg.py:236
- **Detail**: The working tree has an uncommitted change to `roadmap.md` (S-49 `proposed` → `in-progress` in the table and in the slice block). `REPOSITORY_GUIDELINES.md` requires every change of `roadmap.md` to update the data in `tools/render_roadmap_svg.py` and regenerate `docs/visuals/roadmap.svg`. The script still says `"proposed"` for S-49, and the SVG has not been regenerated.
- **Fix**: Set S-49 to `in-progress` in `tools/render_roadmap_svg.py:236`, run `python3 tools/render_roadmap_svg.py`, and commit the three files together. Or revert the roadmap edit if `/10x-archive` sets the final status anyway.
- **Decision**: FIXED (Fix now) — status `progress` w skrypcie, SVG wygenerowane

### F2 — The guidelines still say only AppHeader has the theme toggle

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: REPOSITORY_GUIDELINES.md:55 (the "Tokens" paragraph)
- **Detail**: Phase 4 updated "Shared components", "Form fields" and "No colour literals", but the "Tokens" paragraph still says: "The button "Ciemny motyw" is `ThemeToggle` (in `AppHeader` and on the kitchen sink, section "Motyw"); pages without a header apply the stored choice." Since this change, `AuthLayout.vue` also renders `ThemeToggle`, so the public pages have the toggle. That matches the E2E contract and `e2e/theme.spec.ts` (the "logowanie" block). The sentence would mislead the next agent working on the public pages.
- **Fix**: Change it to "(in `AppHeader`, in `AuthLayout` on the public pages and on the kitchen sink, section "Motyw")" and drop "pages without a header apply the stored choice" (or keep it only for pages with neither).
- **Decision**: FIXED (Fix now) — ThemeToggle w AppHeader i AuthLayout, zdanie o stronach bez nagłówka usunięte

### F3 — The mutation job is flaky on a commit that changes no Java code

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Success Criteria
- **Location**: CI run for 7c5ace8, job `mutation (PIT on the core)`
- **Detail**: The CI run on the epilogue commit 7c5ace8 (plan and change.md only) is red: `SurvivingMutantsTest` reports the accepted mutant `ConcurrentAssessmentDispatcher.assess` as "without a surviving mutant". That mutant timed out under CI load (`TIMED_OUT`) instead of surviving. The branch changes nothing in `szczur-radar-api/`, and the same job was green on 2123d07, so the failure is not caused by S-49. It does block Wait for CI if it recurs on the head commit.
- **Fix**: If the run on 8a0d5e3 is red in `mutation` again, re-run the job (`gh run rerun <id> --failed`). Record the flake as a follow-up outside S-49, for example by giving `TIMED_OUT` of an accepted mutant the same treatment as `SURVIVED` in `SurvivingMutantsTest`, or by raising PIT's `timeoutConstant`.
- **Decision**: DEFERRED — follow-up w `follow-ups/review-fixes.md` (poza S-49); head 8a0d5e3 zielony, także mutation
