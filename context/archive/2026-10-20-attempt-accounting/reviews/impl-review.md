<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Awaria dostawcy LLM ani błąd naszego kodu nie zabija postów (S-09)

- **Plan**: context/changes/attempt-accounting/plan.md
- **Scope**: Full plan
- **Reviewed phases**: 1, 2, 3, 4, 5
- **Date**: 2026-10-23
- **Verdict**: NEEDS ATTENTION
- **Findings**: 0 critical, 3 warnings, 4 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | WARNING |
| Success Criteria | PASS |

## Evidence

- **Diff:** `b7c9bea..e0cb6a3`, 72 files.
- **Changes against the plan:** every planned change is MATCH. Files outside the plan are justified:
  - Javadoc in `RunFlow`;
  - Javadoc in `AssessmentSelection`, a known discrepancy R1;
  - `MigrationsIT`, which gains V10 and its CHECK;
  - `szczur-radar-api/README.md`, R2;
  - `LlmOutageIT`, the "IT with a real transport" from phase 3 #4.
- **"What We're NOT Doing" respected:**
  - thresholds are constants;
  - no column in `pipeline_runs`;
  - the `LlmCheck` shape is unchanged;
  - no historical data migration.
- **Plan text slightly overstated:** the stubs in `PostDetailView.test.ts`, `FeedView.test.ts` and `pipeline.test.ts` build no `FailedPost` and needed no change.
- **Automated gates, run locally on 2026-10-23:**
  - `./mvnw -B verify` passes (709 unit tests and 229 IT, 0 failures);
  - `npm test` passes (400/400);
  - `npm run build` passes;
  - `tools/db-backup/test.sh` passes;
  - the grep for 5.1 finds nothing, and `S-09` is in `prd.md:263`;
  - `flexFailingWithMixedErrorsFallsBack` exists and the old test does not.
