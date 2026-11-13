<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: S-31: ręczne weryfikacje PR env jako scenariusze w CI

- **Plan**: context/changes/pr-env-scenarios-in-ci/plan.md
- **Scope**: Full plan (phase 4 included explicitly, although its Manual row 4.8 is pending)
- **Reviewed phases**: 1, 2, 3, 4
- **Date**: 2026-11-13
- **Verdict**: APPROVED
- **Findings**: 0 critical, 2 warnings, 3 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | PASS |

## Evidence

- **Plan adherence:** every planned item is MATCH, including all assertions of scenarios A–H, the quiet check after each scenario, the 8 items of the manual list and the 7 elements of the S-31 section of `deploy-plan.md`.
- **Harmless deviations:**
  - `context/foundation/roadmap.md` (S-31 → `in-progress`) is not listed in the plan, but Phase 1 #7 implies it.
  - Breakage #2 went into `findRangesEndingSince`, which `findCoveringSince` calls; the change is documented, and only G went red.
  - Before each scenario only the list of unmatched LLM requests is cleared, not the server's whole request log; nothing reads that log.
- **Automated checks:**
  - 1.2: 33 files. 1.3: no key in the recordings. 1.6: SVG current. 1.7: PR #171 with `Refs #141`.
  - 2.4: in `src/main` only the `CLAUDE.md` files changed. 3.3: `PrEnvScenariosIT` ×2 in `pipeline/CLAUDE.md`. 4.4: `timeout-minutes: 15`. 4.7: the three documents mention `PrEnvScenariosIT` (1/10/1).
  - 4.1: seeds 1–5 green.
  - 4.2: alphabetical gives 1130 unit tests and 396 IT, BUILD SUCCESS (PrEnvScenariosIT 6.98 s); reversealphabetical is green as well (11.83 s).
  - 4.5: CI on HEAD `e10ee06` is green on every job.
  - 4.6: in CI, `Tests run: 8 … Time elapsed: 7.286 s` against a 60 s budget.
  - A locally run IT needs `LLM_*` in the shell. Without them all 396 IT fail on context load (`InvalidSettingsException`). The run used test values for `LLM_*`, with a fake key.
- **Manual:** 4.8 (owner accepts the manual list in `verification-scope.md` and the rule in `REPOSITORY_GUIDELINES.md`) is pending. It is a human decision, not work for an agent.
- **Checked and fine:**
  - The recordings hold no secrets or account fields.
  - The negative checks in F and H are not vacuous: `runEnded` runs before `own.remove`.
  - Every wait is bounded (60 s, `@Timeout` 5 min).
  - The virtual clock cannot spin.
  - TRUNCATE covers all 7 run and post tables.
  - The context is not shared with other classes.
  - `ScenarioDaysRecorder` does not run in CI.

## Findings

### F1 — Static state of the fakes is not fully cleared between scenarios

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/test/java/pl/szczurradar/api/alerts/LocalNtfy.java:145
- **Detail**:
  - `LocalNtfy.clear()` resets the server and `REPORTED`, but not the virtual clock `NOW` (`:61`). Only `awaitLimit` moves the clock, so in practice `REPEAT_WINDOW` (10 min) never runs out for the whole class, and the singleton queue remembers `seen` across scenarios.
  - Today every event carries a unique run id, so nothing is lost.
  - A future scenario with an event without a run (`ORPHAN_CLEANUP_FAILED`, `RECOVERY_FAILED`) or a second `NIGHT_SUMMARY` would get `not sent: a repeat` only when it runs after another such scenario. Under `MethodOrderer.Random` that depends on the seed.
  - Related, and harmless: `LocalWykop.reset()` does not clear `SERVER.requests()`, so the list grows across the JVM, unlike `LocalReddit.reset()`.
- **Fix**: In `LocalNtfy.clear()` move `NOW` past both windows (`NOW.updateAndGet(t -> t.plus(OperatorAlertQueue.REPEAT_WINDOW).plusSeconds(61))`); in `LocalWykop.reset()` clear the server's requests.
- **Decision**: FIXED: `LocalNtfy.clear()` moves `NOW` by `REPEAT_WINDOW + RATE_WINDOW`; new `LocalApiServer.clear()`, called by `LocalWykop.reset()`.

### F2 — Rejected recordings are checked only by count

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/test/java/pl/szczurradar/api/scenario/PrEnvScenariosIT.java:135
- **Detail**:
  - `REJECTED = Set.of("reddit-1xgrbst.json", "reddit-1xgnw3d.json")` is used only as `REJECTED.size()` (`:138`, `:141`, `:248`, `:313`).
  - After a re-recording in which a different 2 posts fail, the test stays green while the set, the class Javadoc and the README verdict table in `scenario-days/` say something else. That is the very drift `RecordedDaysTest` is meant to catch for files.
- **Fix**: In scenario A, assert that the `source_item_id` values of the `failed` posts equal the ids from `REJECTED`.
- **Decision**: FIXED: scenario A compares the `source_item_id` values of the `failed` posts with the posts of the `REJECTED` recordings (via `RecordedDays.posts()`).

### F3 — The `quality 7d` lines in scenario H are always empty (two clocks)

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/test/java/pl/szczurradar/api/scenario/PrEnvScenariosIT.java:116
- **Detail**:
  - `NightSummary` builds the 7-day quality window from the `@Primary` `MutableClock` (2026-10-29), while `Assessor` stamps `assessed_at` with `Clock.systemUTC()`.
  - The quality window therefore always sees 0 attempts, and `ASSESSMENT_QUALITY` cannot be exercised in this class.
  - The plan excludes these assertions ("Zależą od zegara JVM"), but the class Javadoc does not say why. The next person might add a quality assertion here and get a green test that proves nothing.
- **Fix**: Add one sentence to the class Javadoc: the quality lines are always empty here because of the two clocks, and they are covered by `NightSummaryIT`.
- **Decision**: FIXED: the sentence about the two clocks is in the "Clock" item of the class Javadoc.

### F4 — `taken()` counts the `taken` lines of every context in the JVM

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/test/java/pl/szczurradar/api/scenario/PrEnvScenariosIT.java:646
- **Detail**:
  - `OutputCaptureExtension` captures the stdout of the whole JVM, including the threads of other cached contexts (their `RunWatchdog` and queues).
  - A stray `Operator alert … taken` line from another context would give `taken > reported`, and `awaitQuiet` would fail after 60 s. That is a red test, not a vacuous one, and it is unlikely: other contexts are idle.
  - The `" ERROR "` scan (`:290`) has the same exposure, as in `PipelineRunIT`.
- **Fix**: Count only the `taken` lines that name this scenario's run ids or say `no run`.
- **Decision**: SKIPPED: Failsafe runs the classes one after another, so the other contexts are idle; if a stray line appeared, the test would go red rather than falsely green.

### F5 — Relative path to the recordings and a silent skip of missing files

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/test/java/pl/szczurradar/api/scenario/RecordedProvider.java:56
- **Detail**:
  - `RecordedDays` and `RecordedProvider` read `Path.of("src/test/resources/...")`, which works only with `szczur-radar-api/` as the working directory (Maven; an IDE run may differ).
  - `RecordedProvider` skips missing files without a word, so every call ends with 400. `settle()` then reports "requests without a recording" instead of "directory not found".
- **Fix**: In `RecordedProvider` (and `RecordedDays`), fail with a clear message when the directory `DIR` does not exist.
- **Decision**: FIXED: the `RecordedProvider` constructor throws `IllegalStateException` with the absolute path when `RecordedDays.DIR` does not exist; a single missing file is still reported by `RecordedDaysTest`.
