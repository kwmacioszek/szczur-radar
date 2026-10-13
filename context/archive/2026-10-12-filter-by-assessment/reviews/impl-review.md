<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Filtrowanie po ocenie LLM (S-04)

- **Plan**: context/changes/filter-by-assessment/plan.md
- **Scope**: Full plan
- **Reviewed phases**: 1, 2, 3
- **Date**: 2026-10-13
- **Verdict**: NEEDS ATTENTION
- **Findings**: 0 critical, 4 warnings, 5 observations

Evidence:
- Branch `feature/filter-by-assessment`, `002da67..e37eef7`: commits `089023b` (phase 1), `d1930d7` (phase 2), `e37eef7` (phase 3).
  - After the rebase onto `main` (`4fdd51a`, 2026-10-13) the same commits are `d2225c9`, `1cb9e4b` and `0290396`; the review fixes are the next commit, `fix(filter-by-assessment): poprawki z impl-review`.
- Two parallel reviews: plan adherence, and safety / quality / patterns. The second included EXPLAIN ANALYZE on 100k synthetic posts and a pgjdbc check against postgres:18.
- Automated criteria re-run on 2026-10-13:
  - `./mvnw -B verify` exit 0 (`PostsApiIT` 13/13, `AuthorizationCompletenessIT` 2/2, the latter unchanged);
  - `npm test` 153/153, `npm run build`, `dist/` without kitchen sink;
  - `node --check dev/mock-api.js`;
  - `grep -c FeedFilter REPOSITORY_GUIDELINES.md` = 1;
  - `gh pr checks 54`: all green on `e37eef7`.

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | WARNING |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | WARNING |
| Success Criteria | PASS |

## Findings

### F1 — Znak NUL w `q` daje 500 zamiast 400

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/posts/api/PostController.java:60
- **Detail**:
  - `GET /api/posts?q=%00` passes the firewall, `@Size` and `Whitespace.strip`.
  - Postgres refuses the bound parameter: `22021 invalid byte sequence for encoding "UTF8": 0x00`.
  - `ApiExceptionHandler` has no handler for `DataAccessException`, so the request ends in a 500 and an ERROR stack trace in the log. Verified in the PR env: `500 {"status":500,"error":"Internal Server Error","path":"/api/posts"}`.
  - Only a logged-in user can trigger it, and nothing leaks.
- **Fix**: Add `@Pattern(regexp = "[^\\p{Cntrl}]*")` to `q`, so the existing `invalid_request` 400 path handles it, and add `q=%00` to `badFilterValuesAreAnInvalidRequest`.
- **Decision**: FIXED — rejects only NUL (`@Pattern(regexp = "[^\\x00]*")`), not every control character: Postgres text refuses only 0x00, and a tab or newline at the ends is stripped as white space. IT case `q=or\u0000len` → 400; break check without the annotation: PostsApiIT fails.

### F2 — „Wyczyść filtry” w formularzu gubi fokus

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-web/src/components/FeedFilter.vue:68
- **Detail**:
  - The button is `v-if="isFilterActive(filter)"`, so clearing removes the element that has focus, and focus falls to `<body>`.
  - The plan's rule is that after a filter change the focus stays in the form.
  - A keyboard or screen-reader user lands at the start of the document.
  - The test "clears the filter from the form" does not check focus.
- **Fix**: After its own "Wyczyść filtry", `FeedFilter` moves focus to the "Spółka lub temat" field, so focus stays in the form. Add a test asserting `document.activeElement`.
- **Decision**: FIXED — `FeedFilter` focuses its text field before emitting `clear`; test "keeps the focus in the form…" (break check without the focus call: the test fails).

### F3 — Pola 40 px, przyciski 37,2 px przy szerokości ≤1024 px

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: szczur-radar-web/src/style.css:154-158
- **Detail**:
  - The field height is a fixed `40px`.
  - The button's height comes from the root line height (`font: 18px/145%`, and 16px at ≤1024 px): 6 + 6 + 26.1 + 2 = 40.1 px above 1024 px, but 6 + 6 + 23.2 + 2 = 37.2 px at or below it.
  - Between 641 and 1024 px the feed filter has fields and buttons in one row, and `/admin` mixes them too, so they differ by about 3 px.
  - `REPOSITORY_GUIDELINES.md` says "as tall as a button" without a width condition, and the README measured only at 1280.
