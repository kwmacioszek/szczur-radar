<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: S-47: powłoka aplikacji z nawigacją u góry

- **Plan**: context/changes/app-shell-navigation/plan.md
- **Scope**: Full plan (phase 3 reviewed although Manual 3.6 — the owner's acceptance of the screenshots — is still pending)
- **Reviewed phases**: 1, 2, 3
- **Date**: 2026-11-22
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

Evidence for the success criteria, all run on HEAD `b9d5285`:

- `npm test`: 35 files and 1104 tests passed.
- `npm run build`: OK.
- `npm run e2e`: 34 tests passed, `shell.spec.ts` included.
- `npm run visual:docker`: 60 passed, 40 skipped.
- `! grep -rn AppHeader …`: no references.
- `gh pr checks 201`: 13 of 13 checks green on `b9d5285`.
- 3.5 (PR env): results are in `evidence/pr-env/README.md`. The variant with dates on real data and the run page were not checked, because the PR env database had no run; the mock covers both. That check found the overflow at 375 px, fixed in `0980f3f`.
- Manual 3.6 is pending.

Extras outside the plan, all harmless:

- a fourth variant, "Z narzędziami", in the "Nagłówek strony" section;
- two E2E tests of `scrollWidth ≤ 375`;
- `box-sizing` on `.freshness` (`0980f3f`).

## Findings

### F1 — Post page title in the display size of a section heading

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Pattern Consistency
- **Location**: szczur-radar-web/src/components/PageHeader.vue:57-61 (used by src/views/PostDetailView.vue)
- **Detail**:
  - `PageHeader` always sets the `h1` to `--font-size-display` (3rem), or to `6xl` below 900 px. The post page passes the post's own title to it, often a long Reddit title.
  - That title used to be `--font-size-2xl`.
  - In the design (`prototypes/screens/post.html:238`, `prototype.css:1114-1117, 2415`), the post title is `.reading-title`: `6xl`, and `5xl` on narrow screens. The display size belongs only to the section name ("Posty"), `.page-title`.
  - The app's post title is therefore one step larger than the design's. The plan does not mention it, and the `view-post` baselines record it (light-375 grew from 89 KB to 105 KB).
  - The run page is correct: with a status it is `6xl`/`5xl`, the same as `.title-row h1`.
- **Fix A ⭐ Recommended**: Give `PageHeader` a size for a reading title, for example a prop `size: 'page' | 'reading'` (6xl/5xl, as `.reading-title`), set it in `PostDetailView`, and update the 4 `view-post` baselines.
  - Strength: The screen follows the design without a silent deviation, and S-51 (the post panel) gets a ready variant.
  - Tradeoff: One prop, one CSS rule, 4 baselines and one more `npm run visual:update` before the owner's acceptance (3.6).
  - Confidence: HIGH — the sizes come straight from `prototype.css`.
  - Blind spot: S-51 may move the post title out of the `h1` entirely, and the variant would then live a short time.
- **Fix B**: Keep it, and state the decision in the PR body and in the gallery for 3.6. The owner judges the size from the pictures, and S-51 rebuilds the post page.
  - Strength: No new code before the merge.
  - Tradeoff: A known deviation from the design stays in production until S-51.
  - Confidence: MED — it depends on the owner's eye in 3.6.
  - Blind spot: How long titles look at 320 px.
- **Decision**: FIXED (Fix A)
  - `PageHeader` has the prop `size: 'page' | 'reading'` (`.reading h1`: 6xl, and 5xl below 900 px), and `PostDetailView` passes `size="reading"`. There is a unit test, and the catalogue has the variant "Tytuł do czytania (post)".
  - New baselines: `view-post` in 4 projects and `kitchen-sink-naglowek-strony` in 2.
  - `npm test` (1105), `build`, `e2e` (34) and `visual:docker` all pass on a second run.

### F2 — AppShell tests do not catch the removal of the single-read guard or of the cleanup

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Success Criteria
- **Location**: szczur-radar-web/src/components/AppShell.test.ts:305-316 (and AppShell.vue:71)
- **Detail**: Two guards have no test that fails without them.
  - The test "stops listening when it is unmounted" passes even without `removeAfterEach?.()` and `removeEventListener`, because after the unmount `mounted = false` and `refreshFreshness` returns early (`AppShell.vue:71`).
  - The `reading` flag (one read at a time) has no test. Removing it from line 71 leaves every test green.
- **Fix**: In the cleanup test, spy on `document.removeEventListener` and on the remover from `router.afterEach`. Add a test where a read is still pending and two page changes follow: `fetchFreshness` is called 1 time, not 3.
- **Decision**: FIXED
  - `mountShell` gets `prepare(router)`. The cleanup test checks that the shell's `afterEach` remover is called and that `removeEventListener` gets the same `visibilitychange` handler that was added.
  - New test: "asks only once while a read is under way".
  - Breaking each guard turns the tests red: without `reading`, the new test; without `removeAfterEach` or without `removeEventListener`, the cleanup test.

### F3 — A freshness read that never settles blocks every later read

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-web/src/components/AppShell.vue:73-87
- **Detail**:
  - `reading` is reset only in `.finally`, and `api/client.ts` has no timeout or AbortSignal.
  - If one `/api/freshness` request never settles (for example the API waking up on Hobby), the date is not read again until the shell mounts again after the next login.
  - Before this change, FeedView read it on every mount. The risk is low, because browsers end a dead connection with an error.
- **Fix**: Accept it, and add one sentence on the `reading` flag in the comment of `refreshFreshness`. A timeout in the client is a change for the whole API client, outside S-47.
- **Decision**: FIXED (risk accepted, documented in the comment of `refreshFreshness` in `AppShell.vue`)

### F4 — The router test name does not match what it does

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: szczur-radar-web/src/router/index.test.ts:201
- **Detail**: The test "mounts the shell again after leaving it for /login" leaves through `/activate#token`. With a session, `/login` would send it back to `/`.
- **Fix**: Rename it to "mounts the shell again after leaving it for a public page".
- **Decision**: FIXED
