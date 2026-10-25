<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: S-18: błędy wymagające reakcji docierają do operatora

- **Plan**: context/changes/error-visibility/plan.md
- **Scope**: Full plan
- **Reviewed phases**: 1, 2, 3, 4
- **Date**: 2026-10-25
- **Verdict**: NEEDS ATTENTION
- **Findings**: 0 critical, 2 warnings, 6 observations

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

- Plan drift: every planned change is in place (MATCH). Minor deviations, all documented:
  - `onClose` raises `RUN_CLOSE_FAILED` only when the instance had runs (`pipeline/CLAUDE.md`).
  - The 401 and 5xx cases in `DeepSeekClientTest` assert `HTTP 401` / `HTTP 503` instead of a cause class. `HttpStatusErrors` attaches no cause.
  - The PRD clarification sits after S-09 (`prd.md:265`).
- Extras, all harmless:
  - the static `OperatorAlertQueue.raise(ObjectProvider, …)`;
  - test seams `Sleeper` / `drainTimeout`;
  - INFO/WARN lines for repeats and for events dropped at shutdown;
  - the roadmap and SVG, required by the repository guidelines.
- Nothing contradicts "What We're NOT Doing": no appender, no new env vars or migrations, `DeploySafetyController` untouched.
- Success criteria:
  - unit tests of the changed packages: 745 tests, 0 failures (local run, 2026-10-25);
  - `./mvnw -B verify` green in CI for code head `08c804d` (11/11 checks; `b615ce3` touches only plan.md);
  - 4.2 evidence in the #77 comment;
  - manual 4.4 and 4.5 confirmed by the operator (Telegram down+up for `szczur-radar-pr-107/api-events`; checks deleted 00:07 UTC).

## Findings

### F1 — Uncleaned run error goes to healthchecks.io/Telegram

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/notifications/RunNotifications.java:109, pipeline/assess/ConcurrentAssessmentDispatcher.java:157
- **Detail**:
  - The `RUN_NEEDS_ATTENTION` detail includes `run.getError()`, cut to 300 characters. The plan assumed this text "is already cleaned of the key". That holds for the LLM clients' own exceptions.
  - It does not hold for an LLM check error from outside the clients: the dispatcher at `:157` stores `"the LLM check failed: " + ex.getMessage()` raw.
  - `LlmCheckController:52-59` cleans exactly this case with `ProviderMessages`. Its comment says such a message "may come from outside the clients (e.g. building one)".
  - The text now reaches a third party (healthchecks.io stores ping bodies) and Telegram, not only `/admin`.
  - Other run errors (`PipelineRunService:325`, `Class: message` of any failure) may carry SQL or post content, but not a key.
- **Fix A ⭐ Recommended**: clean the message at the source (`ConcurrentAssessmentDispatcher:157`) with `ProviderMessages` (the API key from `LlmProviderProperties`), as `LlmCheckController` does.
  - Strength: fixes `/admin` and the alert at once; the same pattern as `LlmCheckController`; one line plus a constructor argument and a test.
  - Tradeoff: the dispatcher gets a dependency on the LLM settings.
  - Confidence: HIGH — the pattern already works in `LlmCheckController`.
  - Blind spot: does not limit other `Class: message` errors (SQL/content); acceptable, they are not secrets.
- **Fix B**: put only the first segment of the error in the alert (up to `:`, i.e. the class or prefix) and leave the full text to `/admin`.
  - Strength: no message from outside goes to a third party.
  - Tradeoff: the alert says less (e.g. it loses "no posts for 2004-01-01"), so the operator has to open `/admin` more often.
  - Confidence: MED — the useful content of the error is lost.
  - Blind spot: the raw text in `/admin` and the log stays.
- **Decision**: FIXED (Fix A): `ConcurrentAssessmentDispatcher` cleans the failed check's message with `ProviderMessages` (key from `LlmProviderProperties`); test `aCheckFailingOutsideTheClientsLeavesTheKeyOutOfTheRunsErrorAndTheLog`, red without the cleaning

### F2 — Report cut off at shutdown leaves the check down

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/alerts/OperatorAlertQueue.java:241
- **Detail**:
  - `destroy()` calls `shutdownNow()` after 5 s, which interrupts a report in flight (each request may take up to 10 s).
  - If `/fail` went out and the success ping did not, the `api-events` check stays down.
  - healthchecks.io alerts only on a state change, so the next event after the restart gives no message.
  - It is unlikely (it needs a request slower than 5 s while the app is stopping), but the effect is silent.
- **Fix**: add to the S-18 section of `deploy-plan.md` that a check stuck in "down" without a new event is re-armed with `send-test-alert` (fail + success), and that "no message" can mean exactly that.
- **Decision**: FIXED: `deploy-plan.md`, S-18 "No message does not mean all is well": an event cut off at shutdown, re-arming with `send-test-alert`

