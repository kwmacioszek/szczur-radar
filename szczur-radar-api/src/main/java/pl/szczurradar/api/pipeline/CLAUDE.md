# pipeline — one run: fetch, store, assess

A run is a plain sequence of steps in Java (`flow/RunSequence`), executed on the queue thread
`pipeline-run` of `PipelineRunService`; no LLM and no framework decides the flow. The durable state is in
Postgres (`pipeline_runs`, `pipeline_run_steps`, `pipeline_run_sources`, `posts`, `assessments`). Sources are reached only
through `sources/SourceFetcher` and `sources/RawInputMapper` — never name a source here
(`sources/CLAUDE.md`).

## Where things are

```
pipeline/            entry points — start here
  PipelineRunService       create runs (advisory lock, clean up orphans, refuse while busy), queue them, run one at a time
  PipelineStartupRecovery  server start, before the port opens: unfinished runs → interrupted (cause), their posts put back
  RunWatchdog              server only: stops a run of this instance without progress (PIPELINE_STALL_TIMEOUT)
  PipelineStallSettings    PIPELINE_STALL_TIMEOUT, read and validated when the server starts
  PipelineStallConfiguration  server only: the one PipelineStallSettings bean (watchdog, queue, deploy safety)
  NightSummary             S-34: the night's summary of the cron's runs (a line per source, S-35's quality line per
                           source, the admin runs' dead posts), read-only; composed for POST
                           /internal/pipeline-runs/nightly-summary and raised as NIGHT_SUMMARY (+ ASSESSMENT_QUALITY
                           when a source crossed its threshold)
  PipelineBusyException, PostDeadException, SourceNotConfiguredException
  api/     the HTTP entries (below)
  run/     the run model: PipelineRun (+ attentionReasons → needsAttention), AttentionReason, PipelineRunStep,
           PipelineRunSource (a source's result in a range run), SourceFetchStatus, RunKind, RunStatus, RunStep,
           StepStatus, InterruptionCause, repositories, CoveredRange, MissingDays (the days no source's `succeeded`
           result covered, for the admin's list)
  flow/    RunSequence (the steps in order), RunFlow (the step types, SourceFetch: one source's result)
  step/    the steps: SourceIngest (fetch), PostStore (store), AssessmentSelection (select),
           NoPostsFetchedException (no source gave a post: the fetch fails)
  assess/  assessing posts: AssessmentDispatcher (port), ConcurrentAssessmentDispatcher, AssessmentInputs,
           AssessmentPool (the LLM threads, PIPELINE_LLM_CONCURRENCY)
  state/   conditional state writes: RunRecorder (runs, steps, counters, activity, `source`: a source's result,
           `storeRejected`: the store's rejected posts as skipped, S-38),
           PostTransitions (posts),
           RunNoLongerRunningException, RunInterruptedException (a step's checkpoint after an interrupt),
           Durations (durations in log lines and errors)
```

The admins' notifications about runs live in `notifications/` (outside this package): the pipeline only
reports each ending to `RunNotifications.runEnded` (below).

## Entries (`api/`)

Every route starts runs only through `PipelineRunService`; none has its own start logic.

