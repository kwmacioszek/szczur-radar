<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Kontrakt oceny LLM V2 z walidatorem i pomiarem na wzorcu

- **Plan**: context/changes/assessment-contract-harness/plan.md
- **Scope**: Phase 5 of 6
- **Reviewed phases**: 5
- **Date**: 2026-10-04
- **Verdict**: NEEDS ATTENTION
- **Findings**: 0 critical, 5 warnings, 5 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | WARNING |
| Scope Discipline | WARNING |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | WARNING |
| Success Criteria | WARNING |

Automated criteria 5.1–5.4: `./mvnw -B test` → 412 tests, 0 failures, 0 skipped (`GoldDataTest` 3/3, `GoldMetricsTest` 17/17, `AnchorMatcherTest` 15/15). Manual 5.5–5.6: confirmed by the user in session (labels 120/120, §16 decisions in N11).

Plan adherence otherwise MATCH: mapper (entry/link, Warsaw time winter/summer, no author/URL/id), sheet paths and export format, every metric definition, `AnchorMatcher` boundaries and maximum 1:1 matching, `GoldDataTest` checks. Documented deviations: search API instead of the tag stream and fixtures from real search answers (N7, N9), media flags and local links (N10), `AnchorMatcherTest` separate (N8), prompt v2.3 / §6.5a (N11).

## Findings

### F1 — Prompt and instructions still say "peron — NIE jest celem"

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: szczur-radar-api/src/main/resources/assessor/system-prompt.md:64; context/changes/assessment-contract-harness/pilot/labeling-guidelines.md:61
- **Detail**: The slang dictionary says "peron, na peronie … (NIE jest celem)", while prompt line 34, contract §7.1.6 and the new §6.5a (which cites "na peronie" as its model) make it the anchor of target "rynek". v2.3 edited the same line and left the contradiction; the model gets two opposite rules before phase 6 records it.
- **Fix**: Change the dictionary entry in the prompt and the instructions to "peron, na peronie = przegapienie wzrostu → cel „rynek” (bullish, §7.1.6)"; prompt v2.4 (no recording uses v2.3 yet), new hash in `AssessmentPromptTest`.
- **Decision**: FIXED — dictionary entry in prompt and guidelines; prompt v2.4 (hash pinned)

### F2 — `WykopRepository` can lose or silently truncate a day

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/sources/wykop/WykopRepository.java:96-116
- **Detail**: (a) `json.treeToValue(element, WykopItem.class)` is not caught — one element with an unexpected type (non-numeric `id`, objects in `tags`) aborts the whole search with a raw Jackson exception; (b) a page shorter than 50 ends the search even when `pagination.total` says more remain (a silent cap would cut the day); (c) no dedup by `(resource, id)` — posts added or removed while paging with `sort=newest` shift pages, giving duplicates or skips, and duplicates inflate the `>= total` check. The gold sample was not affected (closed days, totals matched: 262 = 5×50+12), but S-02 will page "today".
- **Fix**: Per-element mapping failure → skip and count (or `WykopApiException` with the element index); dedup by `(resource, id)`; short page with `items < total` → `WykopApiException`; sampler refuses days ≥ today; tests for each.
  - Strength: The repository is production code for S-02, which reads open days; failures become explicit instead of silent data loss.
  - Tradeoff: A few more branches and four tests now, before S-02 defines its error policy.
  - Confidence: MED — behaviour of Wykop on concurrent inserts and limit caps is inferred, not observed.
  - Blind spot: Whether Wykop's search index is stable within a paging run.
- **Decision**: FIXED — skip and count malformed elements (`WykopSearchResult`), dedup by (resource, id), short page before total → `WykopApiException`, sampler refuses days ≥ today; tests

### F3 — Anonymizer gaps: case-sensitive bare nicks, e-mail local parts

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/test/java/pl/szczurradar/api/measurement/WykopGoldSampler.java:59, :176
- **Detail**: Bare author nicks are replaced case-sensitively ("szczurek99" stays when the author is "Szczurek99"); `MENTION` has no lookbehind, so `jan.kowalski@gmail.com` becomes `jan.kowalski@uzytkownikN.com`; nicks of ≥ 4 characters that are common words would be replaced everywhere, and the replaced list is not printed for review. The 4-character minimum is an undocumented deviation from the plan ("nicki autorów"). Checked: the committed `posts.json` and fixtures contain no real nick, e-mail or URL.
- **Fix**: Case-insensitive bare-nick pattern, e-mails replaced before mentions plus lookbehind `(?<![\p{L}\p{N}._-])`, print the replaced nicks, note the minimum length in the README; tests in `WykopAnonymizerTest`. Do not re-sample (the current sample is clean and labeled).
- **Decision**: FIXED — case-insensitive bare nicks, e-mails → `<email>` before mentions, mention lookbehind, replaced nicks printed; tests; sample not re-drawn

### F4 — v2.3 rules come from the gold set that will score them

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Success Criteria
- **Location**: szczur-radar-api/src/main/resources/assessor/system-prompt.md:21-22; context/foundation/llm-assessment/contract-v2.md §6.5a
- **Detail**: §6.5a and the mood-tag example were written after labeling the 2026-10-01/02 posts, and the prompt now names their phrases ("dzień kibla", "pompa", "spadki", "bessa", "krach"). Pilot days are excluded from the gold set for exactly this reason (`GoldLabels.EXCLUDED_DAYS`). Scale: 7 of the gold targets (10-01-005, 014, 023, 024; 10-02-005, 043, 057).
- **Fix A ⭐ Recommended**: State it in the phase 6 report and give target metrics with and without these 7 targets.
  - Strength: Cheap; the rule aligns the prompt with the user's reading rather than with model output, and the effect is measurable.
  - Tradeoff: The headline number stays slightly optimistic for the slang case.
  - Confidence: HIGH — 7 targets of ~90 counted is small enough to show both ways.
  - Blind spot: Other implicit rule changes made during labeling (e.g. hashtags of companies) are not separated out.
