<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Jakość ocen per źródło — tezy w innym języku niż post (S-36)

- **Plan**: context/changes/statement-language-quality/plan.md
- **Scope**: Full plan
- **Reviewed phases**: 1, 2, 3, 4, 5
- **Date**: 2026-11-13
- **Verdict**: APPROVED
- **Findings**: 0 critical, 1 warning, 5 observations

Phase 5 was reviewed explicitly although its Manual rows 5.5 and 5.6 are pending: its Automated rows are done and the two Manual rows are owner decisions (format of the `language 7d` line and the `ASSESSMENT_QUALITY` text; words and look of the language row, including „za mało danych”), not checks an agent can run.

## Success criteria re-run (2026-11-13, HEAD ba183ef)

| Check | Result |
|-------|--------|
| `./mvnw -B verify` (with `~/.config/szczur-radar/llm.env`) | PASS — 1148 unit, 395 integration, 0 failures |
| `npm test` | PASS — 30 files, 767 tests |
| `npm run build` | PASS |
| `tools/pipeline-trigger/test.sh` | PASS |
| `tools/db-backup/test.sh` | PASS |
| `python3 tools/render_roadmap_svg.py` | PASS — exit 0, SVG unchanged against HEAD |
| `gh pr checks 169` | PASS — all 13 checks SUCCESS on ba183ef |
| PR env (5.2–5.4) | Evidence in `evidence/pr-env/README.md` and screenshots; summary comment on issue #146 |

The first local `verify` run failed in every IT at context load because the shell had no `LLM_*` settings (`InvalidSettingsException`). That was the environment, not the code. The run with `llm.env` sourced was green.

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS (1 observation: F6) |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | PASS (1 observation: F2) |
| Success Criteria | PASS |

## Findings

### F1 — Isolation tests do not abort a Postgres transaction

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/api/InternalPipelineRunIT.java:558, szczur-radar-api/src/test/java/pl/szczurradar/api/posts/api/AssessmentQualityControllerIT.java:159
- **Detail**: The plan's Critical Implementation Detail is that a query error aborts the whole Postgres transaction, so the theses read must run in its own transaction. Both failure tests use `doThrow(new QueryTimeoutException(...))` on the `@MockitoSpyBean AssessmentRepository`. The exception is thrown in Java before any SQL runs, so no transaction is ever aborted. Today's code is correct: `NightSummary.quality()` and `language()` each call `measureRead.execute`, and the controller reads outside any `@Transactional` (`open-in-view=false`). But a future change that puts both reads in one `execute` would keep these tests green.
- **Fix**: In `InternalPipelineRunIT.thesesThatCannotBeReadCostOnlyTheLanguageLines`, replace `doThrow` with a `doAnswer` that first runs a failing SQL statement on the current transaction's connection (e.g. `jdbc.execute("SELECT 1/0")`) and then rethrows. That aborts the real Postgres transaction, and the test then proves that the `quality 7d` lines and the failed-attempts alarm survive.
- **Decision**: FIXED. The spy now runs `SELECT 1 / 0`, asserts `current transaction is aborted`, and only then throws the same `QueryTimeoutException`.
  - `InternalPipelineRunIT`: 31/31 green.
  - Deliberate break: moving the language read inside the snapshot's transaction turns the test red (`current transaction is aborted`). The old `doThrow` version would have stayed green.
  - `AssessmentQualityControllerIT` was left unchanged: there the theses read is the route's last read, so an abort cannot reach anything after it.

### F2 — Copied read/fallback and "unavailable" code in NightSummary

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/NightSummary.java:195-235, :369-392
- **Detail**: `language()` copies `quality()`: the same try → `measureRead.execute` → catch → ERROR log → fallback, a parallel record (`NightLanguage` / `NightQuality`), and the window start recomputed as `date.minusDays(AssessmentQuality.WINDOW_DAYS - 1)`, which repeats `AssessmentQuality`'s own rule. The two `unavailable` blocks in the text differ only in the word `quality` / `language`. `AssessmentQuality` itself shares the threshold rule, the crossing rule and `nightBeforeUntil` between both measures, so the copying is only in the summary layer. Lesson: „Proaktywnie szukaj okazji do abstrakcji…”.
- **Fix**: Extract `unavailableLine(String measure, LocalDate from, LocalDate to, String cause)` and expose `AssessmentQuality.windowFrom(LocalDate to)`. Do it as a separate refactor commit with no behaviour change, pinned by `NightSummaryTest`.
- **Decision**: FIXED in b1189a7 (refactor, no behaviour change); F1's test is in 845613d.
  - `AssessmentQuality.windowFrom(to)` is used by both windows and both fallbacks of `NightSummary`.
  - `NightSummary.measureHead` and `unavailableLine` build the head of every measure line and both `unavailable` lines.
  - The try/catch of `quality()` / `language()` stays as two methods, since each reads a different window type.
  - `NightSummaryTest`, `AssessmentQualityTest`, `NightSummaryIT`, `InternalPipelineRunIT`, `AssessmentQualityIT` and `AssessmentQualityControllerIT` are green.

### F3 — Rename of `not_enough_attempts` and the new fields are not compatible across an API/web deploy skew

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/posts/QualityStatus.java, szczur-radar-web/src/views/AdminView.vue:490
- **Detail**: The API and the web deploy as separate Railway services. In the minutes between the two deploys:
  - an old SPA on the new API shows the raw `not_enough_data` with a neutral badge;
  - a new SPA on the old API shows „nie udało się policzyć” for the language and „próg sprawdzany od undefined tez”.

  This only affects the admin's `/admin` section and lasts only for the deploy window. The S-36 runbook does not mention it.
