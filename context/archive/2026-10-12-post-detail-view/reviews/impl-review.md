<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Widok szczegółowy posta (S-03)

- **Plan**: context/changes/post-detail-view/plan.md
- **Scope**: Full plan
- **Reviewed phases**: 1, 2, 3, 4
- **Date**: 2026-10-13
- **Verdict**: APPROVED
- **Findings**: 0 critical, 2 warnings, 6 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | WARNING |

### Evidence

- **Automated criteria, all re-run on `e332547`:**
  - `./mvnw -B verify`: PASS;
  - `npm test`: PASS (182 tests);
  - `npm run build`: PASS;
  - grep 2.2 = 4, grep 3.2 = 2, grep 4.3 = 1;
  - `gh pr checks 53`: all green.
- **Manual criteria:** rows 1.2, 2.3, 3.3, 4.5 and 4.6 are `[x]`, confirmed by the operator. For 3.3 there is also a Playwright run with the mock (`ui/README.md`, "Nawigacja"). For 1.2 and 4.6 there is a record (`ui/README.md`, "PR env").
- **Out-of-scope list:** nothing from "What We're NOT Doing" was built. `AppHeader`, `PostItem` and the shape of `GET /api/posts` are unchanged, and there are no migrations.
- **Additions outside the plan text:** all were reported during implementation and none breaks the plan's intent:
  - a `tag` prop on `StanceText`;
  - the `h1` is always present;
  - `window.scrollTo` stubs in the tests;
  - the mock and the kitchen sink data are now contract-valid;
  - the 404 in the mock's `empty` mode;
  - two pieces of copy outside the plan;
  - the roadmap with its renderer and PNG.

## Findings

### F1 — An unmounted FeedView still reports its load to the scroll restore

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-web/src/views/FeedView.vue:46-64, szczur-radar-web/src/router/scroll.ts:22-45
- **Detail**:
  - **Cause:** `latestLoad` belongs to one FeedView instance and does not change on unmount. There is no `onBeforeUnmount` anywhere in `src`.
  - **Scenario (slow API):**
    1. Back from the post to the feed: FeedView F1 starts load L1.
    2. Forward to the post: F1 unmounts.
    3. Back again: F2 registers a new waiter.
    4. L1 finishes and calls `reportFeedLoaded('ok')`. The waiter resolves while F2 still shows "Wczytywanie…".
  - **Effect:** the saved position is applied to a short page, the browser clamps it, and the restore is lost. Vue-router does not scroll another route, so nothing worse than a lost restore happens.
  - **Wrong comment:** the comment in `scroll.ts` says "a later navigation replaces it", but only a later return to the feed replaces it.
  - **Missing test:** `scroll.test.ts` and `FeedView.test.ts` do not cover this case.
- **Fix**: In FeedView add `onBeforeUnmount(() => { latestLoad++ })`, so a load that ends after unmount reports nothing. Correct the comment in `scroll.ts` and add a test: unmount during a load, then no `reportFeedLoaded`.
- **Decision**: FIXED (2026-10-13, `e6d7879`). `onBeforeUnmount` in `FeedView.vue` advances `latestLoad`, so a load that ends after unmount reports nothing. The comment in `scroll.ts` now says only a later return to the feed replaces the waiter. Test "stays silent about a load that ends after it was unmounted" (`FeedView.test.ts`). Break-check: red without the fix.

### F2 — The PR env record has no screenshots

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Success Criteria
- **Location**: context/changes/post-detail-view/ui/README.md:31-47
- **Detail**: The plan's contract for Phase 4, item 3, says the PR env record contains "datę, adres, commit web, liczbę postów i zrzuty". The README has the date, address, commits, post count (212/206) and the operator's confirmation, but none of the 38 PNGs comes from the PR env. All of them are from the mock.
- **Fix**: The operator takes two screenshots in PR env #53 (a post at 1280 px light and 375 px dark, with real data), adds them to `ui/` and links them in the "PR env" section. If that is not worth doing, record in the README that the PR env check was manual, without screenshots.
- **Decision**: FIXED via Fix now (2026-10-13). Three screenshots from PR env #53 with real data (web and API on `e6d7879`), in a Playwright window where the operator logged in themselves: `ui/prenv-feed-1280-light.png`, `ui/prenv-post-1280-light.png`, `ui/prenv-post-375-dark.png`. The e-mail of the logged-in account was masked before the screenshot. Linked in the "PR env" section of `ui/README.md`.

### F3 — The screenshot matrix does not cover every theme × width combination

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Success Criteria
- **Location**: context/changes/post-detail-view/ui/README.md:11-19
- **Detail**: Phase 4, item 1, names 1280×900 and 375×812 in both the light and the dark theme. For the error, not found, loading, raw-error, raw-loading and prefilter states there is only `1280-light` plus `375-dark`, and `feed-card-links` has no `375-dark`. Each state still has both widths and both themes, just split across two files. Overflow at 375 px was checked on every shot.
- **Fix**: Add a sentence to the README saying that the missing combinations are covered by the other two (or add the missing PNGs with the mock).
- **Decision**: FIXED via the alternative (2026-10-13). The missing PNGs were made with the mock (port 5174, Playwright session `s03`), so every state now has 1280/375 × light/dark: 56 mock screenshots in all, retaken after F6, with no horizontal overflow at 375 px.

