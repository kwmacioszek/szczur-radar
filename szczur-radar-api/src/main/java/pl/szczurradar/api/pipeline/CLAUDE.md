# pipeline — one run: fetch, store, assess

A run is an Embabel agent planned deterministically (GOAP) from the types in `flow/RunFlow`; no LLM
decides the flow. The durable state is in Postgres (`pipeline_runs`, `pipeline_run_steps`, `posts`,
`assessments`), not in Embabel. Sources are reached only through `sources/SourceFetcher` and
`sources/RawInputMapper` — never name a source here (`sources/CLAUDE.md`).

## Where things are

```
pipeline/            entry points — start here
  PipelineRunService       create runs (advisory lock, refuse while busy), queue them, run one at a time
  PipelineStartupRecovery  server start, before the port opens: unfinished runs → interrupted (cause), their posts put back
  RunWatchdog              server only: stops a run of this instance without progress (PIPELINE_STALL_TIMEOUT)
  PipelineStallSettings    PIPELINE_STALL_TIMEOUT, read and validated when the server starts
  PipelineBusyException, PostDeadException, SourceNotConfiguredException
  api/     the HTTP entries (below)
  run/     the run model: PipelineRun (+ needsAttention), PipelineRunStep, RunKind, RunStatus, RunStep, StepStatus,
           InterruptionCause, repositories
  flow/    the graph: PipelineAgent (thin actions), RunFlow (the blackboard types)
  step/    the steps: SourceIngest (fetch), PostStore (store), AssessmentSelection (select)
  assess/  assessing posts: AssessmentDispatcher (port), ConcurrentAssessmentDispatcher, AssessmentInputs
  state/   conditional state writes: RunRecorder (runs, steps, counters, activity), PostTransitions (posts),
           RunNoLongerRunningException, Durations (durations in log lines and errors)
```

The admins' notifications about runs live in `notifications/` (outside this package): the pipeline only
reports each ending to `RunNotifications.runEnded` (below).

## Entries (`api/`)

Every route starts runs only through `PipelineRunService`; none has its own start logic.

