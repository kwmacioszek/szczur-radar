<!-- PLAN-REVIEW-REPORT -->
# Plan Review: S-17: przebieg pipeline'u bez Embabel — Implementation Plan

- **Plan**: `context/changes/pipeline-without-embabel/plan.md`
- **Mode**: Deep
- **Date**: 2026-10-25
- **Verdict**: REVISE (before triage) → SOUND (all 7 findings fixed in the plan during triage)
- **Findings**: 1 critical, 5 warnings, 1 observation

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| End-State Alignment | FAIL (before fixes) → PASS |
| Lean Execution | PASS |
| Architectural Fitness | PASS |
| Blind Spots | WARNING (before fixes) → PASS |
| Plan Completeness | WARNING (before fixes) → PASS |

## Grounding

46/46 paths ✓, symbols and line anchors ✓ (one wrong: `infrastructure.md:52`, F5), brief↔plan ✓, Progress↔Phase ✓ (5/5 phases, 23/23 criteria). Verified against code by a sub-agent: interruption reaches the step (`await`, `WykopApiClient` never retries `INTERRUPTED`, `TransportRetry.sleep`); `TransportException.unclassified`'s `IllegalStateException` reaches the dispatcher's `catch (RuntimeException)` unwrapped (F7 feasible); removing Embabel breaks compilation only in the 7 `com.embabel` files and `LlmProviderConfigTest` — all in the plan; the console log pattern changes to Boot's default but no test assertion depends on the layout (` ERROR `/` WARN ` match under both). Offline `dependency:tree` confirms `openai-java-client-okhttp` comes only via `embabel-agent-openai` today and `spring-ai-openai` 2.0.1 depends on `openai-java-core`.

## Findings

### F1 — Zadania puli LLM przerwane przez `await` nie są oznaczone `cancelled`

- **Severity**: ❌ CRITICAL
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: End-State Alignment
- **Location**: Phase 1 §3 (`stopStalled`), §6 (dyspozytor)
- **Detail**: `await` on `InterruptedException` cancels the pool futures (`ConcurrentAssessmentDispatcher.java:397`) without setting the batch's `cancelled` flag — only `cancel(runId)` sets it (`:237`) — and `dispatch`'s `finally` (`:219`) removes the `dispatching` entry, so a later `dispatcher.cancel(runId)` finds nothing; `onClose` never calls it at all. Interrupted pool tasks then land in the ordinary branches: an outage (`:291`) or `Outcome.Error` + `releaseFailed` + an ERROR line (`:327`). The end state "INFO, not ERROR, after a stop" and the `RunWatchdogIT` Order 1 / scenario 7 and 10 assertions would be flaky.
- **Fix**: Phase 1 §6 stops being "documentation only": in `await`'s `InterruptedException` branch set the batch's `cancelled` flag first, then `cancel(true)` the futures; `cancel(runId)` stays as the second lever (the thread in `llm.check()` on OkHttp). New `ConcurrentAssessmentDispatcherTest` case: interrupted `await` → the task in flight ends `NotTaken`, no outage, no `releaseFailed`, no ERROR.
- **Decision**: FIXED

### F2 — `@BeforeEach reset()` ściga się z przebiegiem, który jeszcze się zamyka

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Blind Spots
- **Location**: Phase 4 §3–§4
- **Detail**: `finished()` (`PipelineAgentIT:788-804`) returns when `summarize` writes the final status — before `execute`'s `finally` (close, end line, notification INSERT, `own.remove`). Orders 1, 2, 3, 8, 9 and `RunWatchdogIT` 2, 5 end that way; Orders 7 and 15 release the mock latch in `finally` after their assertions. In random order the next `reset()` truncates `pipeline_runs` under a thread still writing; `own`/`dispatching` live in the singleton; late log lines land in the next method's `CapturedOutput`.
- **Fix A ⭐ Recommended**: `@AfterEach settle()` releases every held latch and waits until `service.ownRuns()` is empty and the dispatcher pool has no task; `reset()` asserts that quiet before truncating.
  - Strength: one context, one container, today's run time; `awaitClosed` already exists.
  - Tradeoff: every latch-holding scenario must release in `@AfterEach`, not in the method's `finally`.
  - Confidence: HIGH — `own`/`awaitClosed` are in the code.
  - Blind spot: pool tasks after `dispatching.remove` are invisible from outside; the pool must be idle too.