- **CI for `e0cb6a3`:** every job is green, including `railway config`.
- **Manual steps:** all 7 are checked, with evidence in `reviews/` or in Progress (3.5 and 4.6 with the operator's confirmation).

## Findings

### F1 — LlmCheckController returns an uncleaned message and logs nothing

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/llm/api/LlmCheckController.java:48-53
- **Detail**:
  - The new `catch (RuntimeException ex)` returns the bare `ex.getMessage()` to the admin's browser.
  - For an `IllegalStateException` from the clients (`TransportException.unclassified`) the message is already cleaned. Any other exception from `check()` is not cleaned, for example a `BeanCreationException` when the `@Lazy LlmClient` is resolved (SDK builders with `apiKey`). Before the change such an exception ended in Spring's 500, which hides the message.
  - The catch also writes no log, so the stack trace of a real bug is lost.
  - A practical key leak is unlikely today, but the "without the key" guarantee depends on the callers instead of the controller.
- **Fix**: pass the message through `new ProviderMessages(properties.apiKey()).clean(...)` and add `log.error(..., ex)`. Add a case to `LlmCheckControllerIT`: an exception with the key in its message gives `<hidden>`.
- **Decision**: FIXED — `ProviderMessages(properties.apiKey()).clean(...)` and `log.error`; `LlmCheckControllerIT.anErrorThatIsNotTheProvidersNeverShowsTheKey` (break-check: red without the cleaning); `llm/CLAUDE.md`, `deploy-plan.md` S-09

### F2 — Watchdog can stop the run before the breaker at PIPELINE_LLM_CONCURRENCY ≤ 2

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/assess/ConcurrentAssessmentDispatcher.java:325-326 (`count` skips `Unavailable`); pipeline/state/RunRecorder.java:101-107
- **Detail**:
  - During an outage `last_activity_at` does not move until the step ends.
  - The stall timeout is `maxCallDuration` + 5 min; on production that is 39 min. One call made entirely of timeouts takes ≈30 min on production flex and ≈6 min on default.
  - **Concurrency ≥ 3:** the three failures finish within about one call, so the breaker wins.
  - **Concurrency 2:** on production (~60 min) the watchdog wins.
  - **Concurrency 1:** the watchdog wins in both configurations.
  - **Result:** safe. The run is `failed`, no attempts are used and no technical failures are counted. But the error is "no progress for 39m" instead of "the LLM provider is unavailable…", so the S-09 runbook diagnosis does not fit.
  - Production uses the default concurrency of 4, so this is not hit today.
  - Known as U-e (`reviews/docs-consistency-s09.md`) and not decided until now.
- **Fix A ⭐ Recommended**: document it in the S-09 section of `deploy-plan.md` and in `pipeline/CLAUDE.md` (the breaker needs concurrency ≥ 3 with timeouts; below that a "no progress" error during an outage means the same thing).
  - Strength: no new mechanism; the outcome is already safe, and production has 4 (the lesson "Uzasadniaj zakres celem użytkownika").
  - Tradeoff: the error message depends on the configuration.
  - Confidence: HIGH — the timings come from `TransportRetry.java:69-73` and `PipelineStallSettings.java:69-70`.
  - Blind spot: an operator who lowers the concurrency may not read the runbook.
- **Fix B**: `Unavailable` touches `last_activity_at` (a heartbeat in `RunRecorder` without changing counters).
  - Strength: the breaker wins at every concurrency, and the message is always "unavailable".
  - Tradeoff: new code in the dispatcher and recorder, a new test, and a run kept alive longer during an outage (at concurrency 1 on production ~90 min instead of 39).
  - Confidence: MED — the watchdog's interaction with the heartbeat needs a test.
  - Blind spot: a longer run can collide with the 02:00 UTC backup.
- **Decision**: FIXED via Fix B — `RunRecorder.touch` (also used by `endStep`), dispatcher `count` touches on `Unavailable`; `RunWatchdogIT` Order 5 (3 outages × 3 s at concurrency 1, timeout 5 s: the breaker ends the run) and `touch` verified in the dispatcher's outage tests (break-check: both red without the touch); `pipeline/CLAUDE.md`, `deploy-plan.md` (:468 and S-09)

### F3 — Javadoc `Post.technicalFailures` leaves out a single provider outage

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/posts/Post.java:95-99
- **Detail**:
  - The Javadoc says "Errors of our code", but the counter also grows for a single provider outage (`countTechnicalFailure`, phase 3).
  - The V10 header comment has the same gap, but V10 is already in the PR env database. Editing it would change the Flyway checksum and break the rule "never edit a deployed migration". The same decision as for U-a: correct only the Java documentation.
- **Fix**: Javadoc: "Technical failures in a row (an error of our code, or a single provider outage after retries; no stored assessment, no attempt) …". Leave V10 unchanged.
- **Decision**: FIXED — Javadoc `Post.technicalFailures`; V10 unchanged (already migrated in the PR env)

### F4 — An unknown error from `llm.check()` before the run loses the "the LLM check failed" prefix

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/assess/ConcurrentAssessmentDispatcher.java:142-147
- **Detail**:
  - `dispatch` catches only `LlmCallException`.
  - Before phase 1, an unknown SDK error was `REJECTED` and produced "the LLM check failed: …".
  - It is now an `IllegalStateException` that leaves `dispatch`. The run still ends `failed` (in `PipelineRunService.execute`), the message is cleaned and no post has been taken. But `pipeline_runs.error` lacks the "LLM check failed" context, and there is no "no post is assessed" log.
- **Fix**: `catch (RuntimeException ex)` next to `LlmCallException`, with the same `AssessedBatch` and log.
- **Decision**: FIXED — `catch (RuntimeException)` around `llm.check()`; `ConcurrentAssessmentDispatcherTest.aCheckFailingWithAnErrorThatIsNotTheProvidersTakesNoPostAndStopsTheStep` (break-check: red with only `LlmCallException`); `pipeline/CLAUDE.md`

### F5 — `putBack = true` is set before `release` returns

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/assess/ConcurrentAssessmentDispatcher.java:276-278
- **Detail**:
  - `putBack = true; return new Outcome.Unavailable(..., transitions.release(postId, runId));` sets the flag before `release` runs.
  - When `release` throws (a database error), `finally` no longer releases the post, so it stays `assessing` until `close → releaseAll`. The task error is counted at once as a processing error, even when the step later stops on the streak, which breaks the deferred-counting rule.
  - This is an edge case of a database failure.
- **Fix**: `boolean released = transitions.release(postId, runId); putBack = true; return new Outcome.Unavailable(..., released);`.
- **Decision**: FIXED — `putBack` set after `release` returns; `ConcurrentAssessmentDispatcherTest.anOutagesPostIsPutBackEvenWhenTheFirstWriteFails` (break-check: red with the old order)

### F6 — No API test for a `failed` post on hold

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Success Criteria
- **Location**: szczur-radar-api/src/test/java/pl/szczurradar/api/posts/api/PostsApiIT.java
- **Detail**:
  - `PostsApiIT` covers four cases: a `stored` post on hold, a `failed` post with 1 technical failure, a post below the limit (`assessed`), and a `dead` post with 3 (`onHold = false`).
  - No case pins `onHold = true` for `stage = failed` with 3 technical failures, the second way onto the list that the `FailedPost` Javadoc describes.
- **Fix**: add a case for `failed` + `technical_failures = 3` → `onHold = true`, `lastAttempt` and `lastTechnicalError` both set.
- **Decision**: FIXED — `PostsApiIT.aFailedPostOnHoldHasBothItsLastAttemptAndItsTechnicalError` (break-check: red when `onHold` is limited to `stored`)

### F7 — No breaker for a series of unclassified errors

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Architecture
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/assess/ConcurrentAssessmentDispatcher.java:280-289
- **Detail**:
  - A systematic error that every call returns, such as an `IllegalStateException` from `unclassified` after an SDK change, goes through all the posts of a run.
  - Each post gains one technical failure per run and goes on hold after three nights. This is better than before (`dead`), and the plan bounds it with the post limit.
  - A run-level breaker for such errors is outside the plan's goal.
- **Fix**: no change in S-09. Add a note to the follow-ups of S-17 (`pipeline-without-embabel`), which takes over the attempt semantics.
- **Decision**: FIXED — no code change; note for S-17 in `follow-ups/review-fixes.md`

## Triage (2026-10-23)

| Finding | Decision |
|---------|----------|
| F1 | Fixed |
| F2 | Fixed via Fix B (heartbeat) |
| F3 | Fixed (Javadoc only) |
| F4 | Fixed |
| F5 | Fixed |
| F6 | Fixed |
| F7 | Fixed: S-17 note |
