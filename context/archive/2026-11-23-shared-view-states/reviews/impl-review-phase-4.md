<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: S-48: wspólne stany widoków i stronicowanie (faza 4)

- **Plan**: context/changes/shared-view-states/plan.md
- **Scope**: Phase 4 of 4
- **Reviewed phases**: 4
- **Date**: 2026-11-23
- **Verdict**: APPROVED
- **Findings**: 0 critical, 1 warning, 3 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | WARNING |
| Scope Discipline | PASS |
| Safety & Quality | PASS |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | WARNING |

## Success criteria evidence

- 4.1 `npm test`: 1220 passed (local, 2026-11-23, on 2712cb0, after the review fixes of phases 1–3).
- 4.2 `npm run e2e`: 49 passed (local), `states.spec.ts` included.
- 4.3 `npm run visual:docker`: not re-run. No code has changed since the local run after the fixes (92 passed). CI run 45213255959 on f67f345, job `web-e2e` (visual step included): green.
- 4.4 `evidence/e2e-breaks.md`: each of the 8 new tests was broken once and went red. This covers the plan's three example breaks.
- 4.5 `gh pr checks 202`: all green on f67f345.
- 4.6 `evidence/pr-env/README.md`: steps 1–5 passed on 2712cb0. The PR env has no posts (see F2).
- 4.7 (Manual): the owner accepted in chat on 2026-11-23. No gallery was published (see F3).

What was checked and holds:
- `states.spec.ts` follows the repository's E2E rules (`fixtures.ts`, role/label/text locators in `main` or a region, `exact: true` labels, no CSS, XPath or `waitForTimeout`). Each test asserts what the plan's contract names.
- Waits are set before their clicks and wait for the retry's own request or answer.
- The request counter starts after every section has its data. Pausing the clock keeps the 10 s runs poll out of the counted window, and the test's comment says so.
- The evidence files hold no secrets.
- The focus after a failed retry is not asserted in E2E, but the unit tests cover it (`FeedView.test.ts:469`, AdminView).

## Findings

### F1 — The PR body lacks the baseline groups

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: PR #202 body (contract: plan.md, Faza 4 §3, "Opis PR wymienia grupy wzorców")
- **Detail**: The body has `Refs #180`, but it still says "W toku … Opis grup wzorców F-06 zostanie uzupełniony po ostatniej fazie." None of the groups the contract and REPOSITORY_GUIDELINES ("Visual check") require is named:
  - the new `view-feed-*` and `view-admin-*` baselines;
  - the new catalogue sections;
  - the changed `kitchen-sink-przycisk`;
  - the `view-*` baselines, unchanged;
  - the F3 group (375 px sections and states re-rasterized after the shift).
  75 screenshot files differ from `origin/main`.
- **Fix**: Rewrite the PR body: what changed, each group of baselines and why, the PR env result, the owner's acceptance, `Refs #180`. Drop the "W toku" line.
- **Decision**: FIXED. The PR #202 body is rewritten (`gh pr edit`) with what changes for the user, the F-06 groups (24 new state views, 8 new catalogue sections, 2 `kitchen-sink-przycisk`, 41 re-rasterized at 375 px, the `view-*` unchanged), the verification, the PR env result and the owner's acceptance. `Refs #180` is kept and the "W toku" line is gone.

### F2 — Paging was not clicked on the real API: the PR env has no posts

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Success Criteria
- **Location**: context/changes/shared-view-states/evidence/pr-env/README.md
- **Detail**: `GET /api/posts?page=0&size=50` returned `total: 0`. So step 2 checked only that "Strony" is absent when `total ≤ 50`. The click on "Starsze" → `/?page=1` / "Strona 2 z …" was never done on the real API. The evidence file says so. Paging with data is covered by E2E on the mock (`states.spec.ts`, `feed.spec.ts`) and by the F-06 baselines.
- **Fix**: Start a one-day run in the PR env ("Nowy przebieg", real source and LLM calls). Once `total > 50`, repeat step 2 and add the result to the evidence file.
- **Decision**: FIXED. A one-day run (2026-11-21, `wykop` and `reddit`) was started in the PR env and ended `partial` with no attention reason (325 fetched, 265 assessed on the list). Step 2 on that data: "Strony" "Strona 1 z 6"; "Starsze" → `/?page=1`, "Strona 2 z 6", 50 articles, focus on h1. Steps 4 and 5 were repeated green, and `/?page=1` is 375 wide too. Results are in `evidence/pr-env/README.md`, section "Powtórka na danych".

### F3 — 4.7 was accepted without the gallery the contract describes

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Success Criteria
- **Location**: plan.md, Progress 4.7 (contract: Faza 4 §3, "Galeria pokazuje")
- **Detail**: The contract asks for a gallery Artifact: the state views next to the `state-*` and `admin-*` prototypes, the catalogue sections next to `components-*-375.png`, both themes, 375 and 1280 px. None was published. The owner accepted the look in chat on 2026-11-23. The Progress row records that ("bez opublikowanej galerii"), but it is not yet committed.
- **Fix**: Commit the Progress row as it is, and name the acceptance without a gallery in the PR body (with F1).
- **Decision**: FIXED. The Progress row 4.7 keeps "akceptacja właściciela w czacie 2026-11-23, bez opublikowanej galerii", and the PR body says the look was accepted without a gallery.

### F4 — The guidelines give ErrorState `role="alert"` with no exception

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: REPOSITORY_GUIDELINES.md, UI → shared components, the `ErrorState` entry
- **Detail**: The entry says `role="alert"`. `ErrorState.vue` sets `:role="retrying ? undefined : 'alert'"`, so a test that looks for the alert during a retry will not find it. The view's focus duties after a retry are not mentioned either: the heading after a success, `focus()` after a failure.
- **Fix**: Write "`role="alert"` (none while `retrying`)" and add "after a retry the view focuses its heading, or the error's `focus()` when it failed again".
- **Decision**: FIXED. The `ErrorState` entry in REPOSITORY_GUIDELINES.md says `role="alert"`, none while `retrying`, and gives the view's focus after a retry: its heading, or the error's `focus()` when the retry failed again.

## Triage summary (2026-11-23)

- Fixed: F1, F2, F3, F4
- PR env, after the run: paging on the real API green (see F2). Secrets removed after the check.