- **Fix B**: Sample and label a fresh holdout day for v2.3.
  - Strength: Clean, uncontaminated measure.
  - Tradeoff: Another sampling run and ~30 min of labeling before phase 6.
  - Confidence: MED — one extra day (~60 posts) gives few slang cases.
  - Blind spot: The holdout may contain too few market-slang posts to say anything.
- **Decision**: FIXED via Fix A — queued for the phase 6 report (follow-ups/review-fixes.md)

### F5 — Production Wykop connector ahead of S-02

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Scope Discipline
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/sources/wykop/WykopApiClient.java, WykopRepository.java
- **Detail**: The plan lists "pobieranie z Wykopu w pipeline" under What We're NOT Doing and put only the mapper in `src/main`. The connector was added at the user's request (N9); nothing references it outside the package (no bean, route or schedule), so the deploy does not change behaviour. Also `LiveSmokeTest` now maps through `WykopMapper` (harmless, not in the notes).
- **Fix**: Keep as decided; mention it in the PR description and in the S-02 issue so S-02 builds on it rather than re-planning it.
- **Decision**: FIXED — note for the PR description and the S-02 issue queued (follow-ups/review-fixes.md)

### F6 — `WykopApiException.status` uses sentinels instead of an enum

- **Severity**: 💬 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/sources/wykop/WykopApiException.java; WykopApiClient.java:142-146
- **Detail**: `status` 0 = no answer, 200 = malformed answer / page limit; `llm/LlmCallException` uses the `CallFailure` enum and lessons.md asks for enums for closed sets. `parse()` drops the Jackson cause.
- **Fix**: Add `WykopFailure` (TRANSPORT, HTTP_STATUS, MALFORMED_ANSWER, PAGE_LIMIT, INTERRUPTED) to the exception, keep `status` for HTTP only, pass the cause.
- **Decision**: FIXED — `WykopFailure` enum in `WykopApiException`, status only for HTTP, cause kept

### F7 — Metric definitions the plan words two ways

- **Severity**: 💬 OBSERVATION
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Plan Adherence
- **Location**: szczur-radar-api/src/test/java/pl/szczurradar/api/measurement/GoldMetrics.java; plan.md Desired End State and phase 6 vs phase 5
- **Detail**: Desired End State / phase 6 say "kierunek na dopasowanych celach ze stanowiskiem"; phase 5 says "niedopasowany = błąd" — the code follows phase 5 (unmatched gold targets count as wrong). Contradiction rate counts unmatched targets as non-contradictions, so a model with no targets passes that one threshold (it fails recall/precision, tested). Dominant direction counts only posts where gold has one.
- **Fix**: Keep the phase 5 definitions (stricter) and state them in the phase 6 report's metric legend and in `measurement/README.md`, so 6.3 means the same everywhere.
  - Strength: No code change; the report reader sees exactly what each number divides by.
  - Tradeoff: The plan text keeps the looser wording for "kierunek".
  - Confidence: HIGH — definitions are tested on hand-counted data.
  - Blind spot: The user has not chosen between the two wordings explicitly.
- **Decision**: FIXED — strict definition kept (user: unmatched = wrong); definitions documented in measurement/README.md; legend queued for the report

### F8 — Quasi-identifiers left in Wykop fixtures

- **Severity**: 💬 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/test/resources/fixtures/wykop/link.json:11, search-entries-page.json, search-links-page.json
- **Detail**: Real `type_id` (8022621), users' `rank.position` and `gender` remain; nicks, URLs, ids and media keys are anonymized.
- **Fix**: Null `rank.position` and `gender`, renumber `type_id` in the four fixtures.
- **Decision**: FIXED — gender and rank.position nulled, type_id renumbered in the Wykop fixtures

### F9 — Sheet accepts any URL scheme from the links file

- **Severity**: 💬 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: context/changes/assessment-contract-harness/pilot/labeling-gold.html:200
- **Detail**: `a.href = LINKS[id]` would accept `javascript:` from a locally loaded JSON (user-controlled, so low risk); post text itself goes through `textContent` (no XSS).
- **Fix**: Render the link only when it starts with `https://wykop.pl/`.
- **Decision**: DISMISSED — the links feature is temporary; removal before merge queued (follow-ups/review-fixes.md)

### F10 — Small robustness and test gaps

- **Severity**: 💬 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: GoldLabels.java (`Label`), GoldDataTest.java:76, WykopApiClient.java:71-89, WykopRepositoryTest/WykopApiClientTest
- **Detail**: A null `note` gives an NPE instead of a clear failure; concurrent 401s each refresh the token (Javadoc says thread-safe); no tests for `MAX_PAGES`, invalid JSON, non-array `data`.
- **Fix**: `Objects.requireNonNull(note)` in `Label`; refresh only when the token is still the stale one; three small tests.
- **Decision**: FIXED — `Label.note` required, token refreshed once for concurrent 401s, tests for page limit, invalid JSON, non-list data, transport error