- **Fix B**: `@DirtiesContext(AFTER_EACH_TEST_METHOD)` instead of `reset()`.
  - Strength: full isolation per scenario.
  - Tradeoff: ~13 context starts with a Postgres container — minutes more in CI.
  - Confidence: MEDIUM — cost not measured.
  - Blind spot: many Testcontainers containers per run.
- **Decision**: FIXED (Fix A)

### F3 — `FutureTask` połyka `Throwable`, który dziś trafia do uncaught handlera

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Phase 1 §3 (`submit`)
- **Detail**: `FutureTask.run` stores every `Throwable` in a future nobody reads, so an `Error` or an exception from `execute`'s `finally` vanishes; today it reaches the worker thread's uncaught handler (stderr / Railway). The plan also narrows `catch (Exception)` to `catch (RuntimeException)`.
- **Fix**: the task's Runnable (`guarded(runId)`) wraps `execute` in `catch (Throwable)` with an ERROR line and a rethrow; a `PipelineRunServiceTest` case.
- **Decision**: FIXED

### F4 — Flaga przerwania obsłużona tylko w jednej gałęzi `catch`

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Phase 1 §3 (`execute`), §2 (`RunRecorder.step`)
- **Detail**: `Thread.interrupted()` and `executor.isShutdown()` were only in `catch (RuntimeException)`; the `RunNoLongerRunningException` branch (reachable with the flag set: `onClose` writes `interrupt` before `shutdownNow`; `cancel(true)` just before `openStep`) went to `finally` → `close` + `runEnded` on an interrupted thread without the shutdown check. `RunRecorder.step`'s `catch` calls `endStep` (`RunRecorder.java:165-167`) on the interrupted thread; a failing `endStep` would replace the original exception.
- **Fix**: one `catch (RuntimeException)` that first clears the flag and reads `executor.isShutdown()`, then branches on `instanceof RunNoLongerRunningException`; `step` wraps `endStep` so its failure is `addSuppressed`.
- **Decision**: FIXED

### F5 — Kryterium 5.1 wskazuje `infrastructure.md:52`, gdzie Embabel nie ma

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 5 — Success Criteria 5.1, Progress 5.1
- **Detail**: `grep -in embabel context/foundation/infrastructure.md` hits only `:9` and `:151`, both rewritten by the plan; `:52` is "Historical (2026-09-26, Python backend)…" without Embabel. After the edits the grep returns nothing and the criterion "hits only `:52`" fails.
- **Fix**: criterion 5.1 → the grep over all five current documents (infrastructure.md included) returns nothing.
- **Decision**: FIXED

### F6 — „Czyści tabele w porządku kluczy obcych” działa tylko jednym TRUNCATE

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 4 §3 (`reset()`)
- **Detail**: the six-table list is complete and FK-closed (11 tables in the migrations; `users`, `activation_tokens`, `spring_session*`, `flyway_schema_history` must stay), but Postgres refuses `TRUNCATE` of a referenced table unless every referencing table is in the same statement — six separate statements fail at `posts`.
- **Fix**: one `TRUNCATE notifications, pipeline_run_steps, assessments, posts, raw_payloads, pipeline_runs` and the list of untouched tables, written into the plan.
- **Decision**: FIXED

### F7 — Losowa kolejność bez sposobu odtworzenia czerwonego ziarna

- **Severity**: 💬 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 4 — Success Criteria 4.1, Testing Strategy
- **Detail**: `MethodOrderer.Random` logs its seed at CONFIG level, which Surefire/Failsafe do not show; "two green `verify` runs" gives no way to repeat an order that failed in CI.
- **Fix**: failsafe sets `junit.jupiter.execution.order.random.seed` to `${it.seed}` (default from the build timestamp), `@BeforeAll` prints it, `./mvnw -B verify -Dit.seed=<n>` repeats the order.
- **Decision**: FIXED