| Class | Route | Who |
| --- | --- | --- |
| `PipelineRunController` | `/api/admin/pipeline-runs/**`: start a range or chosen posts, list runs (pages of up to 100, newest first), one run `GET /{id}` (404 `not_found`), sources `GET /sources`, the days without data `GET /missing-days` → `{from, to, days: [{day, sources}]}` (`run/MissingDays`: the window D-15…D-2 of `ZONE` from the injected `Clock`; a day is covered for a configured source by a RANGE run `succeeded`/`partial` whose result for that source (`run/PipelineRunSource`) is `succeeded` — it gave a post; a failed or empty source covers nothing, though the run requested it — the cron's or an admin's (`run/PipelineRunRepository.findCoveringSince` / `findFirstCoveredFrom`); a source's window starts at its first covered day ever, so one never covered misses none; days ascending, `sources` in `Source` order; it starts nothing). A run (`RunView`) has its steps, counters, `scheduled` (requested by `SystemAccount.SCHEDULER`), `needsAttention` and `attentionReasons` (`run/AttentionReason` codes in the rule's order, e.g. `["dead_posts","skipped"]`, empty when it needs none; the SPA turns them into text in `src/runs.ts`), `interruptionCause`, `lastActivityAt`, `llmRetries`, `flexFallbacks`, `inFlight` (`RunRecorder.inFlight`, only for a running run, else 0), `sourcesFailed` / `sourcesEmpty` and `sourceResults` (each source's `run/PipelineRunSource` — `source`, `status`, `fetched`, `skipped`, `error`, `finishedAt` — in `Source` order; the list loads them with one `findByRunIdIn`; empty for a run that is not a range and before its first source ends) | admin session |
| `InternalPipelineRunController` | `POST /internal/pipeline-runs/daily` (D-2…D-1 of `ZONE` from the injected `Clock`, every configured source, requester `SystemAccount.SCHEDULER`; 202 `[{id, kind}]`), `GET /internal/pipeline-runs/{id}` (status, `startedAt`, `finishedAt`, `lastActivityAt`, `interruptionCause`, the running `step` or `null`, `assessTotal` = the `assess` step's `items_in` or `null`, counters `fetched`, `skipped`, `newPosts`, `assessedOk`, `assessedFailed`, `processingErrors`, `dead`; 404 `not_found`) — the cron's heartbeat reads it; `POST /internal/pipeline-runs/nightly-summary` (S-34; body `{"runs": [<id>, …]}`, 1–10 ids, else 400 `invalid_request`; an unknown id 404 `not_found`) → 200 `{"text": …}`, the night's summary of those runs (`NightSummary`: the night's line with the range run's range and each run's status, a line per source with fetched/skipped (or `failed: …` / `empty`), new, leftovers, assessed, failed, dead and tokens, since S-35 a quality line per source with attempts in the 7-day window, the dead posts of the admin's runs since the previous night, `/admin`), raised at once as `AlertEvent.NIGHT_SUMMARY` for the range run's id (else the first) through `OperatorAlertQueue.raise(ObjectProvider, …)`, then, when a source crossed its quality threshold tonight (`Night.qualityAlert()`), `AlertEvent.ASSESSMENT_QUALITY` for the same id — the controller is built in an operator command's context too, which has no queue | the daily cron `pipeline-trigger`, token `PIPELINE_TRIGGER_TOKEN` only (`internal/`, `config/SecurityConfig`), never a session |
| `FreshnessController` | `GET /api/freshness` → `{sources: [{source, lastUpdatedAt}]}`, in `Source` order, only the sources with an update (`[]` before the first): for each source, `finished_at` of the RANGE run `succeeded`/`partial` with that source's result `succeeded` whose range reaches furthest, the latest of those (`run/PipelineRunRepository.findLastUpdates`, one query per source, the same rule as `findCoveringSince`; an admin's run over older days leaves the date, and a source that failed or gave no post keeps its own while another moves on) — the feed's label (FR-030, S-29); it reads no configuration; nothing else about runs | any session |
| `DeploySafetyController` | `GET /api/deploy-safety` → `{safe, reason}`: `reason` (`DeploySafetyReason`) `pipeline_running` before `pipeline_queued` (both can exist: a retry runs, the range waits), `null` when safe (`run/PipelineRunRepository.existsLiveActive` decides it in one statement, then `existsRunning` the reason, over every active run: two statements would both miss a run leaving the queue between them). A dead run — queued or running without activity for `PIPELINE_STALL_TIMEOUT` (`COALESCE(last_activity_at, started_at, created_at)`, the database's clock), and no run ended within it (a queue that just ended a run is alive) — does not hold the deploy (safe when every active run is dead: no instance executes it, and the new instance's start-up recovery interrupts it anyway); it only reads, the next request to start runs interrupts them (`lost`, Rules); 503 `{status: unavailable}` without the database, as `/api/ready` — the CI deploy gate's question (S-14); nothing else about runs | anyone, no session (`PUBLIC_ROUTES`) |
| `PipelineRunExceptionHandler` | the service's refusals for both run controllers: 409 `pipeline_busy`, 400 `post_dead`, 503 `source_not_configured` | — |

The failed posts' list (`GET /api/admin/posts/failed`, "On hold" below) is not a route of this package:
`posts/api/FailedPostController`; nor is restoring dead posts (`POST /api/admin/posts/restore`,
`posts/api/DeadPostController`, S-33), which writes only through `state/PostTransitions.restore` and starts
no run.

```
PipelineRunService.startRange / startPosts → queue (thread pipeline-run) → RunSequence.run:
  RANGE: fetch (SourceIngest → SourceFetcher beans) ──► store (PostStore) ──► select ──┐
  RETRY / POSTS ─────────────────────────────────────────────────────────────► select ──┤
                                                              (AssessmentSelection)  ▼
                                    assess (AssessmentDispatcher) ──► summarize (RunSummary)
```

| Class | Job |
| --- | --- |
| `flow/RunFlow` | the data the steps hand to each other (records). A new stage (e.g. ML) = new types and steps between `StoredBatch` and `PendingAssessments`. `StoredBatch` (S-38): the new posts' ids, each source's `SourceStored` (`posts = newPosts + duplicates + rejected`; its constructor refuses counts that do not add up or are negative) and the `RejectedPost`s (source, item type, source id, cause), with `rejectedBySource()` and `rejectedSummary()` (`store rejected N post(s), counted as skipped: <source> <item type> <source id> (<cause>); …`, at most 10, then `; and M more`) |
| `flow/RunSequence` | the steps of a run in order — RANGE: fetch → store → select → assess → summarize; RETRY / POSTS: select → assess → summarize — each a call to one service recorded as a step (`RunRecorder.step`). The fetch step writes each source's result as the source ends (`RunRecorder.source`); after the store step, outside it, the new posts are counted and each source's posts the database rejected are added to the `skipped` of the run and of the source's row (`RunRecorder.storeRejected`, in `Source` order; S-38); a range run's error has up to three parts, joined ` \| ` in this order — the fetch's summary of the sources that did not succeed, the store's rejected posts (`StoredBatch.rejectedSummary`) and the assessment's error; next to rejected posts the fetch's summary is cut to `FETCH_SUMMARY_WITH_STORE_MAX` (1000) characters, so the store's part survives `RunRecorder`'s 2000 — and its status the assessment's, but `partial` when that succeeded and a source did not (below); rejected posts never change it. A step's exception leaves `run` as it was thrown, and no later step starts |
| `step/SourceIngest` | which sources a range run fetches (default: every configured one) and the fetch over them, each source isolated: a source that throws (also one not configured any more) is its `failed` result (`<SimpleName>: <message>`, a WARN line with the exception; on an interrupted thread — the run was stopped during the source — the same result at INFO without the exception, `ended with the run's stop`), one without a post its `empty` result, and the next source is fetched; each result is handed to the caller as soon as its source ended, outside the source's `try` (`RunSequence` writes it; a failed write ends the step); the step fails only when no source gave a post (`NoPostsFetchedException`, below); a source with skipped elements gets a WARN line; before each source an interrupt checkpoint (`RunInterruptedException`, below) |
| `step/PostStore` | new posts with their raw payload, once per `(source, item_type, source_item_id)`; `findKeys` in chunks of 1,000; stores chunks of 500 in one transaction each (raw payloads, then posts: JDBC batches; the `INSERT`s go at the commit, so a chunk's failure comes from it), a chunk failing with a `DataIntegrityViolationException` again post by post; each post ends as one of three (S-38): new, a duplicate (stored by an earlier run, repeated in the fetch, or — its save failed and its key, read again after its transaction rolled back, is stored — stored meanwhile by another run) or rejected (its save failed with a `DataIntegrityViolationException` and its key is not stored: its raw payload rolled back with it, the rest of its chunk stored; its cause the constraint's name (for a `NOT NULL` the column's name, as Hibernate reports it), else `SQLState <code>` (in practice a data error), else `unknown`, never the exception's message); any other exception ends the step; before each chunk an interrupt checkpoint (`RunInterruptedException`, below), so a stopped run stores at most the chunk it was in |
| `step/AssessmentSelection` | one attempt per post and request: range → only the posts it stored first; retry → every leftover except posts on hold (`PostRepository.findLeftoverIds`, below); posts → the given ones still assessable (`stored` / `failed`, on hold or not) |
| `assess/AssessmentDispatcher` | the port of the assess step (`dispatch`, which stops like `cancel` when its thread is interrupted while it waits, and `cancel` for a run the watchdog stopped); a Batch API version may replace the concurrent one |
| `assess/ConcurrentAssessmentDispatcher` | first `LlmClient.check()` (any exception → no post taken, run `failed` with `the LLM check failed: …`; a check that fails on an interrupted thread — the run was stopped during it — ends the step with `RunInterruptedException` instead, at INFO, the check's exception as its cause); the `AssessmentPool` threads (`PIPELINE_LLM_CONCURRENCY`, default 4: a bean passed in, shut down with the context); per post: claim → `Assessor.assess` on `AssessmentInputs.of(post)` → record, or release, then the post's result goes into the run's counters at once (`RunRecorder.count`, below); an `LlmConfigurationException` (revoked key, no credit, unknown model) stops taking posts (WARN with the provider's reason), counts no attempt and fails the run (`AssessedBatch.stoppedBy`); an `LlmUnavailableException` (retries used up) stores nothing and releases the post, and `OUTAGE_STREAK` (3) of them in a row stop the step the same way (the breaker, Rules below); any other exception puts the post back with `releaseFailed` (one more technical failure), and `CLIENT_ERROR_STREAK` (3) `LlmClientException`s in a row (errors the LLM client could not classify) also stop the step (the client's breaker, Rules below); a call the provider refused is the post's `failed` result (`provider_error` / `timeout`), stored with its attempt like any failed result, and `REFUSAL_STREAK` (3) of them in a row (`AssessmentResult.callFailed()`, with no answer of the model between them) stop the step after the third is stored (the refusals' breaker, Rules below); a failed `count` is a WARN and leaves the post's outcome (a stored post is never a technical error), and once every post has finished the step's failed counts give one ERROR and `RUN_COUNTERS_LOST` (below); `cancel(runId)` interrupts the run's post tasks, and so does an interrupt of the thread waiting in `dispatch` (it marks them cancelled first, then throws; below) and the pool's own shutdown when the context closes; a task ending on an interrupted thread counts nothing, whichever of the three interrupted it; a progress line in the log at most once a minute and after the last post |
| `assess/AssessmentInputs` | a stored post's assessment input, rebuilt from its raw payload by its source's `RawInputMapper` |
| `state/PostTransitions` | the post's stage changes, each a conditional `UPDATE` with `updated_at = now()`: claim (only while the run is running), record (only while the run owns the post; resets `technical_failures` and `last_technical_error`), release (back without an attempt; says whether this run put it back), releaseFailed (release + one more technical failure with its message, in the same statement, only while the run owns the post), countTechnicalFailure (one more technical failure on a post already put back, `stored` / `failed`, only while the run is still running), restore (an admin's: `dead` → `stored` with 0 attempts, 0 technical failures and no last error, only from `dead`, the posts' `assessments` rows kept; `posts/api/DeadPostController`, `POST /api/admin/posts/restore`, S-33); the message is cut to 500 characters, without U+0000. A column added to `posts` is classified in `state/PostColumnsIT` (set once by the `INSERT`, or post state with its value after every transition), and a new transition gets a scenario there |
| `state/RunRecorder` | run status, steps, counters, error, `last_activity_at`, each source's result (`source`: the run's `fetched`, `skipped`, `sources_failed` / `sources_empty`, its activity and the `pipeline_run_sources` row in one statement, only while it runs), the store's rejected posts (`storeRejected`, S-38: added to the `skipped` of the run and of the source's row, with the run's activity, in one statement, only while it runs and only when the source's row exists — both rows or neither) — every change conditional on the expected status; a step opens only in a running run and closes only once; `step(…, Function<T, StepOutcome>)` lets a step decide its own end from its result (the assess step whose dispatcher stopped, `AssessedBatch.stoppedBy`, is closed `failed` with that reason though `dispatch` returned), `step(…, ToIntFunction<T>)` closes it `succeeded`; a step that throws is closed `failed` with the error, and when that close fails too (an interrupted thread, the database gone) its failure is suppressed in the step's exception; `interrupt` / `interruptAll` (with the cause), `interruptOrphans` (cause `lost`, all or none; Rules), `stall` (the watchdog) and `fail` (a run that cannot go on: an `Error` escaping its step leaves the step open, `step` catching only `RuntimeException`) close the open steps in the same transaction; status and stage values are bound from their enums, never written into the SQL |
| `state/RunInterruptedException` | `checkpoint(runId, step)` throws when the run's thread is interrupted (the flag stays set): `PostStore` before each chunk, `SourceIngest` before each source |

## Progress, interruption, watchdog, notifications (S-06)

- **Counting per post.** The dispatcher adds each post to the run's counters as soon as its result is
  stored (`Counters.assessedOk` / `assessedFailed`, with the call's retries `max(attempts − 1, 0)` —
  the prefilter has `attempts = 0` — and whether flex fell back), or after a technical error
  (`Counters.processingError`); a post not taken counts nothing. A single provider outage is the
  exception: its processing error is counted only when the step ends (deferred, Rules below).
  `RunSequence.assess` does not count the batch at the end (it would count twice); it only sets the error summary. The counters of a running
  run are therefore live in `/admin`, on the run's page and in the cron's heartbeat.
- **`last_activity_at`.** Set by the start, every counted post, every post whose call found the provider
  unavailable (`RunRecorder.touch`: counted only at the step's end, but its end is progress, so the
  watchdog never stops a run of outages before the breaker) and every step's start and end (all
  conditional on `running`). The fetch step also moves it after each source (`RunRecorder.source`), so
  sources fetched one after another are each measured on their own; inside one source's fetch nothing
  moves it (`SourceFetcher` reports no progress). The watchdog measures from it (or from `started_at`).
  A source bounds its fetch with a deadline below `PipelineStallSettings.MIN_DEFAULT` (Wykop: 10 min,
  `WykopRepository.FETCH_DEADLINE`; Reddit: 10 min over all its subreddits, `RedditRepository.FETCH_DEADLINE`),
  so a failing source ends with its `failed` result before the watchdog
  stops the run; the deadline is per source, and their sum may exceed the threshold (`RunWatchdogIT`).
  An explicit `PIPELINE_STALL_TIMEOUT` below that deadline plus two request time-outs (Wykop: about
  11 min) would stop a failing fetch as stalled instead; `minimum()` warns only about the LLM call, so
  do not set it lower.
- **Log lines (INFO).** `Pipeline run <id> (<kind>) started`; `Run <id> step <step> started (in N)` and
  `Run <id> step <step> <status> (out N) in <time>`; `Run <id> assess: <done>/<N> (ok …, failed …, dead …,
  errors …, unavailable …), in flight …, retries …, flex fallbacks …` at most every `PROGRESS_INTERVAL`
  (1 min) as posts finish, and after the last (`unavailable`: posts whose call found the provider
  unavailable, part of `done`; the run's processing errors are `errors` plus those of them counted at the
  step's end); `Pipeline run <id> (<kind>) ended <status> in <time>: fetched …, skipped …,
  new …, ok …, failed …, dead …, errors …, retries …, flex fallbacks …`. The fetch's WARNs: `Run <id>: <source>
  skipped <N> of <M> elements (unexpected shape or not mappable)` from `SourceIngest.fetch`, only for a
  source with skipped elements, and `Run <id>: <source> failed: <message>` with the exception, for a source
  that threw (on an interrupted thread — the run was stopped during it — INFO `Run <id>: <source> ended with
  the run's stop: <exception>` instead); each source's result then gives INFO `Run <id>: <source> <status>: fetched N, skipped M[;
  <error>]` from `RunRecorder.source` (only when written: not for a run already stopped). The store step (S-38): INFO
  `Run <id> store <source>: <P> post(s), new <A>, duplicates <D>, rejected <R>` for each source with posts, WARN
  `Run <id>: the database rejected <source> <item type> <source id> at store (<cause>, <SimpleName>); counted as
  skipped` for each rejected post (without the exception and its message: they hold the row's values), and after
  the step INFO `Run <id>: <source> store rejected <N>, counted as skipped` from `RunRecorder.storeRejected` (only
  when written). Hibernate logs its own WARN lines of a failed batch (`org.hibernate.orm.jdbc.error`); they carry no row
  values (`Batch entry N <unknown> was aborted: ERROR: … constraint "<name>"`, no `Failing row`), because the data
  source sets pgjdbc's `logServerErrorDetail=false` (`application.properties`). The assess step adds its own:
  WARN `The LLM provider is unavailable in run <id>: 3 calls in a row failed after their retries; no
  further post is taken: …` (the breaker, once), WARN `The LLM client failed 3 calls in a row in run <id>
  with an error that is not the provider's; no further post is taken: …` (the client's breaker, once),
  WARN `The LLM provider refused 3 calls in a row in run <id>; no further post is taken: …` (the
  refusals' breaker, once; the last refusal's message, cleaned of the key by the client),
  WARN `Assessing post <id> in run <id> found the LLM
  provider unavailable; the post was put back without an attempt, counting a technical failure: …` (each
  single outage counted at the step's end) and ERROR `Assessing post <id> in run <id> failed; the post is
  put back without an attempt, technical failure N` (an error of our code). No post text, no secret.
  These are not all the engine's WARNs (`grep log.warn` under `pipeline/` lists them): the dispatcher also
  warns when the provider refuses the configuration, a `count` fails (`RUN_COUNTERS_LOST`, below), an
  outage cannot be counted as its post's technical failure and the progress line cannot be logged;
  `PipelineRunService` at the orphan clean-up (Rules) and the watchdog's stop (below);
  `PipelineStartupRecovery` for the runs it interrupted and the posts it put back; `PipelineStallSettings`
  for a short `PIPELINE_STALL_TIMEOUT` at start-up.
- **Steps only in a running run.** `RunRecorder.step` inserts the step row only while the run is
  `running` (one statement that also moves `last_activity_at`); otherwise it throws
  `RunNoLongerRunningException` without doing the work, and `execute` logs that at INFO, not as a
  failure. So a worker of an interrupted or stopped run never adds a step (e.g. `summarize: succeeded`)
  to a run that is over. `endStep` changes only a step that is still `running`: the step an interruption
  or the watchdog closed stays as they left it. The assess step ends by its result (audit A6): a dispatch
  the dispatcher stopped (`AssessedBatch.stoppedBy`: the LLM check failed, the configuration refused, the
  breaker, the client's breaker, the refusals' breaker) closes the step `failed` with that reason, so a `failed` run never shows its `assess` step
  `succeeded`.
- **Interruption cause** (`run/InterruptionCause`, column `interruption_cause`, only on an
  `interrupted` run; its open steps become `interrupted`):
  - `deploy` — start-up recovery found a run queued by another deployment: every run stores the
    `RAILWAY_DEPLOYMENT_ID` of the instance that queued it (`deployment_id`), and Railway stops the old
    deployment only once the new one runs, so the new instance's id differs;
  - `restart` — the same deployment, or either id unknown (no `RAILWAY_DEPLOYMENT_ID`, e.g. locally);
  - `shutdown` — the instance running it was stopping (`PipelineRunService.onClose`);
  - `lost` — nobody executed it any more (its instance was lost without closing it): another instance,
    asked to start a run, found no queued or running run with activity for `PIPELINE_STALL_TIMEOUT`
    (`RunRecorder.interruptOrphans`, Rules; `V11` adds the value).
  The run's `error` says the same in words (`InterruptionCause.error()`). Runs interrupted before `V8`
  have no cause.
- **Watchdog** (`RunWatchdog`, server only, thread `pipeline-watchdog`). Every fifth of
  `PIPELINE_STALL_TIMEOUT`, at most a minute and at least 100 ms (`RunWatchdog.checkInterval`), it hands each run of this instance (`PipelineRunService.own`,
  in memory) to `stopStalled`; while `own` is empty it asks the database nothing, so the API can still
  sleep on Railway. `stopStalled`, in this order:
  1. in one transaction (as recovery pairs `interruptAll` with `releaseOrphans`): `RunRecorder.stall`
     (`running` → `failed` with `no progress for <timeout>: the run was stopped`, only when
     `last_activity_at` (or `started_at`) is still older than the timeout, and the open steps closed
     `failed` with the same error) and `PostTransitions.releaseAll` — the posts go back without an attempt;
  2. after the commit, WARN `Pipeline run <id> made no progress for <timeout>; stopped`, then at once
     `cancel(true)` on the run's queue task (`PipelineRunService.running`, a `FutureTask` registered before
     the executor gets it), which interrupts the `pipeline-run` thread inside its step (below), so the step
     ends within seconds and the same single-thread queue takes the next run (e.g. the RANGE run behind a
     RETRY);
  3. `AssessmentDispatcher.cancel(runId)`: the run's post futures are cancelled with `cancel(true)`, so a
     post not started never runs and a call in flight gets an interrupt (INFO `Run <id> assess: stopped,
     N post task(s) not finished were cancelled`) — only the tasks registered so far: none before the LLM
     check has ended (there the interrupt of step 2, and `claim` needing a running run, end the step), and
     a task whose call started just before `dispatch` registered its future is reached only through step 2
     (the dispatcher waiting in `dispatch` cancels every task itself);
  4. `RunNotifications.runEnded` — last, so its database round trip does not widen the window in which the
     stalled step ends by itself and the interrupt lands in `execute`'s clean-up instead (`close` tolerates
     it, below); idempotent, so `execute` may report the run too.
  Writing first matters: interrupting first would let `execute` close the run `failed` with another error.
  `onClose` likewise first marks the instance stopping, then interrupts its runs and puts their posts back
  in one transaction, then notifies, then `shutdownNow()` interrupts the step the same way.
- **Interruption reaches the step.** A run's steps run on the `pipeline-run` thread itself
  (`flow/RunSequence`), so the watchdog's `cancel(true)` and `onClose`'s `shutdownNow()` interrupt the step.
  What observes the interrupt:
  - the dispatcher waiting for its posts (`await`): it marks the run's post tasks cancelled, cancels them
    with `cancel(true)` and throws `IllegalStateException` (`interrupted while assessing run …`); a
    cancelled task counts nothing, and what it ends with is logged at INFO (`Assessing post <id> in run <id>
    ended with its stopped run: …`), never as an outage or the post's technical error;
  - an LLM call waiting for its answer or between attempts (`TransportRetry`'s wait fails on the interrupt,
    without another attempt); a source's client (`sources/transport/HttpRetry`: `WykopApiException` /
    `RedditApiException` with `INTERRUPTED`, without another attempt);
  - the LLM check before the posts (`llm.check()`), when its call fails on the interrupted thread: the
    dispatcher ends the step with `RunInterruptedException` (INFO `Run <id> assess: the LLM check was
    interrupted by the run's stop`; the check's exception its cause), not with the check's ERROR line;
  - the checkpoints `RunInterruptedException.checkpoint` before each chunk of `PostStore.store` and each
    source of `SourceIngest.fetch`, so a stopped run stores at most the chunk it was in.
  What does not: a call blocked in a socket read on OkHttp (Gemini, OpenRouter) ends within its attempt's
  `LLM_TIMEOUT`, and — an assumption about OkHttp that no test checks — without another attempt: whether
  the read then ends with an `InterruptedIOException` (which `TransportException.ofTimeoutOrConnection`
  classifies `TIMEOUT`, a retryable kind) and whether the thread's flag is still set for `TransportRetry`'s
  wait to stop on it is OkHttp's / okio's behaviour, untested here; a database call within the driver's `socketTimeout` (300 s,
  `application.properties`). Hence the two levers of `stopStalled` (above). Whatever the stopped step still
  does changes nothing: `claim` needs a running run, `record` needs the post still held by the run (not
  after `releaseAll`), counters and steps are conditional, and `endStep` leaves the step as the stop closed
  it (INFO `Run <id> step <step> ended in <t> after its run had stopped`).
- **How `execute` classifies a step's exception.** The run's state decides, not the exception's type
  (after an interrupt it may be any of `WykopApiException`, `RedditApiException`, `IllegalStateException`,
  `RunInterruptedException`, `RunNoLongerRunningException`). One `catch` for every `RuntimeException`
  first clears the interrupt flag (`Thread.interrupted()`) and reads whether the instance is stopping (the
  flag `stopping`, set as `onClose`'s first statement, before its writes; `executor.isShutdown()` becomes
  true only at its end), before any database call (the connection pool refuses an interrupted thread that
  has to wait), then:
  - the instance is stopping → INFO `Pipeline run <id>: stopped, the application is stopping` (the
    exception itself at DEBUG); the run is neither closed nor reported (`onClose` does both, and the
    database may be closing), and the flag is restored after the clean-up. This holds from `onClose`'s
    start: a step refused (`RunNoLongerRunningException`) between its commit and `shutdownNow()` is the
    stop too, never an ERROR or `RUN_CLOSE_FAILED`;
  - `RunNoLongerRunningException` → INFO `Pipeline run <id> is no longer running here; its remaining steps
    were not started`;
  - the run is no longer running (`RunRecorder.isRunning`: the watchdog, a newer instance's recovery) →
    INFO `Pipeline run <id> ended by the watchdog or an interruption; its step ended with <SimpleName>:
    <message>` (the exception itself at DEBUG: a real bug coinciding with the stop is not lost). The
    watchdog's late `cancel(true)` (the stalled step ended by itself with its own exception as `stopStalled`
    committed) can land in this read after the flag was cleared, and the pool refuses the interrupted
    thread: `stillRunning` makes a read that fails on an interrupted thread once more with the flag cleared
    (`cancel(true)` interrupts once), restoring the flag after it; only that second read's failure, or a
    failure without an interrupt, is suppressed in the step's exception and takes the run as running (the
    ERROR below), so the watchdog's stop stays this INFO line;
  - otherwise ERROR `Pipeline run <id> failed`, and the run `failed` with `<SimpleName>: <message>` (the
    text the SPA, `dev/mock-api.js` and the cron read).
  The `finally` clears the flag again first and skips `close` and `runEnded` whenever the instance is
  stopping — also after a normal return (the summary during `onClose`): `onClose` reports every run of its
  own. After the watchdog's interrupt the flag stays cleared: the same thread takes the next run (and the
  executor clears a flag left over before its next task anyway). An interrupt that lands only after the
  `catch` cleared the flag, or after a normal return, is cleared at the start of the clean-up and restored
  only when the instance is stopping. The watchdog's `cancel(true)` can still land inside the clean-up
  (the stalled step ended by itself as `stopStalled` committed), where the pool may refuse `close` its
  connection: `close` tolerates it — its `catch` clears the flag and asks `RunRecorder.isRunning` (through
  `stillRunning`, above); a run
  had already ended (its summary, the watchdog, a newer instance's recovery) is INFO `Pipeline run <id> had already
  ended (its summary, the watchdog or a newer instance); its clean-up here was cut short: <exception>` (the exception at DEBUG), no alert;
  a run still running, or a read that fails too, stays ERROR `could not be closed` and `RUN_CLOSE_FAILED`.
  The interrupt can also land after `close`, in `RunNotifications.runEnded`: `execute` clears the flag
  before the notification and again after it (`late`), restoring it only when the instance is stopping by
  then (the same rule as above); `runEnded` makes an attempt that fails on an interrupted thread once more
  with the flag cleared, restoring the flag after it, and only the retry's failure (the first suppressed in
  it), or a failure without an interrupt, is its ERROR and `NOTIFICATION_FAILED` (Notifications, below).
  Anything that
  escapes `execute` (an `Error`, a failure in its `finally`) is logged by its queue task, ERROR
  `Pipeline run <id> crashed its queue thread` — the `FutureTask` would otherwise keep it silently.
- **`PIPELINE_STALL_TIMEOUT`** (`PipelineStallSettings`, read only by the server): optional, by the one
  duration rule (`settings/DurationSetting`: a whole number with a lower-case unit, e.g. `39m`), from `1ms`
  to `1d`; default `max(15m, TransportRetry.maxCallDuration(LLM_TIMEOUT, LLM_SERVICE_TIER) + 5m)` — the LLM
  check, a run's last posts and a small retry run have one call in flight, which moves nothing until it
  ends. `15m` with the defaults (`120s`, tier `default`), `39m` on production (`300s`, `flex`). A shorter
  explicit value is accepted with a startup WARN naming `PIPELINE_STALL_TIMEOUT` and `LLM_TIMEOUT`; a bare
  number, ISO-8601 (`PT5M`) or a value outside `1ms`–`1d` throws `InvalidSettingsException` from the
  `pipelineStallSettings` bean (`PipelineStallConfiguration`, `@ConditionalOnWebApplication`), and the server's start fails with an `APPLICATION FAILED TO START` block
  (`config/InvalidSettingsFailureAnalyzer`, no stack trace at ERROR). That one bean is injected into
  `RunWatchdog`; `PipelineRunService` (orphans) and `DeploySafetyController` (dead runs) take an
  `ObjectProvider` and resolve it only when asked, so a context without the web server (an operator
  command, the pre-deploy `migrate`) never reads the setting.
- **Notifications.** Every way a run ends calls `RunNotifications.runEnded(runId)` directly (not through
  Spring events: recovery runs before listeners are known to be registered), five places:
  1. `PipelineRunService.execute`, in `finally` after `close` (its own end, an exception; not when the
     instance is stopping, which `onClose` reports);
  2. `PipelineRunService.onClose`, for each run of its own, interrupted (`shutdown`) or just ended (a
     summary that committed as the instance began to stop, which `execute` then no longer reports);
  3. `PipelineStartupRecovery.recover`, for each run it interrupted, after its transaction commits;
  4. `PipelineRunService.stopStalled`, after the watchdog's stop and its interrupts;
  5. `PipelineRunService.cleanUpOrphans` (the start of `queue()`), for each orphaned run it interrupted
     (`lost`), after its transaction commits.
  `runEnded` notifies only a run with a non-empty `PipelineRun.attentionReasons()` (`needsAttention()`, the
  one rule) — `failed` (the breaker's stop
  included), `interrupted`, `succeeded` / `partial` with a source that failed or gave no post
  (`source_failed`, `source_empty`), `partial` with a `dead` post or a technical error (a single provider outage
  included), or `succeeded` / `partial` with skipped elements (a
  `partial` run with only retryable `failed` posts does not: the next run retries them) — one row per admin with `login_enabled`, `ON CONFLICT DO
  NOTHING`, so a run reported twice is notified once. It never throws: a failure is logged (ERROR),
  raised as `NOTIFICATION_FAILED` (below, the class of the failure that counts), and the run's ending, or
  the server's start, goes on. A failure on an interrupted thread (the watchdog's late `cancel(true)`, the
  pool refusing the thread) is tried once more with the flag cleared, the flag restored after; only the
  retry's failure counts. The retry is safe: the rows are `ON CONFLICT DO NOTHING` and
  `RUN_NEEDS_ATTENTION` is raised only when it created rows. The unique key `uq_notifications_recipient_kind_run`
  (`V9`) treats a `NULL` `run_id` as distinct: a future kind without a run needs a new migration with
  `UNIQUE NULLS NOT DISTINCT` (never an edit of `V9`), or its `ON CONFLICT` never fires.
- **The operator's alert of a run (S-18, S-34).** When `runEnded` created notifications (`created > 0`: once per
  run, and only with an admin to notify), it also raises `AlertEvent.RUN_NEEDS_ATTENTION` through
  `alerts/OperatorAlertQueue` (since S-34 one ntfy message per event, `alerts/NtfyAlerts`, topic `NTFY_URL`;
  before it a healthchecks.io check), with the detail
  `Pipeline run <id> (<kind>, cron|admin) ended <status>[ (<cause>)]: dead N, errors N, skipped N[; error:
  <the run's error, cut to 300 characters>]; /admin/runs/<id>`. Two exceptions. A run of the daily cron
  (`PipelineRun.scheduled()`, requested by `SystemAccount.SCHEDULER`) that ended `failed` or `interrupted`
  — the cron reports it already (exit 1, its own check `<env>/pipeline-trigger`), so an alert would only
  double that message. And, since S-34, a run — the cron's or an admin's — whose `attentionReasons()` are
  exactly `[DEAD_POSTS]` (`RunNotifications.reportedByTheNightSummary`): the night's summary counts its dead
  posts per source (`dead N`, or the line `Dead in admin runs since the previous night`), so a message of
  its own would only teach the operator to ignore the channel; the rule `PipelineRun.attentionReasons` is
  untouched, so the bell and the run's page still show it. A dead post next to any other reason is raised
  as before. A cron run ending `partial` with a failed or empty source or a technical
  error, or with skipped elements, exits 0 and is raised (the source's name is in the run's error the
  detail carries); an admin's run is raised for every ending that notifies but the dead-only one. When the cron
  gives up before its runs end (`not ended within` `TRIGGER_RUN_TIMEOUT` 90 min, the API not answering for
  `TRIGGER_WAKE_TIMEOUT` while it waits, no answer to its `POST`), its exit 1 says only that the run did
  not end; a later `failed` / `interrupted` ending of that run is still the cron's, so it shows in `/admin`
  and the bell, not in ntfy.
- **The night's summary (S-34).** After its runs — every run ended, one `failed` / `interrupted`, `not ended
  within` its time-out, or a status read the API answered with another status than 200 or with an unknown
  status (only the API not answering while the cron waits ends it without the call) — the cron calls `POST
  /internal/pipeline-runs/nightly-summary` with their ids (Entries above). `NightSummary.of` (at least one id,
  else `IllegalArgumentException`) reads only, in one read-only `REPEATABLE READ` transaction, so the counters
  of a run still running come from one snapshot: the runs (`findAllById`, sorted by creation), the range run's
  `pipeline_run_sources` and `posts.first_run_id` counts, the retry run's distinct assessed posts
  (`AssessmentRepository.countDistinctPostsBySource`), the night's assessments summed per source
  (`sumBySource`: ok, failed, tokens, `NULL` as 0), the dead posts whose latest assessment is one of the
  night's runs (`PostRepository.countDeadKilledBySource`), and the admin runs created between the previous
  night's last cron run (`findLastScheduledCreationBefore`, `EPOCH` without one) and this night's first
  (`findAdminRunIdsCreatedBetween`) with their dead posts — bounded by creation, never by now, so each admin
  run is in exactly one summary. The queries by run use `ix_assessments_run` and `ix_posts_first_run` (`V14`,
  S-34 review F3), so their cost follows the night, not the history. Before that transaction, in a read-only
  one of its own (S-35; review F1: a failure there costs only the quality lines, never the summary — one line
  `quality 7d (<from>…<to>): unavailable (<the exception's class>)` in their place, an ERROR with the exception
  in the API log, nothing crossed, so no `ASSESSMENT_QUALITY`), the quality of the assessments per source (`posts/AssessmentQuality`, `ZONE` passed in: `posts/` imports nothing
  from here): the window of the night's date (`window`: the last 7 Warsaw days, every run's attempts with a
  model call, the prefilter's left out, against `Source.failureThresholdPercent()`, at least 100 attempts to be
  checked) gives a quality line per source after the sources' lines — `<source> quality 7d (<from>…<to>):
  failed X.X% of N attempts (<class> X.X%, …); threshold T%`, `; ABOVE THRESHOLD` above it, `N attempts, fewer
  than 100, threshold not checked` below the minimum, no brackets without a failed attempt, each class's share
  of all the attempts — and `crossed(window, now, ZONE)` the sources above their threshold tonight that were
  not above it in the window the previous night's summary saw (`[D−7, min(start of D, now − 24 h))`), each
  with that row (`posts/QualityCrossing`): two queries by `assessed_at` (`ix_assessments_assessed_at`, `V15`),
  no stored state, so a source staying above alarms once and shows `ABOVE THRESHOLD` every night. The
  controller raises `NIGHT_SUMMARY` for the range run's id (else the first) through
  `OperatorAlertQueue.raise(ObjectProvider, …)`, always, also with nothing dead, then, only when a source
  crossed, one `ASSESSMENT_QUALITY` (priority 4) for the same id, its detail `Night.qualityAlert()`: per source
  `<source>: failed X.X% of N attempts in 7 days (<from>…<to>), above its threshold T% (the day before: Y.Y% of
  M attempts | M attempts, fewer than 100) (<classes>)`, joined `; `, ending `; /admin` — another event, so the
  queue's repeat window (per event and run) never drops it; and returns the text for the cron's log (the
  route's answer keeps its shape). The format is pinned by `NightSummaryTest`
  (production's night of 2026-11-11, the sources in `Source` order whatever order they come in, a run still
  running, a failed and an empty source, a source without a result, the admin runs' dead posts in `Source`
  order, the quality lines in each state, the line `unavailable` and the `ASSESSMENT_QUALITY` detail, every
  source failing with the
  longest error and every failure class staying within ntfy's bytes), the queries by `NightSummaryIT` on
  Postgres (two sources with a retry and a range still running, the first night counting the admin's dead
  posts from the beginning and not those of a later run, the quality lines from seeded attempts with one
  source crossing and the other above two nights in a row, an unknown run and a repeated id, no id refused),
  the window and its rule by `posts/AssessmentQualityTest` / `AssessmentQualityIT`, and the route by
  `api/InternalPipelineRunIT` (returned and raised for the range run, `ASSESSMENT_QUALITY` once after it for a
  crossing and never without one, the summary still raised with the line `unavailable` when the quality window
  throws, 400, 404) and `InternalPipelineRunWithoutTokenIT` (401). Runbook (what to do
  after `ASSESSMENT_QUALITY`, changing a threshold): `context/deployment/deploy-plan.md`, section S-35.
- **Operator alerts of the error paths (S-18).** Each path that ended in an ERROR line nothing else reported
  now also raises an `alerts/AlertEvent` through `OperatorAlertQueue.raise(event, runId, detail)`; the
  ERROR line stays:

  | Path | Event | Run in the event |
  | --- | --- | --- |
  | `RunNotifications.runEnded` fails (on an interrupted thread: its attempt with the flag cleared fails too) | `NOTIFICATION_FAILED` | the run |
  | `PipelineStartupRecovery.recover` fails | `RECOVERY_FAILED` | none |
  | `PipelineRunService.close` fails for a run still running, or whose state cannot be read even with the flag cleared (not for one that had already ended: INFO) | `RUN_CLOSE_FAILED` | the run |
  | `PipelineRunService.onClose`: interrupting this instance's runs fails (ERROR `The pipeline runs of this instance could not be interrupted on shutdown: [ids]`; raised only when it had runs) | `RUN_CLOSE_FAILED`, the ids in the detail | none |
  | `RunWatchdog`: a check throws | `WATCHDOG_FAILED` | the run (so once per repeat window and run) |
  | `PipelineRunService.cleanUpOrphans` fails | `ORPHAN_CLEANUP_FAILED` | none |
  | `ConcurrentAssessmentDispatcher`: `RunRecorder.count` failed for results of one `dispatch` (audit A5) | `RUN_COUNTERS_LOST`, once after every post has finished, with ERROR `N post result(s) of run <id> could not be added to its counters (D dead, E technical error(s)); its counters are understated and needsAttention may be false` | the run |

  Two events are no error path and not in this table: `NIGHT_SUMMARY` (S-34) and `ASSESSMENT_QUALITY` (S-35),
  both raised by the night's summary route (above).

  The detail says what failed, the run, the exception's class (never its message: a database exception
  may carry SQL and values) and what happens next, e.g. that the next start request cleans up after
  `PIPELINE_STALL_TIMEOUT`, ending `details in the API log`. The queue exists only in the server
  (`alerts/AlertsConfiguration`): the beans an operator command's context shares (`PipelineRunService`,
  `RunNotifications`, `ConcurrentAssessmentDispatcher`) take an `ObjectProvider<OperatorAlertQueue>` and call
  the static `OperatorAlertQueue.raise(provider, …)`, which does nothing without the bean; the web-only
  `PipelineStartupRecovery` and `RunWatchdog` inject it. `onClose` runs on `ContextClosedEvent`, before the
  beans are destroyed, so the queue still takes its event; `executor.shutdownNow()` is in `finally`.
  Not raised on their own, by design: a run's crash in `execute`, a failed LLM check, a post's error —
  they end the run, which `RUN_NEEDS_ATTENTION` or the cron reports.

## Rules

- Every state change is one conditional `UPDATE` whose row count says whether it happened — no row
  locks, no blind `save()` of a run or a post. A worker of an interrupted run (deploy overlap) must
  never change a post another run took over.
- **Attempts: three faults** (S-09, `ConcurrentAssessmentDispatcher`):
  - *the post's fault* — the provider refused the request (a non-retryable error) or the model's answer
    broke the contract: `Assessor` returns a `failed` result, its row is stored and the attempt counts;
    the third makes the post `dead` (`state/PostTransitions.Outcome`), which no run takes any more: only an
    admin's restore (`PostTransitions.restore`, S-33) brings it back, `stored` with all three attempts
    again. Only this uses an attempt. A refusal on its own is this fault, but three in a row (S-32) are
    most likely the provider's doing and would go through every post's attempt, so they stop the step
    (the refusals' breaker, below);
  - *the provider's fault* — `LlmUnavailableException` (retries used up on 429/5xx/time-out/connection
    errors, or interrupted between attempts; `llm/CLAUDE.md`), which `Assessor` throws like
    `LlmConfigurationException`: nothing is stored, the post is released (`release`), no attempt;
  - *our code's fault* — any other exception (a mapper, an unknown SDK exception in a client, a failed
    write): nothing is stored, the post goes back with `releaseFailed` (one more technical failure, its
    message kept), and the run counts a processing error (`RunFlow.AssessedBatch.errorSummary` becomes
    the run's error). An error the LLM client could not classify (`llm/LlmClientException`, e.g. an
    unknown SDK error after an SDK change) is this fault too, but most likely systematic, so a series of
    them stops the step (the client's breaker, below) instead of going through every post of the run.
- **The breaker.** `OUTAGE_STREAK` (3, a constant, no variable) outages in a row stop the step: no
  further post is taken, `stoppedBy` = `the LLM provider is unavailable: 3 calls in a row failed after
  their retries; the last: …`, so the run ends `failed`; the remaining posts go back without an attempt
  or a technical failure. "In a row" is in the order the calls end (one counter per step across the
  pool's threads); a stored result whose call reached the model (`provenance.attempts() >= 1`, ok or
  `failed`) resets it; the prefilter makes no call and does not reset it, nor does an error of our code.
  An outage of a task `cancel()` (watchdog), an interrupted `await` or the pool's shutdown cancelled is
  `NotTaken`, never counted (the pool's shutdown sets no mark: a task ending on an interrupted thread is
  taken as cancelled — the transport and the HTTP client leave the flag set).
- **The client's breaker** (S-17, F7 of S-09). `CLIENT_ERROR_STREAK` (3, a constant, no variable)
  `LlmClientException`s in a row stop the step the same way: no further post is taken, `stoppedBy` = `the
  LLM client failed 3 calls in a row with an error that is not the provider's; the last: <SimpleName>:
  <message>` (one WARN), so the run ends `failed` and its `assess` step `failed` (signals as for any
  `failed` run). Unlike the outages, each of the streak's posts is still our code's fault: put back with
  `releaseFailed` (one more technical failure, so the third run in a row puts it on hold) and a processing
  error of the run; the remaining posts go back without an attempt or a technical failure, and posts whose
  calls were already in flight on other threads end as usual. "In a row" is in the order the calls end (one
  counter per step, next to the outages'); a stored result whose call reached the model
  (`provenance.attempts() >= 1`) resets it, as it resets the outages; an outage, the prefilter and every
  other exception (a mapper — e.g. a plain `IllegalStateException` from `AssessmentInputs` —, the prompt,
  the parser, a write, a plain `IllegalStateException` from the client) neither add to it nor reset it. A
  client error of a task `cancel()`, an interrupted `await` or the pool's shutdown cancelled is `NotTaken`,
  never counted. The
  check before the step (`llm.check()`) is outside the streak: any exception there already takes no post.
- **The refusals' breaker** (S-32, R-41). `REFUSAL_STREAK` (3, a constant, no variable) refusals in a row
  stop the step the same way: a refusal is a stored `failed` result of the call itself
  (`AssessmentResult.callFailed()`: `FailureClass.fromCall()`, `provider_error` / `timeout`, built only by
  `Assessor.failed` — the request rejected by the provider, a blocked answer, an answer without text, a
  time-out of the last attempt; `llm/CLAUDE.md`), never a contract violation of the model's text. After the
  third is stored: no further post is taken, `stoppedBy` = `the LLM provider refused 3 calls in a row; the
  last: <the last refusal's message>` (one WARN), so the run ends `failed` and its `assess` step `failed`
  (signals as for any `failed` run: the bell, `RUN_NEEDS_ATTENTION` for an admin's run, exit 1 for the
  cron's). The three posts of the streak keep their stored result and used attempt (the result is stored
  before the stop is decided, as for any failed result; a third attempt still makes a post `dead`, which
  FR-034 restores), the remaining posts go back without an attempt or a technical failure, and posts whose
  calls were already in flight on other threads end as usual (so on `PIPELINE_LLM_CONCURRENCY` threads up
  to `3 + threads − 1` refusals are stored). "In a row" is in the order the calls end (one counter per
  step, next to the outages' and the client's); a stored result with the model's text (ok or a contract
  violation) resets it; a refusal still resets the outages' and the client's streaks (the provider
  answered); an outage, a client error, any other exception (a mapper, a write) and the prefilter neither
  add to it nor reset it, and a result `record` refused (the run no longer holds the post) counts nothing.
  The check before the step (`llm.check()`) is outside the streak, as for the other two.
- **Deferred counting of single outages.** A post's outage is counted only after `dispatch` has awaited
  every task, and only when nothing stopped the step (`stoppedBy == null`): the breaker may trip after
  that post was put back. Then `countTechnicalFailure` (only while the run is still `running`: a run a
  deploy, `onClose` or the watchdog ended meanwhile counts nothing) and only for a post this task put
  back itself (`release` returned true); when it changed the row, the run counts a processing error and
  the post joins `errorSummary`, so the run ends `partial`. Otherwise the outage counts like `NotTaken`.
- **On hold.** `posts.technical_failures` (`V10`) counts technical failures in a row — errors of our code
  and single outages alike — with `last_technical_error`; any stored result (ok or `failed`) resets both
  (`record`). At `Post.MAX_TECHNICAL_FAILURES` (3) the post is on hold: it keeps its stage (`stored` /
  `failed`), `PostRepository.findLeftoverIds` / `existsLeftover` skip it (the RETRY run never takes it,
  and it alone queues no RETRY), a POSTS run (an admin's "Ponów zaznaczone"; dead posts get "Przywróć
  martwe" instead, `POST /api/admin/posts/restore`) still takes it, and
  `GET /api/admin/posts/failed` lists it with `technicalFailures`, `lastTechnicalError`, `onHold`.
- Code that runs work in parallel passes its own executor (`availableProcessors()` may be 1).
- **A source's failure or silence is its own result** (S-20). `SourceIngest.fetch` fetches every source of
  a range run, one after another: a source that threw is `failed` (`<SimpleName>: <message>`), one that gave
  no post to store (no element in the range, or only skipped ones) `empty`, one with a post `succeeded`;
  `RunSequence.fetch` writes each result as the source ends (`RunRecorder.source`: the row in
  `pipeline_run_sources`, the run's `fetched`, `skipped`, `sources_failed` / `sources_empty`, its activity),
  so the counters are written before a fetch fails too, and the step writes no sums of its own. The step
  fails only when no source gave a post: `NoPostsFetchedException` names each source with its result, and
  the run ends `failed` (notification, cron exit 1, the feed's dates stay). Otherwise the posts of the
  sources that gave some are stored and assessed, and the run ends `partial` when its assessment
  succeeded but a source did not, with the reasons `source_failed` / `source_empty` (the bell,
  `RUN_NEEDS_ATTENTION` as for any `partial` run, cron exit 0) and the run's error `<source> failed: …` /
  `<source> gave no post to store (fetched N, skipped M)` (joined `; `), followed by ` | ` and the
  assessment's error when it has one. A stop between sources: the checkpoint before the next source ends the
  step, and the source the stop interrupted gets no row (whoever stopped the run wrote first). Retry and
  posts runs fetch nothing and are not affected. Skipped elements next to posts are only a warning: the WARN
  line and `needsAttention`.
- **A post the database rejects at store is skipped, never silent** (S-38). A post whose save fails with a
  `DataIntegrityViolationException` other than its unique key (a `CHECK`, `NOT NULL`, a foreign key, a data
  error — e.g. a future migration's constraint or a new source's value) is counted as `skipped` in the run and in
  its source's row (`RunRecorder.storeRejected` after the store step), named in the run's error
  (`store rejected …`) and in a WARN line; so it gives the reason `skipped` (the bell, `RUN_NEEDS_ATTENTION`, the
  night's summary) without changing the run's status, nor its source's (`succeeded`). For every range run whose
  store step succeeded, and each of its sources, `fetched = new + duplicates + skipped` (the store's INFO line
  gives new and duplicates; duplicates are not stored). It is stored by a later range run over its day, once the
  cause is fixed (`context/deployment/deploy-plan.md`, section S-38).
- A step with side effects runs once; retries live only below the step (`llm/transport/TransportRetry`, a
  source's client such as `sources/wykop/WykopApiClient`), never around it: a repeated step would repeat
  its writes and be recorded again (seen under a retrying framework: a failed Wykop fetch repeated every
  10 s and more). `RunSequence` calls each step once and lets its exception end the run;
  `RunSequenceTest` pins it.
- No run stays `queued`/`running` without a thread: `PipelineRunService.execute` ends it `failed` on
  any exception; start-up recovery interrupts unfinished runs and puts back every post `assessing`
  without a queued or running run (`PostTransitions.releaseOrphans`), also one claimed just as its run
  was interrupted by a stopping instance. A run of this instance that hangs (no progress for
  `PIPELINE_STALL_TIMEOUT`) is stopped by `RunWatchdog`, so it never blocks the queue. A run nobody
  executes any more (its instance lost without `onClose`, a run queued on the old instance after the new
  one's recovery, a `submit` that never reached the executor, a failed start-up recovery) is cleaned up
  on request (S-11). The `submit` case exists today (A8): `queue()` and `submit` do not read `stopping`,
  so a request whose runs commit after `onClose`'s `shutdownNow()` gets `RejectedExecutionException` from
  the executor after the commit — HTTP 500, `PipelineRunExceptionHandler` maps nothing for it — and its
  run stays `queued` until this clean-up, once `PIPELINE_STALL_TIMEOUT` has passed since its
  `created_at`, or start-up recovery interrupts it. Every request to start runs (`queue()`: the cron's and the admin's) first runs
  `cleanUpOrphans` in its own transaction under the same advisory lock —
  - only when this instance's `own` is empty (its own runs are its watchdog's), `RunRecorder.interruptOrphans`:
    one statement marks every queued and running run `interrupted` (`lost`, open steps `interrupted`)
    when none of them has `COALESCE(last_activity_at, started_at, created_at)` within
    `PIPELINE_STALL_TIMEOUT` by the database's clock (`created_at` is the application's clock, a negligible
    difference) — all or none, the queue being one: a queued run
    behind a dead running one is as orphaned, one run with progress means a living queue, and so does any
    run that ended within the timeout (between a long run's end and the start of the run queued with it,
    that run shows only its old creation); the same age
    as the watchdog's; one edge is accepted — the `NOT EXISTS` is evaluated once on the statement's
    snapshot, so a live run of another instance (deploy overlap only) whose progress commits at the
    threshold's edge while it runs is still marked `lost` (no row locks, Rules);
  - always `PostTransitions.releaseOrphans`, so a post left `assessing` by an ended run goes back at
    every request to start runs, not only at start-up;
  - committed before the request is decided — `startRange` / `startPosts` run it first, before any
    refusal — so it stays when the request is then refused (503 `source_not_configured`, 409
    `pipeline_busy`, 400 `post_dead`), and the refusals keep their order; after the commit WARN `Marked N orphaned pipeline run(s)
    interrupted: no queued or running run showed activity for <t> and none runs on this instance: [ids]`,
    WARN `Put back N post(s) left assessing by ended runs` and `runEnded` for each run.
  Nothing else asks: no timer, so an idle API still sleeps; reads (`/admin`, `/api/deploy-safety`) never
  write.
- Whoever ends a run reports it to `RunNotifications.runEnded`; a new way to end a run adds that call. `state/RunEndingTest` guards it: every public method of `RunRecorder` is classified (ends a run, with tests that check its notification, or does not, with a reason), and the named tests must exist (`state/RunEndingGuard`, red paths in `RunEndingGuardTest`). A new public method fails it until it is added to `ENDS` or `NOT_ENDING`; it proves the tests exist, not what they check, and does not see a new caller of an existing method or a write of a run's end outside `RunRecorder`.
- A new path that ends in an ERROR line and that nothing else reports (not a run's ending: `runEnded`
  covers those) gets its own `AlertEvent` constant, whose description says what to check, and calls
  `OperatorAlertQueue.raise` next to that line (S-18, above). Never `OperatorAlerts` directly (it blocks
  for an HTTP request and sends nothing from an interrupted thread), never a Logback appender
  or an alert per ERROR line; the detail carries no secret and no exception message.
- Start-up recovery runs before the web server opens its port (`SmartInitializingSingleton`, not
  `ApplicationReadyEvent`): it interrupts every queued or running run without asking who created it,
  and the cron's `POST` is often the very request that wakes the API, so a later recovery would
  interrupt the run that request just queued. `PipelineStartupRecoveryIT` checks the order; keep it
  before `WebServerInitializedEvent`.
- The root package holds only what other packages (the API in S-02 phase 4) call; the subpackages are
  the run's internals.

## Tests

- **The engine's tests do not measure the machine.** A wait for an event (a latch, a held LLM call, a
  Wykop gate) has no bound of its own: it sits under the class's `@Timeout` — `@Timeout(60)` on
  `PipelineRunServiceTest`, `RunWatchdogTest`, `assess/ConcurrentAssessmentDispatcherTest`;
  `@Timeout(value = 5, unit = TimeUnit.MINUTES)` on `RunWatchdogIT`, `PipelineRunIT`, `OrphanedRunIT`,
  `LlmOutageIT`, `api/PipelineRunControllerIT`, `api/InternalPipelineRunIT`, `scenario/PrEnvScenariosIT` — and a hang ends there with a
  dump of every thread on stdout (the CI `backend` job's log): `src/test/resources/junit-platform.properties`
  sets `junit.jupiter.execution.timeout.threaddump.enabled=true`, with no default timeout (only a class
  that declares `@Timeout` has one). A duration is not an assertion when the property can be said as an
  order: "the step ended while the held call still had no answer" (`answered.getCount() == 1`, the call
  saw the interrupt), not "within 10 s"; "the dispatcher registered every task" (a test pool whose futures
  release a permit on `get()`), not a pause — every test that calls `cancel` waits for it, since a call can
  start before its future is registered and `cancel` reaches only registered ones. What keeps a bound: a pool's `awaitTermination` (a day,
  asserted) and the IT helper loops that poll the database or the log (`awaitLog`, `awaitClosed`, `settle`,
  `finished`, `awaitIdle`), far longer than their wait, so a red one says what it waited for. Only the watchdog's own
  scenarios measure time, against its threshold, each with its margin. A new test of the engine keeps this.
- Unit (`*Test`, no Spring): `flow/RunSequenceTest` (each kind of run's steps in order and each once,
  each source's result written inside the fetch step in order and no sums of the step's own, the store's rejected
  posts counted per source in `Source` order after the new posts and none without them, the run's error in its
  three parts (sources ` | ` store rejected ` | ` assessment) and `store rejected …` alone with a run still
  `succeeded` (S-38), a long failed source's summary cut next to rejected posts so the store's part stays within
  2000 characters, and not cut without them, a failed
  source next to a clean assessment `partial` with the sources' summary as the error, the summary and the
  assessment's error joined ` | `, a fetch without posts failing its step with no later step, the store step counting new posts, the assess step a stopped dispatcher ended closed `failed` with
  its reason and the run's error set, the summary step closed before the finish and `INTERRUPTED` when the
  finish changed nothing, a step's exception ending the run with no later step, a refused step ending the
  sequence), `PipelineRunServiceTest` (with a mocked `RunSequence`: a run's own end without ERROR, a step's
  exception an ERROR and `failed` with `<SimpleName>: <message>`, one after someone else ended the run an
  INFO, a stopping instance neither closing nor reporting its run and restoring the interrupt flag — also
  for a refused step, also while `onClose` is still writing (before `shutdownNow()`) —, `onClose`
  reporting every run of its own, one that had already ended too, a close the watchdog's late interrupt
  cut short an INFO without an alert (one of a run still running, or unreadable, still raised), the same
  late interrupt cutting short the step's check whether the run still runs read once more with the flag
  cleared — an INFO, no ERROR, no alert, the run closed with the default error, the flag not kept
  (`aLateInterruptOfTheWatchdogCuttingTheStepsCheckShortIsAnInfoWithoutAnAlert`) —, an interrupt the
  notification restores not kept for the next run, a run no
  longer running an INFO, an `Error` escaping `execute` logged by its queue task; `stopStalled`: its
  writes, then the interrupt of the run's thread, then `AssessmentDispatcher.cancel`, then the
  notification, nothing when the stall changed nothing; and the clean-up and alert paths below), `state/RunOutcomeTest`, `state/DurationsTest`, `step/PostStoreTest` (S-38: a post stored meanwhile a duplicate after its key is read again, one without its key rejected with its cause and one WARN without the exception's message, a chunk with a duplicate and a rejection storing the others once, a post repeated in the fetch a duplicate, the cause from a constraint's name, a `NOT NULL`'s column, `SQLState 22021` or `unknown`, the summary listing 10 and `; and 2 more`, `SourceStored` refusing counts that do not add up, `posts = new + duplicates + rejected` in each test),
  `step/AssessmentSelectionTest`, `step/SourceIngestTest` (two sources with the scripted
  `sources/ScriptedSourceFetcher` of the tests, its only user, as the ITs run the real sources: posts of
  both, one empty, one throwing — a WARN with the
  exception —, both without a post naming both after reporting both, one not configured any more, an
  interrupt before the second source fetching it not), `assess/ConcurrentAssessmentDispatcherTest`
  (threads, one client, a technical error; a bug before the call puts the post back with
  `releaseFailed` and no attempt; one `count` per stored post with its retries and flex
  fallback, `processingError`, nothing for a post not taken; a failed `count` keeps a stored post
  stored; `cancel` interrupts the run's call in flight, skips its post not started and leaves another
  run's call alone (called only once both steps wait for their posts: a test pool whose futures release a
  permit on `get()`, i.e. after the dispatcher registered every task); the concurrency's lower bound from a
  latch (`overlap`) that holds the first two calls until both are in flight; a dispatch whose thread is interrupted while it waits marks its tasks cancelled before
  interrupting them, so the call counts nothing and is no technical error, and throws; the progress line; the breaker: three outages in a row stop the step with no
  attempt, no technical failure and no processing error, single outages between answered calls are
  counted once the step has ended (`partial`), a refused call ends the streak, a prefilter post does
  not, an outage of a call the watchdog cancelled and one of a run no longer running count nothing, every
  outage moves the run's activity (`touch`), an outage's post whose first put-back write fails is put
  back on the way out; a check failing with an error that is not the provider's takes no post, and one interrupted by the run's
  stop ends the step with `RunInterruptedException` (the check's exception its cause) and no ERROR; an
  outage or a client error of a call the pool's shutdown interrupted counts nothing and starts no streak; the
  client's breaker: three `LlmClientException`s in a row stop the step after the third, each of the three
  put back with `releaseFailed` and a processing error, a stored result between them resets the streak,
  an error outside the client (a plain `IllegalStateException` building the input or from the client)
  neither adds to it nor resets it, nor does an outage, and a client error of a cancelled call counts
  nothing; the refusals' breaker: three refusals in a row stop the step after the third is stored with
  its attempt (the posts after it never claimed, no technical failure, one WARN, no ERROR), a contract
  violation and a valid answer between refusals each reset the streak, a prefilter post does not, an
  outage and an error of our code between them neither add to it nor reset it (the single outage is not
  counted, as the step stopped), and a refusal still resets the client's streak (the outages' streak is
  covered by `aRefusedCallEndsTheStreakOfOutages`); `state/RunOutcomeTest` that its stop fails the run
  with both the stop and the posts' errors),

  `run/PipelineRunTest` (`attentionReasons` for every status, with and without failed or empty sources and
  skipped elements, in the rule's order; none for a new run),
  `run/MissingDaysTest` (the plan's example, the window D-15…D-2, a source's first covered day, a source
  never covered or not configured, two sources with different gaps, overlapping ranges, a one-day run on
  the 25-hour day, the gap one bad night shows just after midnight),
  `PipelineStallSettingsTest` (default from the LLM settings, WARN below it, units, refusals),
  `RunWatchdogTest` (no database call without a run of its own, a failure and an `Error` logged, a raise
  of `WATCHDOG_FAILED` that throws an exception or an `Error` logged without stopping the other runs' checks,
  the checks going on after an `Error` (waiting on a latch of three checks), the check interval); `llm/transport/TransportRetryTest` covers
  `maxCallDuration`; `agents/assessor/AssessorTest` that `LlmUnavailableException` is thrown, not a
  `failed` result.
- `state/RunRecorderIT` (on Postgres, each test rolled back; runs inserted with SQL): every guard of
  `RunRecorder` on its own — `start` only from `queued`; `finish`, `count`, `touch` and `error` only in a
  running run (`finish` keeping the error set while running); `fail` from `queued` or `running` (closing an open step `failed` with the same error), never
  from an ended run; a step of a run that is not running refused before its work, adding no row; the
  step's end as its work decides (`succeeded` with its count, `failed` with a `StepOutcome`'s reason — A6
  —, `failed` with its exception's message); a step an interruption closed left as it is; `stall` only
  after the threshold, its open steps `failed` with the same error; `interrupt` with its cause and its
  open steps `interrupted`; `interruptAll` naming `deploy` / `restart` by `deployment_id`;
  `interruptOrphans` all or none (both cases; the rest in `OrphanedRunIT`); `source` only in a running run
  (its counters, activity and row), a second row of a source refused, a long error cut; `storeRejected` (S-38)
  adding to the run's and the source's `skipped` and moving the activity, the other source's row unchanged, nothing
  in a run that is not running and nothing without the source's row.
- `step/PostStoreIT` (S-38, on Postgres, not rolled back: each test deletes its rows): a chunk of three posts with
  an empty channel rejected by `ck_posts_channel` — named, its raw payload rolled back, the other two stored once, the
  log without `Failing row` and without the post's title or body —
  and a real `uq_posts_source_item` hit (the first `findKeys` of a spied `PostRepository` missing the stored post) a
  duplicate, not a rejection.
- `posts/PostRepositoryIT` (outside this package, on Postgres): the RETRY predicates (a post on hold is
  no leftover, one technical failure short of it is), `claim` only of a `stored` / `failed` post and only
  for a running run (returning its attempts), `releaseAll` only of that run's posts, `releaseOrphans`
  putting back a post claimed just as its run was interrupted and leaving the posts of a queued or
  running run, `releaseFailed` counting only for the run holding
  the post, a long error cut and without U+0000, `record` resetting the count, `countTechnicalFailure`
  only on a post put back while its run is running, `restore` only of a `dead` post among every stage (the
  count, a second call 0, its `assessments` rows kept, the post a leftover again). `state/PostColumnsIT` (on Postgres, not rolled back):
  every `posts` column classified, and each transition's full row on a post with technical failures — the
  state columns as above, every other column unchanged, `updated_at` moved (`restore`: a dead post of an
  ended run). `posts/api/PostsApiIT`: the failed list with posts on
  hold and `failed` posts with technical errors; `POST /api/admin/posts/restore` — a dead post restored
  (`{"restored":1}`, off the failed list, its attempts kept, no longer dead for a posts run), only the dead
  ones of a mixed list, a repeat `{"restored":0}`, 400 `invalid_request` for an empty list, 101 ids, a
  `null` id or no `postIds`, allowed while a run is running.
- `api/InternalPipelineRunIT` (+ `InternalPipelineRunWithoutTokenIT`): the token, no session and no
  cookie on `/internal/**`, the D-2…D-1 range for a fixed `Clock` (also a DST day), 409, 404, the exact
  keys of `GET /internal/pipeline-runs/{id}` with `step` and `assessTotal` during the assessment; since S-34
  `POST /internal/pipeline-runs/nightly-summary`: the text returned and `NIGHT_SUMMARY` raised for the range
  run (a mocked `OperatorAlertQueue`), 400 without 1–10 ids, 404 for an unknown run, 401 without the token.
  `NightSummaryIT` (the summary's queries on seeded rows) and `NightSummaryTest` (the text) are listed under
  "The night's summary" above.
  `api/PipelineRunControllerIT`: the run page, 404, the new fields, `scheduled` for the cron's run; a dead
  post refused (`post_dead`), restored, then assessed by a posts run with one `assessments` row more.
  `api/FreshnessIT`: which runs move each source's date in the label. `api/MissingDaysIT` (fixed `Clock`): which runs cover a day
  (`failed`, `interrupted`, `queued`, `running` and RETRY/POSTS runs do not; `partial` and an admin's do;
  in a `partial` run with a second source — Reddit on `sources/reddit/LocalReddit`, configured only there —,
  only the source whose result is `succeeded`), the first covered day from the sources' results, the exact
  JSON; `api/PipelineRunWithoutSourceIT` also that no
  day is missing without a configured source. `api/DeploySafetyIT`: every answer (a running run
  outranks a queued one, ended runs are safe; a dead running run is safe and its row unchanged, a dead
  queued run is safe, a running run with recent activity is not, nor a live queued run behind a dead
  running one), no session and no cookie, 503 without the database.
  `PipelineStartupRecoveryIT`: recovery before the port, the cause `deploy` / `restart`, steps
  `interrupted`, notifications; a recovery while the old worker lives (it takes no new post, its summary,
  late result, clean-up and release change nothing of a post a new run took).
- `OrphanedRunIT` (`PIPELINE_STALL_TIMEOUT=60s`, which no scenario waits for: a dead run is
  `now() - 1 hour`, "activity 2 s ago" keeps 58 s of margin, and the WARN reads `… activity for 1m 00s …`;
  "another instance's" runs are rows inserted with SQL, `deployment_id = 'deployment-other'`): a dead running run with an open step and a held post plus a
  queued run behind it → 202, both `interrupted` / `lost`, the step `interrupted`, the post `stored`, two
  notifications, both WARN lines, new runs created; a lone orphaned queued run; a run with activity →
  409, unchanged; a dead running run next to a fresh queued one → 409, nothing cleaned up (all or none);
  activity 2 s ago → kept; a run of this instance in progress → 409, the dead foreign row unchanged; a post
  left `assessing` by an ended run put back although the request gets 409 or 400 `post_dead`; a queued run
  behind a run that just ended → 409, kept (a living queue); `PipelineRunServiceTest` (mocks): a failing
  clean-up is an ERROR and the request is still decided, 503 `source_not_configured` comes after the
  clean-up, a run of this instance's own keeps `interruptOrphans` from being asked; the admin's
  `POST /api/admin/pipeline-runs` cleans up the same way. In `OrphanedRunIT`, `api/PipelineRunControllerIT`
  and `api/InternalPipelineRunIT` the local Wykop holds a run in its fetch on `wykopGate`, which waits
  without a bound; an interrupted gate answers HTTP 503, never an empty day, and `awaitIdle`
  (`@AfterEach`) opens it first. `MigrationsIT` (outside this package) accepts
  `lost` and still rejects an unknown cause.
- `RunWatchdogIT` (`PIPELINE_STALL_TIMEOUT=5s`, `PIPELINE_LLM_CONCURRENCY=1`, a scripted `LlmClient`
  held on a latch without a bound; scenarios independent, in a random order, as `PipelineRunIT` below): a
  hanging run ends `failed` with its `assess` step closed and its posts back without an attempt, its held
  call is interrupted, so the run queued behind it assesses on the only LLM thread, the stopped step ends
  while the held call still has no answer ("… after its run had stopped", `answered.getCount() == 1`; an
  order, not a duration) and stores nothing, a
  slow run with progress (a post every 1.5 s) is not stopped, nor is a
  run whose calls each find the provider unavailable after a 2 s pause (each under the 5 s threshold,
  three in a row at least 6 s, longer than it: the breaker ends the run, not the watchdog), another
  instance's run is left alone, the admins are notified, a run of two sources each taking about 3 s (Wykop
  paused, Reddit's one search on `sources/reddit/LocalReddit` delayed — `LocalReddit.delay`, its arrival
  seen by the server's request count) is not stopped though its fetch step lasts longer than the threshold
  (the run's last activity is the first source's write while the second is fetched), and a database
  connection has the 300 s `socketTimeout`.
- `notifications/api/NotificationsIT`: recipients (admins only), idempotence, which run endings notify,
  the list, marking one and all read, 404 for another admin's. `notifications/RunNotificationsTest`
  (outside this package, mocks) also covers the late interrupt: an attempt it cut short made once more
  with the flag cleared (the admins notified, no ERROR, no `NOTIFICATION_FAILED`, the flag restored), a
  second attempt failing on an
  interrupted thread too raised as `NOTIFICATION_FAILED`, a failure without an interrupt not tried again.
- `PipelineRunIT`: the whole sequence on Postgres with a source on a local server and a scripted
  `LlmClient` — range, the same range again, a leftover's one attempt per request, a failing source
  (`failed`, the queue takes the next request), a range without elements (`failed` at `fetch`, one search
  per resource, the feed's dates unchanged), a range of unmappable elements (`failed` with its counters),
  some skipped elements (`succeeded`, needs attention, the WARN and end lines), a stopping instance
  (`onClose`, cause `shutdown`, step `interrupted`, its queue thread ends the step while the held call
  still has no answer and the call saw the interrupt, neither closes
  nor reports the run again and logs no ERROR), a refused LLM key (`failed`, no attempt), a provider
  unavailable for three calls in a row (`failed` with the breaker's error, no attempt, no technical
  failure, the admin notified), a provider refusing every call on every model-assessed post of the day
  (S-32: `failed` with the refusals' breaker's error and the last refusal's message, 3 to 6 refusals
  stored — the 4 threads — each a `provider_error` result and its post's one attempt, equal to the run's
  `assessed_failed`, every other post `stored` without an attempt or a technical failure, no `dead`, no
  processing error, the admin notified) and an outage of a run a stopping instance interrupted (the interrupt ends
  the held call before its answer, as the stopped run's, at INFO, counting nothing); two sources — Reddit
  is its production client, repository, fetcher and mapper on `sources/reddit/LocalReddit` (a
  `RedditRepository` bean over `LocalRedditServer` answering the real, anonymized Arctic Shift pages of
  `fixtures/reddit/`, configured only by these scenarios; no test sets `REDDIT_SUBREDDITS`): Reddit down
  (`partial`, `source_failed` with its client's error after 3 attempts, Wykop's posts assessed and stored
  with their channel `gielda`, the admin notified and `RUN_NEEDS_ATTENTION` raised), Reddit empty
  (`partial`, `source_empty`), Wykop down — gate F1 (S-20, closed in S-27): every Reddit post stored with
  its subreddit as `channel` and assessed on the input its raw payload maps to, equal to the production
  `RedditMapper`'s input of the fixture element with that id (item type from `is_self`, the body its
  `selftext`), and Arctic Shift asked for the run's range one second wider at each end —, removed Reddit
  posts (r/stocks: 19 of 25) neither fetched nor skipped (only the visible ones stored, no `skipped`
  reason, no notification, counted in the log line `… removed or deleted left out …`), the same range
  again storing neither source's posts twice, both without a post (`failed` at `fetch`, naming both, a
  row each) and a run stopped while Reddit's search is held (no row for it, Wykop's row kept, the
  interrupt ending the search at INFO without a second attempt, no ERROR); a held call or search waits
  without a bound (only an interrupt or `settle()` ends it; `LocalReddit`'s client time-out, 60 s, is
  far above any wait); a post rejected at store (S-38: a `CHECK` added for the scenario, `ck_s38_rejects_one`,
  refusing one entry of the day, dropped in `finally` — `succeeded`, `skipped` 1 on the run and Wykop's row, the
  reason `skipped`, the run's error `store rejected 1 post(s), …`, the WARN line and no ERROR, neither the post nor
  its raw payload stored, the admin notified and `RUN_NEEDS_ATTENTION` raised with `skipped 1` and `store rejected`
  in its detail, read through a spied `OperatorAlertQueue`); `fetched = new + duplicates + skipped` per source and
  per run (the store's INFO lines) in that scenario, the first range run, the same range again (both runs) and Reddit
  down; counters and
  `last_activity_at` growing during the assessment; no new step in an interrupted run and no ERROR in the
  log. Each scenario stands alone, in a random order (`@TestMethodOrder(MethodOrderer.Random.class)`, as
  `RunWatchdogIT`): before each one `reset()` checks that nothing runs, empties the run and post tables
  in one `TRUNCATE` (the seven tables are closed under the foreign keys; users, sessions and Flyway's history
  stay) and switches Reddit off (`LocalReddit.reset()`); a scenario that needs assessed posts runs `assessedDay()` itself; after each one `settle()`
  releases the held calls and waits until no instance has a run of its own and the LLM pool is idle
  (`AssessmentPool.idle()`) — a
  run's status is written before its queue thread closes it, so `finished()` alone returns too early. The
  seed comes from `-Dit.seed` (Failsafe, `pom.xml`; by default a number of the build's time) and each
  class logs it at INFO: `./mvnw -B verify -Dit.seed=<n>` repeats a red order. A new scenario keeps that:
  no reliance on another scenario's data, no `@Order`. Re-stub the `@MockitoBean` with
  `doAnswer(…).when(…)`, not `when(…)` (which calls the old answer with `null`).
- `LlmOutageIT`: the breaker through the real transport — the client from `LlmClientFactory` (DeepSeek) on
  `ScriptedProviderServer`, the check answered, then HTTP 500 to everything; a RANGE run ends `failed`
  after the check and 3 × 3 HTTP requests, with no model assessment, no attempt, no technical failure and
  the admin notified. The only test of transport → `Assessor` → dispatcher: a PR env cannot make the
  provider fail.
- `scenario/PrEnvScenariosIT` (S-31, outside this package): the PR env's verification steps that fakes can
  replay, on the whole API in one context — a run started over HTTP (`POST /api/admin/pipeline-runs` as an admin
  or `POST /internal/pipeline-runs/daily` with the cron's token), really ended with assessed posts, read through
  the API's routes and the messages of the operator's topic. Its fakes: `sources/wykop/LocalWykop` and
  `sources/reddit/LocalReddit` (r/inwestowanie and r/stocks on their own pages; `sources/CLAUDE.md`), the production
  OpenRouter client from `LlmClientFactory` on a `LocalApiServer` answering each post by its user message with its
  recorded provider answer (`scenario/RecordedProvider`, `scenario/RecordedDays`, `fixtures/llm/scenario-days/`:
  31 pass the validator, 2 Reddit posts are rejected on every attempt, so they are `failed`, then leftovers, dead at
  the third), the real `OperatorAlertQueue` and `NtfyAlerts` to `alerts/LocalNtfy` (`@Primary` beans of their own
  names, the queue on a virtual clock, so its limit of 2 a minute costs no wait) and "today" 2026-10-29 11:00 in
  Warsaw (`config/MutableClock`). The scenarios: A — an admin's range of both sources from its start to the feed
  (every step `succeeded`, each source's result, the deploy guard safe, a Reddit post with its channel, URL and
  `/raw`, a Wykop post with its recorded targets, the log's removed r/stocks posts, no ERROR; S-17, S-20, S-27,
  S-32); B — the same range again (`[retry, range]`, the leftovers' second attempt, no post stored twice; S-27); C
  — Reddit down in an admin's run (`partial`, `source_failed`, Wykop's posts in the feed, the bell, one
  `RUN_NEEDS_ATTENTION` with `(range, admin) ended partial`; S-20, S-18); D — Reddit empty in the cron's run
  (`partial`, `scheduled`, `source_empty`, the range D-2…D-1, `RUN_NEEDS_ATTENTION` with `(range, cron)`; S-20);
  E — no source with a post in an admin's run (`failed` at `fetch` with `NoPostsFetchedException` naming both, no
  later step, the bell, one `RUN_NEEDS_ATTENTION` without the topic's token or address, `reported: DELIVERED`;
  S-18, S-08); F — the same in the cron's run (`failed`, the bell, no `taken` line and no message once the queue is
  quiet; S-18); G — `missing-days` from no run, a D-3 run, D-2 with Reddit down (only Wykop covers it) and D-2
  again (S-13, S-20); H — the cron's night and `POST /internal/pipeline-runs/nightly-summary` (the `wykop:` and
  `reddit:` lines, the same text as `NIGHT_SUMMARY` in the topic, no `RUN_NEEDS_ATTENTION` for its run; the
  `quality 7d` lines are not checked; S-34). Each scenario stands alone, in a random order (`-Dit.seed`, as
  `PipelineRunIT`); after each one `settle()` waits until no instance has a run of its own, the LLM pool is idle and
  the topic's reports equal the scenario's `Operator alert … taken` lines, then requires that every request to the
  provider had its recording. Every wait follows the order the run's thread writes in: the run's status, then the
  instance letting it go (`OwnRuns`: notification written, alert raised or left out), then the queue's delivery.