### F3 — Raw exception in the LLM check logs

- **Severity**: 🔍 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/assess/ConcurrentAssessmentDispatcher.java:156, llm/api/LlmCheckController.java:56
- **Detail**:
  - `log.error(…, ex)` prints the full chain of an exception that did not come from a client (e.g. one thrown while building the client).
  - The clients' own exceptions are clean since phase 3. These lines predate S-18.
  - B7 covered "exceptions from the LLM clients".
- **Fix**: log `messages.cleanCause(ex)` instead of `ex` at both sites (together with F1, Fix A).
- **Decision**: FIXED: both log lines (`ConcurrentAssessmentDispatcher`, `LlmCheckController`) log `messages.cleanCause(ex)`; the dispatcher test checks the log

### F4 — `seen` keeps keys with repeats forever

- **Severity**: 🔍 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/alerts/OperatorAlertQueue.java:282-284
- **Detail**:
  - `forgetExpired` keeps a key with `repeats > 0` until the same key comes back.
  - Most keys carry a run id (e.g. `WATCHDOG_FAILED`), so they do not come back. The entry stays for the whole uptime, and the promised "N repeats were not sent" note never goes out.
  - The growth is tiny, so this is not a real leak.
- **Fix**: also forget a key with repeats after 2 × `REPEAT_WINDOW`, or document in the Javadoc that the repeat count may never be reported.
- **Decision**: SKIPPED

### F5 — Watchdog alert built inside `catch (Throwable)` without protection

- **Severity**: 🔍 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/RunWatchdog.java:97-104
- **Detail**:
  - This catch exists so that nothing reaches `scheduleWithFixedDelay`, which would cancel every later check.
  - Building the detail (concatenation, `Durations.format`) after an `OutOfMemoryError` can throw again, and that error would escape the catch.
  - The ERROR log line before it has the same risk; the alert adds a second point.
- **Fix**: wrap `alerts.raise(...)` in `try { … } catch (Throwable ignored) { }`.
- **Decision**: SKIPPED

### F6 — Rate limit and the queue at the edges of the lifecycle

- **Severity**: 🔍 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/alerts/OperatorAlertQueue.java:270-277, :209-220
- **Detail**:
  1. `awaitLimit` sleeps until `sent.first + 1 min` by the wall clock. A clock jump backwards prolongs the sleep by the size of the jump.
  2. Not verified: after the context is destroyed, `ObjectProvider.getIfAvailable()` in the static `raise` may create the queue again, with a non-daemon thread. This has no effect in production (the JVM stops); at most it leaves a stray thread in the test JVM.
- **Fix**: cap a single sleep at `RATE_WINDOW`. Point 2 needs no change unless stray threads show up in tests.
- **Decision**: FIXED (point 1): `awaitLimit` drops reports "started" after now, so a jump back never holds the queue longer than the window; test `aClockGoingBackDoesNotHoldTheNextEvent`, red without the fix. Point 2: no change

### F7 — Lost counts not reported on the interrupt path

- **Severity**: 🔍 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/assess/ConcurrentAssessmentDispatcher.java:205
- **Detail**:
  - `lost.report(runId)` runs only on the normal path.
  - It does not run when `await` is interrupted, or when a task counts after the watchdog's `cancel`.
  - In both cases the run ends `failed`/`interrupted` and gives a notification and `RUN_NEEDS_ATTENTION`, so the operator learns of it anyway.
- **Fix**: no change; accept as a deliberate limit (the plan promises "one per run" only for a dispatch that ended normally).
- **Decision**: ACCEPTED: the run ends `failed`/`interrupted` and gives `RUN_NEEDS_ATTENTION` anyway

### F8 — Two `cleanCause` sites without a test

- **Severity**: 🔍 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Success Criteria
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/llm/deepseek/DeepSeekClient.java:143, llm/openrouter/OpenRouterClient.java:201
- **Detail**:
  - `serverCutOff(observed, cleanCause(ex))` in DeepSeek and `failedAnswer(…, cleanCause(ex))` in OpenRouter (an error inside a 200 response) apply the cleaning, but no test covers them.
  - The tests cover 401, 5xx, the error without a status and the timeout.
  - Reverting either site would leave the tests green.
- **Fix**: add one case per site to the existing `noFailureLeavingTheClientCarriesTheKeyInItsCauses` tests.
- **Decision**: FIXED: `DeepSeekClientTest.aServerCutOffTheSdkFailsOnCarriesNoKeyInItsCauses`, `OpenRouterClientTest.anErrorInA200AnswerTheSdkFailsOnCarriesNoKeyInItsCauses`; each red without its `cleanCause`
