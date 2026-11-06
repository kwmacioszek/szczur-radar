<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Feed z testami regresji i granicą numeru strony

- **Plan**: context/changes/feed-regression-and-paging-bounds/plan.md
- **Scope**: Full plan (3 of 3)
- **Reviewed phases**: 1, 2, 3
- **Date**: 2026-11-06
- **Verdict**: APPROVED
- **Findings**: 0 critical, 1 warning, 3 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | PASS |
| Architecture | PASS |
| Pattern Consistency | WARNING |
| Success Criteria | PASS |

Success criteria re-run on HEAD 52c24fb: `./mvnw -B verify` 1031 unit + 338 IT green; `npm test` 519 passed (28 files); `npm run build` ok. No Manual rows in the plan.

## Findings

### F1 — Roadmap changed, its SVG data and image did not

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: tools/render_roadmap_svg.py:220, docs/visuals/roadmap.svg
- **Detail**: REPOSITORY_GUIDELINES.md: whenever roadmap.md changes, update the data in tools/render_roadmap_svg.py and regenerate docs/visuals/roadmap.svg. S-44 went ready → in-progress in roadmap.md (commit 023a51f), the SVG data still says "ready".
- **Fix**: Set S-44's status in render_roadmap_svg.py to the "progress" state, run `python3 tools/render_roadmap_svg.py`, commit both.
- **Decision**: FIXED — S-44 set to "progress" in render_roadmap_svg.py, docs/visuals/roadmap.svg regenerated

### F2 — Runs edge test does not check `total`

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/api/PipelineRunControllerIT.java (aRunsPageBeyondTheOffsetLimit...)
- **Detail**: Plan: the last allowed page is 200 with an empty list and a correct `total`. The feed and failed-list tests check `total`; the runs test only checks empty `items` (the IT is not transactional, so it has no fixed count).
- **Fix**: Assert `$.total` equals the `$.total` of `/api/admin/pipeline-runs?size=1`.
- **Decision**: FIXED — PipelineRunControllerIT asserts $.total equals count(pipeline_runs)

### F3 — The page limit literal in the mock is unguarded

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: szczur-radar-web/dev/mock-api.js:457
- **Detail**: `21474836` is a bare copy; `rules.test.ts` scans only error-code literals in the mock, so it can drift silently. Dev mock, low risk; S-43 (contract-bound mocks) is where mocks get tied to the contract.
- **Fix**: Leave it for S-43 (or add a one-line check in rules.test.ts that the mock contains `String(rules.limits.pageMax)`).
- **Decision**: FIXED — rules.test.ts checks the mock contains "pageNumber > <pageMax>"

### F4 — Cosmetics: import order and an unexplained `'1e9'` case

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: SpaRulesTest.java (import of config.PageLimits), szczur-radar-web/src/feedFilter.test.ts ('1e9' → 1)
- **Detail**: `config.PageLimits` sits after `posts.Post` instead of next to `config.ErrorCode`; the `'1e9' → 1` assertion pins parseInt's leniency without saying so.
- **Fix**: Move the import beside `config.ErrorCode`; add a one-line comment to the `'1e9'` assertion.
- **Decision**: FIXED — import moved beside config.ErrorCode; comment on the '1e9' assertion
