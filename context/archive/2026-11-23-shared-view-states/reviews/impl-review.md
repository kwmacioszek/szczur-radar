<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: S-48: wspólne stany widoków i stronicowanie

- **Plan**: context/changes/shared-view-states/plan.md
- **Scope**: Full plan (completed phases only; phase 4 has pending rows 4.6 and 4.7)
- **Reviewed phases**: 1, 2, 3
- **Date**: 2026-11-23
- **Verdict**: NEEDS ATTENTION
- **Findings**: 0 critical, 2 warnings, 5 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | WARNING |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | WARNING |
| Success Criteria | PASS |

## Success criteria evidence

- `npm test`: 1216 passed (local, 2026-11-23).
- `npm run build`: green (local).
- `npm run e2e`: 49 passed (local).
- Visual + axe: `npm run visual:docker` not re-run locally; CI run 45161022215 on head c15653f, job `web-e2e`, step "Run visual tests": 92 passed.
- Grep 2.6 and 3.5: clean.
- `gh pr checks 202`: all green.
- 1.4, 1.5, 2.7 (agent checks through playwright-cli): not repeated in this review; they are marked done with commits.

## Findings

### F1 — The runs poll re-runs the error's retry every 10 s

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-web/src/views/AdminView.vue:365-367 (with :302-334, ErrorState.vue:30-35)
- **Detail**: After a failed refresh, `runs.value` keeps the old rows. If one of them is active, `useEvery` keeps calling `loadRuns()` every 10 s. Each tick sets `runsRetrying = runsError` (true), so "Spróbuj ponownie" goes busy and disabled. Its failure bumps `drawing`, which creates a new `role="alert"`. Scenario: the API goes down while a run is RUNNING, and the keyboard focus is on "Spróbuj ponownie". Every 10 s:
  - the focus drops to `body` (the button the user didn't press becomes disabled, and no `settleFocus` runs);
  - the screen reader announces the alert again.
  The old code also redrew its message on each tick (`runsError = null` at the start). The focus loss is new.
- **Fix A ⭐ Recommended**: Skip the poll while the section is in error (`if (!refreshing && !runsError.value && runs.value.some(isActive))`). Recovery then comes from "Spróbuj ponownie" or "Odśwież".
  - Strength: A one-line change. It ends both the focus loss and the repeated alert. It matches the plan: while the list is replaced by the error, there is nothing to refresh.
  - Tradeoff: After the outage ends, the section doesn't recover on its own. The user has to press the button.
  - Confidence: HIGH. `refreshing` already gates the poll the same way.
  - Blind spot: An existing AdminView test may expect a poll after an error. Not checked.
- **Fix B**: Keep the poll, but leave the error silent: only a read from the button or "Odśwież" sets `runsRetrying`.
  - Strength: The section recovers by itself after an outage.
  - Tradeoff: A background read of a section in error shows no busy state, and its failure must not re-key the alert. This adds a "user-started" flag to `loadRuns`.
  - Confidence: MED. More state paths to test.
  - Blind spot: The interaction with `latestRefresh` when a user retry overlaps a poll tick.
- **Decision**: FIXED (Fix A) — `!runsError.value` in the `useEvery` condition; test "does not refresh while the section shows its error" (red without the fix: 8 calls instead of 2)

### F2 — A newer read cancels the focus restore of a section retry

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-web/src/views/AdminView.vue:146-166 (same in every section loader; settleFocus :62-66)
- **Detail**: `settleFocus` runs only in the read that was called with `{ retry: true }`. If a newer read supersedes it, the `return` in `try`/`catch` skips the `if (retry)` line. Example: a user retries "Dni bez danych". Before the answer, the runs poll sees a run end and calls `void loadMissing()` (:315-316). The retry's answer is dropped, and the newer read has `retry = false`. The focus stays on `body`, where it fell when the busy button was disabled. This is rare (it needs a run to end during the retry), but it breaks the "Critical Implementation Details" contract. Also, `settleFocus` takes `xErrorState.value` before `nextTick`. That works today only because the `ErrorState` instance survives its re-key, so it is fragile if the template changes.
- **Fix**: Add a per-section "focus owed" flag. The retry sets it, and whichever read is the latest consumes it when it settles. Read the refs (heading, error state) after `nextTick`, inside `settleFocus`, through getters.
- **Decision**: FIXED. `sectionFocus(heading, errorState)` per section (`claim(read)` / `settle(ok)` in the latest read's `finally`, refs read after `nextTick`). `Read = { by?: 'retry' | 'action' }`: `action` ("Odśwież", "Uruchom", "Ponów zaznaczone", "Przywróć martwe") cancels a pending focus. Two new tests:
  - superseded by a background read → focus goes to the heading (red on the old code);
  - superseded by "Odśwież" → focus stays (red without the cancel).

### F3 — 41 existing baselines changed that the plan did not list

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Scope Discipline
- **Location**: szczur-radar-web/visual/__screenshots__/{light,dark}-375/ (commit fed862a)
- **Detail**: The plan (Faza 1 §6, Faza 4 §3 at plan.md:493) names only the changed `kitchen-sink-przycisk`. fed862a also changed 41 more files in both 375 px projects:
  - the catalogue sections below the new ones: `dzwoneczek`, `filtr`, `ikony`, `marka`, `naglowek-strony`, `ocena-posta`, `pasek-aplikacji`, `pola`, `posty`, `znacznik-stanu`, and `stanowisko` (dark only);
  - all `state-przycisk-*`, `state-nawigacja-*`, `state-pola-email-hover` and `state-pola-search-focus`.
  The commit message explains it: the sections moved down and were re-rasterized, with a channel difference of 39 or less and no visible change. The PR body isn't final yet. REPOSITORY_GUIDELINES ("Visual check") requires it to name every changed group and say why.
- **Fix**: In the final PR body (phase 4), add the group "sekcje katalogu i stany 375 px pod nowymi sekcjami — przerasteryzowane po przesunięciu, bez zmiany wyglądu" next to the groups from plan.md:493.
- **Decision**: FIXED in plan: the group was added to the PR-body contract of Faza 4 §3 (plan.md, under the line "Opis PR wymienia grupy wzorców"). The PR body itself is still phase 4 work.

### F4 — `aria-busy` during a retry: the admin panel differs from the other views

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: szczur-radar-web/src/views/AdminView.vue:505, 551, 584, 628, 693
- **Detail**: During their own retry, FeedView (:199), PostDetailView (:163) and RunDetailView (:173) keep the container `aria-busy="true"`. The five sections of AdminView don't: `xLoading` is false while `xRetrying` is true, and `AdminView.test.ts:291, 309` pins that. The plan ("dopóki wczytuje się bez danych na ekranie", plan.md:378) can be read either way, because during a retry there is no data on screen either.
- **Fix**: Make each section `aria-busy` also while `xRetrying` (`:aria-busy="xLoading || xRetrying ? 'true' : undefined"`), as in the other views, and update the two tests.
- **Decision**: FIXED. All five sections use `xLoading || xRetrying`. The test "keeps the errors with a busy button while recovering" now expects `aria-busy="true"` during the retry and none after the answer. E2E: 49 passed.

### F5 — "Wpisz do formularza" does nothing visible while the form is absent

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-web/src/views/AdminView.vue:176-184
- **Detail**: While the sources are loading or in error, the form is replaced by `LoadingState`/`ErrorState`. A click on "Wpisz do formularza" then:
  - sets the dates;
  - filters `chosenSources` against an empty `sources`, giving `[]`;
  - moves no focus (`fromInput` is null).
  The user gets no feedback, and a later successful `loadSources` overwrites the range's sources with the defaults. This matches the plan's wording (plan.md:382), but the range's sources are lost.
- **Fix**: Disable "Wpisz do formularza" while there is no form (sources loading or in error).
- **Decision**: FIXED. `formShown` computed; the button is `:disabled="!formShown"` and `fillForm` returns early without the form. The test "disables Wpisz do formularza while the form is not there, and enables it once the sources load" replaces the old one and is red without the fix. This deviates from plan.md:382 ("wpisuje zakres bez przenoszenia fokusu"); the plan line is not edited.

### F6 — Back to the page on screen during a page load refetches it with a spinner

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-web/src/views/FeedView.vue:62-65, 185
- **Detail**: On page 0 the user clicks "Starsze", then presses browser Back before the answer. The load kind is `page` with `loading.page === shown.page`, so `pagerBusy` is `'previous'`. The disabled "Nowsze" shows "Wczytywanie…", and the view refetches page 0, which is already on screen. It ends correctly, with a scroll to the top and the focus on h1. Cosmetic.
- **Fix**: When the target page and filter equal what is shown, supersede the in-flight load (`++latestLoad`, clear `loading`) and skip the fetch.
- **Decision**: FIXED. The route watcher in FeedView drops the in-flight `page` load when the target is the page and filter on screen, fetches nothing, then scrolls to the top and focuses the heading after `nextTick`, as after any page change. The test "fetches nothing when the way back returns to the page on screen while the next one loads" is red without the fix (3 fetches instead of 2).

### F7 — Two meanings of "retry" in the "Nieudane posty" section

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: szczur-radar-web/src/views/AdminView.vue:396
- **Detail**: In one section, `retrying` / `retry()` (the "Ponów zaznaczone" action on posts) sit next to the new `failedRetrying` and the `{ retry }` read option (reloading the section). This makes the code easy to misread.
- **Fix**: Rename the action's names to `retryingPosts` / `retryPosts`.
- **Decision**: FIXED (differently named). `retryPosts` is already the imported API function, so the names are `retryingSelected` / `retrySelected`, after the button "Ponów zaznaczone".

## Triage summary (2026-11-23)

- Fixed: F1 (Fix A), F2, F3 (in the plan), F4, F5, F6, F7 (under other names)
- After the fixes (local):
  - `npm test`: 1220 passed;
  - `npm run build`: green;
  - `npm run e2e`: 49 passed;
  - `npm run visual:docker`: 92 passed, no baseline changed.
- Every new or changed test was red on its break before the fix was restored.
