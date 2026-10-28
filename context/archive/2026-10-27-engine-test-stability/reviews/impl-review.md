<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: S-26: testy silnika bez zależności od zegara

- **Plan**: context/changes/engine-test-stability/plan.md
- **Scope**: Full plan
- **Reviewed phases**: 1, 2, 3, 4
- **Date**: 2026-10-28
- **Verdict**: NEEDS ATTENTION
- **Findings**: 0 critical, 2 warnings, 4 observations
- **Triage**: F1 fixed (Fix A), F2–F5 fixed, F6 accepted

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | WARNING |
| Scope Discipline | WARNING |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | WARNING |
| Success Criteria | PASS |

Success criteria re-run on 7778a60:
- 1.1 and 2.1 green: 29, 22, 8 and 11 tests.
- The 1.2, 3.2 and 4.1 greps are clean.
- 4.2: 11/11 checks green.
- 1.3–1.5, 2.2, 3.1, 3.3 and 3.4 were verified during implementation:
  - deliberate breaks, each reverted;
  - 20/20 green under CPU load;
  - seeds 1 and 2;
  - `verify` with 848 unit tests and 270 ITs.
- Manual 4.3 is pending (the user's review of the diff).

## Findings

### F1 — The E2 race remains in two dispatcher tests (the CI hang on d2c5373)

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Plan Adherence
- **Location**: szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/assess/ConcurrentAssessmentDispatcherTest.java:747-761, :950-963; szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/assess/ConcurrentAssessmentDispatcher.java:206, :260-267
- **Detail**:
  - `anOutageOfACallTheWatchdogCancelledIsNotCounted` and `aClientErrorOfACallTheWatchdogCancelledIsNotCounted` call `holding.acquire(THREADS)` and then `dispatcher.cancel(RUN)` on the default pool.
  - `dispatch` adds each Future to `tasks.futures()` only after `executor.submit` returns. A task can therefore pass the `cancelled` check and start its call before its Future is listed. `cancel` then interrupts only the listed Futures; the unlisted call stays in `release.await()`, and `step.get()` waits until `@Timeout`.
  - This is exactly the thread dump from the CI run on d2c5373. The file is unchanged at HEAD; the later green runs were luck.
  - The same mechanism most likely explains the "unknown" Timeout in run 39133095273.
  - The plan named only the cancel test. Its premise "the product is correct: a late task ends NotTaken" is incomplete: a task already in flight whose Future is not yet listed is never interrupted by `cancel`.
  - This breaks the Desired End State ("Wyścig z E2 nie może wystąpić", "Żaden test silnika nie zależy od szybkości maszyny").
  - Production is shielded in `stopStalled`: the stall commits first, and `task.cancel(true)` interrupts the run's thread, whose `await` cancels every Future, all listed by then.
- **Fix A**: In both tests, build the dispatcher on `awaitedPool(THREADS, awaited)` and call `awaited.acquire()` before `cancel` (as in the cancel test).
  - Strength: The pattern from phase 1 is already proven (deliberate pause, old test red); no production change.
  - Tradeoff: Every future test that cancels has to remember it; the `cancel` Javadoc ("reaches them") stays inexact.
  - Confidence: HIGH — the same mechanism already closed the race in the cancel test.
  - Blind spot: It does not cover future tests written without the pattern.
- **Fix B ⭐ Recommended**: In `ConcurrentAssessmentDispatcher.dispatch`, register each task before handing it to the pool: create a `FutureTask`, add it to `tasks.futures()`, then `executor.execute(task)`, as `PipelineRunService.submit` already does (`:282-287`).
  - Strength: Removes the race at its source, for both tests and any later one; makes `cancel`'s contract true without depending on the run thread's interrupt; follows an existing pattern in the same package.
  - Tradeoff: A change to the core dispatcher's production code that the plan did not foresee (just before S-20/S-27); the tests must still prove it.
  - Confidence: MED — the change is small and follows `submit`, but it needs the full dispatcher suite and a deliberate break (the old submit order → tests red).
  - Blind spot: The `AssessmentPool` executor and its rejection on shutdown (`execute` vs `submit`: a rejected task must not stay in the list as a never-finishing Future) have not been checked.
- **Decision**: FIXED (Fix A) — awaitedPool + awaited.acquire() in both tests; with a 200 ms window widened between submit and registration the fixed tests are green, the HEAD versions hang to @Timeout

### F2 — A red test can hang `@AfterEach` in PipelineRunServiceTest

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/PipelineRunServiceTest.java:452, :127-133
- **Detail**:
  - In `aRunEndingWhileOnCloseStillWritesIsTheStopNotAFailure`, the `recorder.interrupt` answer waits on `written.await()` without a bound (it had 10 s).
  - When the test exceeds `@Timeout` before `written.countDown()`, `@AfterEach release()` calls `service.onClose()`, which enters the same answer and blocks forever.
  - `@Timeout` on the class does not cover lifecycle methods, and the `backend` job has no `timeout-minutes`, so CI would hang up to 6 h instead of going red.
- **Fix**: Release `written` in a `finally` of the test, or in `@AfterEach` before `onClose()`.
- **Decision**: FIXED — written.countDown() in a finally of the test; PipelineRunServiceTest 22/22 green

### F3 — Two branches of the E1 fix have no test

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/PipelineRunService.java:392 (`late && this.stopping`), :423-427 (second read fails too)
- **Detail**:
  - Removing `|| (late && this.stopping)`, or the second suppression and the restore after a double failure in `stillRunning`, leaves the suite green.
  - `anInterruptTheNotificationRestoresIsNotKeptForTheNextRun` covers only the not-stopping side; the window-1 test covers only a successful second read.
- **Fix**: Add two tests:
  - a `runEnded` stub that marks the instance stopping and interrupts → the flag is kept after `execute`;
  - both `isRunning` reads fail on an interrupted thread → ERROR with the step's error, and the flag cleared after `execute`.
- **Decision**: FIXED — anInterruptTheNotificationRestoresIsKeptWhenTheInstanceBeganToStopMeanwhile, aStepsCheckThatFailsTwiceOnAnInterruptedThreadIsStillTheStepsError; each red without the branch it pins

### F4 — The docs say slightly more than the code

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/PipelineRunService.java:489-490; szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/CLAUDE.md (Tests: dispatcher tests; :148-151); szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/assess/ConcurrentAssessmentDispatcher.java:257
- **Detail**:
  - The `close` Javadoc says "The interrupt flag is cleared for that check and not restored here", but `stillRunning` can now restore it (`execute`'s `late` handles it).
  - `pipeline/CLAUDE.md` says the dispatcher tests cancel only after every task is registered, which the two tests in F1 do not do.
  - Pre-existing, not from this change: the `cancel` Javadoc and `CLAUDE.md:150` say it also reaches a run whose thread is in the LLM check, but before the check ends no `Tasks` is registered, so `cancel` does nothing there (the run thread's interrupt and the `claim` gate cover that window).
- **Fix**: Reword the three places to match the code (after F1's decision: Fix B makes the `CLAUDE.md` sentence about the tests true as it stands).
- **Decision**: FIXED — close Javadoc (flag restored by stillRunning), cancel Javadoc and CLAUDE.md step 3 (only registered tasks; none before the LLM check ends), CLAUDE.md test rule (every test that cancels waits for registration)

### F5 — The two retry-once blocks pick the primary exception differently

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/PipelineRunService.java:418-427; szczur-radar-api/src/main/java/pl/szczurradar/api/notifications/RunNotifications.java:77-91
- **Detail**:
  - `stillRunning` keeps the caller's exception as primary and suppresses both reads.
  - `runEnded` makes the retry's exception primary and suppresses the first.
  - Both fit their purpose and are documented, but they are two hand-written copies of one rule; a third copy has no reference.
- **Fix**: A one-line comment in each pointing to the other ("same rule as …: retry once only on an interrupted thread, flag restored").
- **Decision**: FIXED — one-line cross-reference in stillRunning and runEnded

### F6 — Changes beyond the plan (justified)

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Scope Discipline
- **Location**: context/foundation/roadmap.md, docs/visuals/roadmap.svg, tools/render_roadmap_svg.py; PipelineRunService.java:383-385; PipelineRunServiceTest `anInterruptTheNotificationRestoresIsNotKeptForTheNextRun`
- **Detail**:
  - The roadmap/SVG sync is required by the repo rule; the SVG is byte-identical to the generator's output.
  - Clearing `late` after `close` (not only after `runEnded`) follows from `stillRunning` restoring the flag.
  - The extra test pins the plan's "Critical Implementation Details".
  - Nothing violates "What We're NOT Doing".
- **Fix**: None needed; keep as recorded.
- **Decision**: ACCEPTED — justified extras (repo rule, plan detail), kept as recorded