- **Fix**: Use `height: calc(1.45rem + 14px)` (the root line height + 2 × 6px padding + 2px border), the same formula as the button at every width. Update the comment and check the height at 800 px.
- **Decision**: FIXED — `height: calc(1.45rem + 14px)` (the button's own formula). Measured with the mock: 40.1 px at 1280 and 37.2 px at 800 for fields and buttons, in the feed filter and `/admin`; README updated.

### F4 — Odstępstwa od planu nie są zapisane w planie

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: context/changes/filter-by-assessment/plan.md (Phase 1 items 1–2, Phase 2 item 6)
- **Detail**: Plan item 1.1 names `config/WebConfig.java` with `addFormatters`, which does not exist. The other deviations are not in the plan or the README either:
  1. `config/ContractValueBinding.java` (`@ControllerAdvice` + `@InitBinder` PropertyEditor) replaces it. With a converter, Spring fell back to `Enum.valueOf`, which accepted `BULLISH`. The reason is only in the class Javadoc and the commit message.
  2. The ILIKE escape character is `!`, not the backslash proposed in research.md:214, because Hibernate parses backslashes in native queries.
  3. `PostFilter` normalises in its compact constructor, not in a `PostFilter.of` factory.
  4. Focus goes to the `h1` after "Wyczyść filtry" from the empty result. The plan says "the same as clear" and that a filter change moves no focus.

  All four are sound, but a later reader of the plan will look for `WebConfig`.
- **Fix**: Add the section `## Adaptacje w trakcie implementacji` at the end of the plan, above Progress, with these four points and their reasons.
- **Decision**: FIXED — section `## Adaptacje w trakcie implementacji` added to plan.md above Progress (points 1–10, including the fixes from this review).

### F5 — Ponowne „Filtruj” z tym samym filtrem na stronie > 0 przewija i przenosi fokus

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: szczur-radar-web/src/views/FeedView.vue:86-88, 114-116
- **Detail**: `applyFilter` drops `page`. When the filter is unchanged, the watcher sees a page-only change, scrolls to the top and focuses the `h1`. This contradicts the plan: submitting the filter neither scrolls nor moves focus.
- **Fix**: `applyFilter` sets a flag (like `focusAfterClear`) that the watcher reads as a filter action, so there is no scroll and no focus. Add a test on `/?page=2&q=x`.
- **Decision**: FIXED — `applyFilter` sets `fromForm` while its navigation is in flight; the watcher does not treat that page change as paging. Test "submits an unchanged filter from a later page…" (it also checks that the pager scrolls and focuses afterwards); break check: the test fails without the condition. In the browser with the mock, `/?kind=company&page=1` → "Filtruj" → `?kind=company`, focus stays on "Filtruj".

### F6 — Zapytanie z filtrem: zliczanie skanuje wszystkie ocenione posty; reguła „najnowsza ok” w dwóch miejscach

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/posts/PostRepository.java:89-99
- **Detail**:
  - EXPLAIN ANALYZE on 100k assessed posts:
    - the page query with a sparse filter takes 44 ms (it walks `ix_posts_stage_published`, and the lateral subquery uses `ix_assessments_post_latest`);
    - the count query takes 0.6–0.95 s (a sequential scan of `posts`, plus a lateral lookup and `jsonb_array_elements` per row);
    - a filter with no matches makes the page query pay the same scan.
  - Today's ~300 posts take milliseconds; at ~270 a day, 100k is about a year away.
  - "Latest ok" (`status = 'ok' ORDER BY assessed_at DESC, id DESC`) is also written in `AssessmentRepository.findLatestOk`. Both agree today.
- **Fix**: No change now. Record a follow-up in issue #17 or the roadmap: past ~20k posts, measure and consider a projection (a `latest_ok_assessment_id` column or a `post_targets` table, plus `pg_trgm`). The projection also removes the duplicated rule.
- **Decision**: DEFERRED — no code change (user decision). Recorded as a future TODO in the roadmap: Parked „Wydajność filtra feedu przy dużej liczbie postów (TODO na przyszłość)”, v2.13 note, the „Ochrona i skala” card in `tools/render_roadmap.swift`; `docs/visuals/roadmap-mvp.png` regenerated.

### F7 — Lokalizacja produkcyjnej bazy nie jest sprawdzona

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: context/changes/filter-by-assessment/ui-after/README.md (PR env section)
- **Detail**:
  - `ILIKE` folds the case of Polish letters only with a suitable `LC_CTYPE`.
  - Testcontainers and the PR env (`c | en_US.utf8`) are checked; production is not.
  - The plan moves this to the runbook in issue #17 after the merge, but nothing confirms the runbook has the `pg_database` query.
- **Fix**: Make sure the runbook in issue #17 includes `SELECT datlocprovider, datcollate, datctype FROM pg_database WHERE datname = current_database()` (via `railway ssh -s postgres -e production`) and the `q=KĘTY`-style search on production.
- **Decision**: FIXED — runbook „filter-by-assessment: filtr feedu (S-04)” in `context/deployment/deploy-plan.md` (locale via `railway ssh -s postgres`, Polish case, same target, empty result, 400 for `kind=xyz` and `q=%00`, closing #17); a comment in issue #17 links it after the review fixes are pushed.

### F8 — SPA i API przycinają inne zbiory białych znaków

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-web/src/feedFilter.ts:23-26
- **Detail**:
  - JavaScript `trim()` leaves U+0085 and U+001C–U+001F, which the API's `Whitespace.strip` removes, and it removes U+FEFF, which the API keeps.
  - `q=%C2%85` is therefore an active filter in the SPA but no filter in the API: the page says "Pasujące do filtra: N" over the whole feed.
  - Hard to hit by hand; it takes a crafted link.
- **Fix**: Skip, or align `normalizeQuery` with the contract §10 set from `text/Whitespace.java`.
- **Decision**: FIXED — `normalizeQuery` strips the contract §10 set (`text/Whitespace.java`) instead of `String.trim()`; test with U+0085, U+2007, U+001F, U+3000 and U+FEFF (break check: with `trim()` the test fails).

### F9 — Dane pokazowe: cytaty kitchen sink nie są dosłowne; mock odrzuca puste `kind=`

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: szczur-radar-web/src/views/KitchenSinkView.vue:112, 150; szczur-radar-web/dev/mock-api.js:116-117
- **Detail**:
  - In the kitchen sink the quotes 'energetyka' and 'ropy' do not appear in their posts' bodies, against D6 (the quote is verbatim); the mock's data was fixed for this.
  - The mock answers an empty `kind=` or `direction=` with 400, while the API treats an empty value as no condition (`ValueEditor`). The SPA never sends an empty key.
- **Fix**: Put the quotes verbatim into the kitchen sink posts' bodies, and make the mock treat an empty value as absent.
- **Decision**: FIXED — the kitchen sink posts contain their quotes verbatim ("Cała energetyka…", "…przy spadku ceny ropy"); the mock treats an empty `kind=`/`direction=` as no condition, strips the §10 white space from `q` and answers 400 for a NUL, like the API (checked in the browser: `kind=` → 120, `q=%C2%85orlen%E3%80%80` → 20, `q=%00` → 400).