- **Fix**: Accept it, and add one sentence about the deploy window to the S-36 section of `deploy-plan.md`.
- **Decision**: FIXED. Accepted; the S-36 section of `deploy-plan.md` (the `/admin` bullet) now describes the deploy window and says that a reload after both deploys fixes it.

### F4 — The theses query loads every statement into memory, twice a night

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/posts/AssessmentRepository.java:89-108, posts/AssessmentQuality.java (`languageWindow`, `languageCrossed`)
- **Detail**: Each summary reads two 7-day windows that overlap by about 6 days: roughly 14k strings and about 7k JSONB outputs to detoast. Each `/admin` load reads one window. The rows are held as `List<Object[]>` and as `List<PostStatement>`. The query uses `ix_assessments_assessed_at`. This is fine at today's volume (plan, Performance Considerations) and grows linearly with sources and subreddits. The runbook already checks the route's response time on production after the merge.
- **Fix**: No change now. If volume grows, classify per (source, Warsaw day) once and build both windows from those counts.
- **Decision**: FIXED differently in 3334a14 (owner chose "stream and counters").
  - Why not per-day counts: the night before's window ends at `min(start D, now − 24 h)`, which is not a day boundary, so per-day counts would not give the same numbers.
  - `findModelStatements` now returns a `Stream` (native query, Hibernate fetch size 500). `AssessmentQuality` counts it per source as it is read and closes it, so no list of texts is held.
  - `languageWindow` / `languageCrossed` are `@Transactional(readOnly = true)`: at night they join the summary's own transaction, and on `/admin` they open one (a streaming query needs it; `AssessmentQualityControllerIT` exercises that path).
  - The night still runs two queries; reading 8 days once was offered and not chosen.
  - New `AssessmentQualityTest.theThesesAreCountedAsTheyAreReadAndTheirStreamIsClosed`. Full `./mvnw -B verify` green: 1149 unit, 395 IT.

### F5 — LanguageEvidence does not normalise Unicode before matching

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/text/LanguageEvidence.java:28-31
- **Detail**: A decomposed Polish letter (e.g. `e` + U+0328) does not match `[ąćęłńóśźż]`, and `\p{L}+` splits the word at the combining mark, so the thesis loses its Polish evidence. Model output is normally NFC, so the case is unlikely, and the production evidence shows no sign of it.
- **Fix**: `Normalizer.normalize(text, Normalizer.Form.NFC)` at the start of `of()`, plus one case in `LanguageEvidenceTest`.
- **Decision**: FIXED in ce69a7d.
  - NFC normalisation at the start of `LanguageEvidence.of`, with the class javadoc updated.
  - New `LanguageEvidenceTest.aDecomposedPolishLetterIsPolishToo` (`"Kupię CDR"` → `PL`). It is green, and red with the normalisation removed.

### F6 — Small wording and process drifts from the plan

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: szczur-radar-web/src/views/AdminView.vue:181-195; context/changes/statement-language-quality/evidence/pr-env/README.md
- **Detail**:
  - At `not_enough_data` the row reads `tezy w innym języku niż post: 46 tez · próg 2%`: it has a colon and a Polish plural, and the threshold stays visible. The plan said "liczba tez bez udziału". The intent is the same, and `AdminView.test.ts` pins the wording.
  - `.quality-language` was added to two existing selector lists. It adds no declarations or literals.
  - The Phase 5 results are in `evidence/pr-env/README.md`, with a summary comment on issue #146; the plan said "w komentarzu issue".
  - Extras, both justified: the SQL also requires `jsonb_typeof(c->'statement') = 'string'` (covered by `AssessmentQualityIT`), and the `nightly-summary.json` fixture now carries the language line (it is the plan's `SUMMARY`).
- **Fix**: None needed. The owner's manual check 5.6 decides the row's words.
- **Decision**: FIXED differently in ff0f654 (owner chose "like the attempts' row").
  - Below `minTheses` the language row now shows its share like the failed attempts' row: `tezy w innym języku niż post 2,2% z 46 tez · próg 2%`, the badge `za mało danych`. Without a thesis it shows `…: 0 tez · próg 30%`. This consciously departs from the plan's "liczba tez bez udziału".
  - `AdminView.test.ts` is updated; `npm test` (767) and `npm run build` are green.
  - `few-light-1280.png` and `few-dark-375.png` were retaken on the mock, and the screenshot README updated.
  - The other points (CSS selectors, Phase 5 evidence in README plus the #146 comment, the two extras) need no change.

## Triage summary (2026-11-13)

| Finding | Decision | Commit |
|---------|----------|--------|
| F1 | Fixed | 845613d |
| F2 | Fixed | b1189a7 |
| F3 | Fixed (runbook sentence) | this review's commit |
| F4 | Fixed differently (stream and counters) | 3334a14 |
| F5 | Fixed | ce69a7d |
| F6 | Fixed differently (share also below the minimum) | ff0f654 |

Manual rows 5.5 and 5.6 of the plan stay with the owner.
- 5.5: the `language 7d` line and the `ASSESSMENT_QUALITY` text are unchanged by the triage.
- 5.6: the row's words now include the share below the minimum.