| Class | Route | Who |
| --- | --- | --- |
| `PipelineRunController` | `/api/admin/pipeline-runs/**`: start a range or chosen posts, list runs (pages of up to 100, newest first), one run `GET /{id}` (404 `not_found`), sources, failed posts. A run (`RunView`) has its steps, counters, `scheduled` (requested by `SystemAccount.SCHEDULER`), `needsAttention`, `interruptionCause`, `lastActivityAt`, `llmRetries`, `flexFallbacks` and `inFlight` (`RunRecorder.inFlight`, only for a running run, else 0) | admin session |
| `InternalPipelineRunController` | `POST /internal/pipeline-runs/daily` (D-2…D-1 of `ZONE` from the injected `Clock`, every configured source, requester `SystemAccount.SCHEDULER`; 202 `[{id, kind}]`), `GET /internal/pipeline-runs/{id}` (status, `startedAt`, `finishedAt`, `lastActivityAt`, `interruptionCause`, the running `step` or `null`, `assessTotal` = the `assess` step's `items_in` or `null`, counters; 404 `not_found`) — the cron's heartbeat reads it | the daily cron `pipeline-trigger`, token `PIPELINE_TRIGGER_TOKEN` only (`internal/`, `config/SecurityConfig`), never a session |
| `FreshnessController` | `GET /api/freshness` → `{lastUpdatedAt}`: `finished_at` of the RANGE run `succeeded`/`partial` whose range reaches furthest, the latest of those (`run/PipelineRunRepository.findLastUpdate`; an admin's run over older days leaves it) — the feed's label (FR-030); nothing else about runs | any session |
| `PipelineRunExceptionHandler` | the service's refusals for both run controllers: 409 `pipeline_busy`, 400 `post_dead`, 503 `source_not_configured` | — |

```
PipelineRunService.startRange / startPosts → queue → PipelineAgent:
  route ──► RANGE: fetch (SourceIngest → SourceFetcher beans) ──► store (PostStore) ──► select ──┐
        └─► RETRY / POSTS ─────────────────────────────────────────────────────────► select ──┤
                                                                       (AssessmentSelection)  ▼
                                    assess (AssessmentDispatcher) ──► summarize (goal: RunSummary)
```

| Class | Job |
| --- | --- |
| `flow/RunFlow` | the blackboard types; the graph follows from them. A new stage (e.g. ML) = new types and actions between `StoredBatch` and `PendingAssessments` |
| `flow/PipelineAgent` | the actions — each calls one service and is recorded as a step (`RunRecorder.step`); no logic; exactly one action can run for any blackboard state |
| `step/SourceIngest` | which sources a range run fetches (default: every configured one) and the fetch over them |
| `step/PostStore` | new posts with their raw payload, once per `(source, item_type, source_item_id)`; `findKeys` in chunks of 1,000; stores chunks of 500 in one transaction each (raw payloads, then posts: JDBC batches), a chunk hitting the unique key again post by post |
| `step/AssessmentSelection` | one attempt per post and request: range → only the posts it stored first; retry → every leftover; posts → the given ones still assessable |
| `assess/AssessmentDispatcher` | the port of the assess step (`dispatch`, and `cancel` for a run the watchdog stopped); a Batch API version may replace the concurrent one |
| `assess/ConcurrentAssessmentDispatcher` | first `LlmClient.check()` (fails → no post taken, run `failed`); own pool (`PIPELINE_LLM_CONCURRENCY`, default 4); per post: claim → `Assessor.assess` on `AssessmentInputs.of(post)` → record, or release, then the post's result goes into the run's counters at once (`RunRecorder.count`, below); an `LlmConfigurationException` (revoked key, no credit, unknown model) stops taking posts (WARN with the provider's reason), counts no attempt and fails the run (`AssessedBatch.stoppedBy`); a failed `count` is a WARN and leaves the post's outcome (a stored post is never a technical error); `cancel(runId)` interrupts the run's post tasks (below); a progress line in the log at most once a minute and after the last post |
| `assess/AssessmentInputs` | a stored post's assessment input, rebuilt from its raw payload by its source's `RawInputMapper` |
| `state/PostTransitions` | the post's stage changes, each a conditional `UPDATE` with `updated_at = now()`: claim (only while the run is running), record (only while the run owns the post), release (back without an attempt) |
| `state/RunRecorder` | run status, steps, counters, error, `last_activity_at` — every change conditional on the expected status; a step opens only in a running run and closes only once; `interrupt` / `interruptAll` (with the cause) and `stall` (the watchdog) close the open steps in the same transaction; status and stage values are bound from their enums, never written into the SQL |

## Progress, interruption, watchdog, notifications (S-06)

- **Counting per post.** The dispatcher adds each post to the run's counters as soon as its result is
  stored (`Counters.assessedOk` / `assessedFailed`, with the call's retries `max(attempts − 1, 0)` —
  the prefilter has `attempts = 0` — and whether flex fell back), or after a technical error
  (`Counters.processingError`); a post not taken counts nothing. `PipelineAgent.assess` no longer counts
  the batch at the end (it would count twice); it only sets the error summary. The counters of a running
  run are therefore live in `/admin`, on the run's page and in the cron's heartbeat.
- **`last_activity_at`.** Set by the start, every counted post and every step's start and end (all
  conditional on `running`). The fetch step moves it only at its start and end: a long fetch shows no
  progress inside (`SourceFetcher` reports none). The watchdog measures from it (or from `started_at`).
- **Log lines (INFO).** `Pipeline run <id> (<kind>) started`; `Run <id> step <step> started (in N)` and
  `Run <id> step <step> <status> (out N) in <time>`; `Run <id> assess: <done>/<N> (ok …, failed …, dead …,
  errors …), in flight …, retries …, flex fallbacks …` at most every `PROGRESS_INTERVAL` (1 min) as posts
  finish, and after the last; `Pipeline run <id> (<kind>) ended <status> in <time>: fetched …, new …, ok …,
  failed …, dead …, errors …, retries …, flex fallbacks …`. No post text, no secret.
- **Steps only in a running run.** `RunRecorder.step` inserts the step row only while the run is
  `running` (one statement that also moves `last_activity_at`); otherwise it throws
  `RunNoLongerRunningException` without doing the work, and `execute` logs that at INFO, not as a
  failure. So a worker of an interrupted or stopped run never adds a step (e.g. `summarize: succeeded`)
  to a run that is over. `endStep` changes only a step that is still `running`: the step an interruption
  or the watchdog closed stays as they left it.
- **Interruption cause** (`run/InterruptionCause`, column `interruption_cause`, only on an
  `interrupted` run; its open steps become `interrupted`):
  - `deploy` — start-up recovery found a run queued by another deployment: every run stores the
    `RAILWAY_DEPLOYMENT_ID` of the instance that queued it (`deployment_id`), and Railway stops the old
    deployment only once the new one runs, so the new instance's id differs;
  - `restart` — the same deployment, or either id unknown (no `RAILWAY_DEPLOYMENT_ID`, e.g. locally);
  - `shutdown` — the instance running it was stopping (`PipelineRunService.onClose`).
  The run's `error` says the same in words (`InterruptionCause.error()`). Runs interrupted before `V8`
  have no cause.
- **Watchdog** (`RunWatchdog`, server only, thread `pipeline-watchdog`). Every fifth of
  `PIPELINE_STALL_TIMEOUT`, at most a minute, it hands each run of this instance (`PipelineRunService.own`,
  in memory) to `stopStalled`; while `own` is empty it asks the database nothing, so the API can still
  sleep on Railway. `stopStalled`, in this order:
  1. in one transaction (as recovery pairs `interruptAll` with `releaseOrphans`): `RunRecorder.stall`
     (`running` → `failed` with `no progress for <timeout>: the run was stopped`, only when
     `last_activity_at` (or `started_at`) is still older than the timeout, and the open steps closed
     `failed` with the same error) and `PostTransitions.releaseAll` — the posts go back without an attempt;
  2. after the commit, WARN `Pipeline run <id> made no progress for <timeout>; stopped`, then
     `RunNotifications.runEnded`;
  3. `future.cancel(false)` on the agent's future, so `execute` stops waiting (`CancellationException`)
     and the same single-thread queue takes the next run (e.g. the RANGE run behind a RETRY);
  4. `AssessmentDispatcher.cancel(runId)`: the run's post futures are cancelled with `cancel(true)`, so a
     post not started never runs and a call in flight gets an interrupt (INFO `Run <id> assess: stopped,
     N post task(s) not finished were cancelled`).
  Writing first matters: cancelling first would let `execute` close the run `failed` with another error.
  `onClose` likewise interrupts its runs and puts their posts back in one transaction, then notifies.
- **Why a future, not the queue thread.** Embabel runs the agent's actions on its own pool
  (`embabel-platform-N`; `invoke()` is `invokeAsync().get()`), so the `pipeline-run` thread only waits:
  interrupting it never reaches the action or the dispatcher's `await`. `execute` therefore starts the
  agent with `invokeAsync` and keeps its future in `waiting` while it waits. The cancelled action goes
  on in Embabel's thread (a "zombie") but changes nothing: `claim` needs a running run, `record` needs
  the post still held by the run (not after `releaseAll`), and counters and steps are conditional. It
  holds no thread for good: its post futures are cancelled with an interrupt (`cancel(true)`), so its
  `await` returns at once (a cancelled task counts nothing) and its calls end — an LLM call waiting for
  its answer or between attempts at once, one blocked in a socket read within its attempt's
  `LLM_TIMEOUT`, without another attempt (`TransportRetry`'s wait fails on the pending interrupt); a
  failure a cancelled task ends with is logged at INFO, never as the post's technical error. A database
  call ends within the driver's `socketTimeout` (300 s, `application.properties`). This is temporary: it
  goes away with Embabel out of the run (`roadmap.md`, `## Parked`). `execute` catches
  `InterruptedException` (the instance closing) on its own: it neither closes nor reports that run
  (`onClose` did both, and the database may be closing), and restores the thread's interrupt flag.
- **`PIPELINE_STALL_TIMEOUT`** (`PipelineStallSettings`, read only by the server): optional, by the one
  duration rule (`settings/DurationSetting`: a whole number with a lower-case unit, e.g. `39m`), from `1ms`
  to `1d`; default `max(15m, TransportRetry.maxCallDuration(LLM_TIMEOUT, LLM_SERVICE_TIER) + 5m)` — the LLM
  check, a run's last posts and a small retry run have one call in flight, which moves nothing until it
  ends. `15m` with the defaults (`120s`, tier `default`), `39m` on production (`300s`, `flex`). A shorter
  explicit value is accepted with a startup WARN naming `PIPELINE_STALL_TIMEOUT` and `LLM_TIMEOUT`; a bare
  number, ISO-8601 (`PT5M`) or a value outside `1ms`–`1d` throws `InvalidSettingsException` from the
  `RunWatchdog` bean, and the server's start fails with an `APPLICATION FAILED TO START` block
  (`config/InvalidSettingsFailureAnalyzer`, no stack trace at ERROR).
- **Notifications.** Every way a run ends calls `RunNotifications.runEnded(runId)` directly (not through
  Spring events: recovery runs before listeners are known to be registered), four places:
  1. `PipelineRunService.execute`, in `finally` after `close` (its own end, an exception; not after an
     `InterruptedException`, which `onClose` already reported);
  2. `PipelineRunService.onClose`, for each run it interrupted (`shutdown`);
  3. `PipelineStartupRecovery.recover`, for each run it interrupted, after its transaction commits;
  4. `PipelineRunService.stopStalled`, after the watchdog's stop.
  `runEnded` notifies only a run with `PipelineRun.needsAttention()` — `failed`, `interrupted`, or
  `partial` with a `dead` post or a technical error (a `partial` run with only retryable `failed` posts
  does not: the next run retries them) — one row per admin with `login_enabled`, `ON CONFLICT DO
  NOTHING`, so a run reported twice is notified once. It never throws: a failure is logged (ERROR) and
  the run's ending, or the server's start, goes on. The unique key `uq_notifications_recipient_kind_run`
  (`V9`) treats a `NULL` `run_id` as distinct: a future kind without a run needs a new migration with
  `UNIQUE NULLS NOT DISTINCT` (never an edit of `V9`), or its `ON CONFLICT` never fires.

## Rules

- Every state change is one conditional `UPDATE` whose row count says whether it happened — no row
  locks, no blind `save()` of a run or a post. A worker of an interrupted run (deploy overlap) must
  never change a post another run took over.
- An attempt counts only when a `failed` assessment row is stored; the third makes the post `dead`
  (`state/PostTransitions.Outcome`). A technical error (any exception — a failed LLM call is a
  `failed` result from `Assessor`, not an exception) stores nothing, puts the post back and is counted
  in `processing_errors` (`RunFlow.AssessedBatch.errorSummary` becomes the run's error).
- Code that runs work in parallel passes its own executor (`availableProcessors()` may be 1).
- Every agent action fires once (`@Action(actionRetryPolicy = FIRE_ONCE)`): Embabel's default policy
  retries an action after most exceptions, with growing pauses (seen: a failed Wykop fetch repeated
  every 10 s and more), which would repeat and re-record a step. Retries of a call live only below
  the step (`llm/transport/TransportRetry`).
- No run stays `queued`/`running` without a thread: `PipelineRunService.execute` ends it `failed` on
  any exception; start-up recovery interrupts unfinished runs and puts back every post `assessing`
  without a queued or running run (`PostTransitions.releaseOrphans`), also one claimed just as its run
  was interrupted by a stopping instance. A run of this instance that hangs (no progress for
  `PIPELINE_STALL_TIMEOUT`) is stopped by `RunWatchdog`, so it never blocks the queue.
- Whoever ends a run reports it to `RunNotifications.runEnded`; a new way to end a run adds that call.
- Start-up recovery runs before the web server opens its port (`SmartInitializingSingleton`, not
  `ApplicationReadyEvent`): it interrupts every queued or running run without asking who created it,
  and the cron's `POST` is often the very request that wakes the API, so a later recovery would
  interrupt the run that request just queued. `PipelineStartupRecoveryIT` checks the order; keep it
  before `WebServerInitializedEvent`.
- The root package holds only what other packages (the API in S-02 phase 4) call; the subpackages are
  the run's internals.

## Tests

- Unit (`*Test`, no Spring): `state/RunOutcomeTest`, `state/DurationsTest`, `step/PostStoreTest`,
  `step/AssessmentSelectionTest`, `step/SourceIngestTest`, `assess/ConcurrentAssessmentDispatcherTest`
  (threads, one client, a technical error; one `count` per stored post with its retries and flex
  fallback, `processingError`, nothing for a post not taken; a failed `count` keeps a stored post
  stored; `cancel` interrupts the run's call in flight, skips its post not started and leaves another
  run's call alone; the progress line), `run/PipelineRunTest` (`needsAttention` for every status),
  `PipelineStallSettingsTest` (default from the LLM settings, WARN below it, units, refusals),
  `RunWatchdogTest` (no database call without a run of its own, a failure and an `Error` logged, the
  checks going on after an `Error`, the check interval); `llm/transport/TransportRetryTest` covers
  `maxCallDuration`.
- `api/InternalPipelineRunIT` (+ `InternalPipelineRunWithoutTokenIT`): the token, no session and no
  cookie on `/internal/**`, the D-2…D-1 range for a fixed `Clock` (also a DST day), 409, 404, the exact
  keys of `GET /internal/pipeline-runs/{id}` with `step` and `assessTotal` during the assessment.
  `api/PipelineRunControllerIT`: the run page, 404, the new fields, `scheduled` for the cron's run.
  `api/FreshnessIT`: which runs move the label. `PipelineStartupRecoveryIT`: recovery before the port,
  the cause `deploy` / `restart`, steps `interrupted`, notifications.
- `RunWatchdogIT` (`PIPELINE_STALL_TIMEOUT=5s`, `PIPELINE_LLM_CONCURRENCY=1`, a scripted `LlmClient`
  held on a latch): a hanging run ends `failed` with its `assess` step closed and its posts back without
  an attempt, its held call is interrupted, so the run queued behind it assesses on the only LLM thread,
  the zombie action stores nothing, a slow run with progress (a post every 1.5 s) is not stopped, another
  instance's run is left alone, the admins are notified, and a database connection has the 300 s
  `socketTimeout`.
- `notifications/api/NotificationsIT`: recipients (admins only), idempotence, which run endings notify,
  the list, marking one and all read, 404 for another admin's.
- `PipelineAgentIT`: the whole graph on Postgres with a source on a local server and a scripted
  `LlmClient` — range, the same range again, a leftover's one attempt per request, recovery and a late
  worker, a failing source (`failed`, the queue takes the next request), an orphaned `assessing` post,
  a stopping instance (`onClose`, cause `shutdown`, step `interrupted`, its queue thread neither closes
  nor reports the run again) and a refused LLM key (`failed`,
  no attempt); counters and `last_activity_at` growing during the assessment; no new step in an
  interrupted run and no ERROR in the log. Re-stub the `@MockitoBean` with `doAnswer(…).when(…)`, not `when(…)` (which calls the old
  answer with `null`).