### F4 — Accessibility of the post view's links and buttons

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-web/src/views/PostDetailView.vue:124-128, 146-150
- **Detail**:
  - **Retry in RAW:** "Spróbuj ponownie" calls `loadRaw` and disappears, so focus falls to `<body>`. FeedView handles the same case by moving focus to the heading.
  - **Link to the original:** it opens in a new tab (`target="_blank"`) with no cue for screen readers.
  - **"← Posty":** a screen reader reads the arrow aloud. The link also has the same accessible name as the header's "Posty", but behaves differently (back with the position restored).
- **Fix**:
  - After the RAW retry, move focus to `<summary>`.
  - Put the arrow in `<span aria-hidden="true">`.
  - Add hidden text "(otwiera się w nowej karcie)" to the link to the original, using a visually-hidden class in `style.css` or an `aria-label`.
- **Decision**: FIXED (2026-10-13, `e6d7879`). `retryRaw()` focuses `<summary>` before loading again. The "←" is in `<span aria-hidden="true">`. The link to the original has `aria-label` "Otwórz oryginał na {host} (otwiera się w nowej karcie)". Tests in `PostDetailView.test.ts`; break-check: red without the focus on summary.

### F5 — After returning to the list, keyboard focus is on `<body>`

- **Severity**: 💡 OBSERVATION
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-web/src/views/FeedView.vue:76-86, szczur-radar-web/src/router/scroll.ts:41-44
- **Detail**: The post view moves focus to its `h1`. On a return FeedView does not move focus (`changed === false`): the position is restored, but a keyboard user starts from the top of the document.
- **Fix**: After the position is restored, move focus to the heading link of the post just left. `scrollBehavior` knows `from.params.id`, and FeedView could focus `a[href="/posts/<id>"]`.
  - Strength: keyboard and screen reader users come back where they were, the same promise as the scroll position.
  - Tradeoff: a third coordination point between router and FeedView (beside the report and the waiter), which the hardening focus logic must not override.
  - Confidence: MED — a common SPA pattern, but no such case exists in this repo yet.
  - Blind spot: not checked how screen readers announce focus moved during scroll restoration.
- **Decision**: FIXED (2026-10-13, `e6d7879`). After the feed reports `ok`, `scrollBehavior` focuses `a[href="<from.path>"]` with `preventScroll` and returns the saved position; after an error it leaves focus alone. Tests in `scroll.test.ts`; break-check: red without `focusLinkTo`.

### F6 — "fakt" suggests a verified truth

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-web/src/labels.ts:25-27
- **Detail**: The contract says `factual_claim` means the text presents something as fact and "model jej nie weryfikuje" (`contract-v2.md:175`). The label "fakt" in the UI can read as confirmed. `trade_action: 'działanie'` matches the contract ("deklarowane … działanie"), so it can stay.
- **Fix**: `factual_claim: 'informacja'` (or "stwierdzenie"), plus the `PostAssessment` tests and kitchen sink that use the label.
- **Decision**: FIXED (2026-10-13, `e6d7879`). `factual_claim: 'informacja'`; `PostAssessment.test.ts` updated. The plan text (Phase 2, item 2) still says "fakt": the plan's phase blocks are not edited after implementation, and the change is recorded here.

### F7 — Link styles are repeated in three places

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: szczur-radar-web/src/components/AppHeader.vue:61, szczur-radar-web/src/components/PostCard.vue:93, szczur-radar-web/src/views/PostDetailView.vue:184
- **Detail**: Each of the three places sets its own scoped link colour (`--accent`), underline and hover. `style.css` has no global `a` rule. lessons.md recommends tidying such things while the code is small.
- **Fix**: Leave it for a `/10x-ui` pass over the post view: one global link rule in `style.css` (colour, hover), with local exceptions only.
- **Decision**: SKIPPED: deferred to a `/10x-ui` pass over the post view (one global link rule in `style.css`).

### F8 — The `/raw` Javadoc promises more than value equality

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/posts/api/PostController.java:86-94
- **Detail**: "with every value as stored" is true of values, not of their text:
  - number notation can change (`1e10` → `1E+10`, `1.0E-5` → `0.000010`);
  - `\u` escapes come back as characters;
  - duplicate keys collapse.

  This does not matter for payloads Jackson wrote (Wykop), but the doc is too broad. A bad hash or an unreadable payload gives 500, and the UI offers a retry that will not succeed; that is acceptable for an integrity fault.
- **Fix**: Javadoc: "values unchanged (numbers may change notation, escapes come back as characters), indented for reading".
- **Decision**: FIXED (2026-10-13, `e6d7879`). Javadoc: "the values are unchanged, though a number may change notation and an escape comes back as its character".
