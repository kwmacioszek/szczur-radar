# pipeline — one run: fetch, store, assess

A run is an Embabel agent planned deterministically (GOAP) from the types in `flow/RunFlow`; no LLM
decides the flow. The durable state is in Postgres (`pipeline_runs`, `pipeline_run_steps`, `posts`,
`assessments`), not in Embabel. Sources are reached only through `sources/SourceFetcher` and
`sources/RawInputMapper` — never name a source here (`sources/CLAUDE.md`).

## Where things are

```
pipeline/            entry points — start here
  PipelineRunService       create runs (advisory lock, refuse while busy), queue them, run one at a time
  PipelineStartupRecovery  server start: unfinished runs → interrupted, their posts put back
  PipelineBusyException, PostDeadException, SourceNotConfiguredException
  run/     the run model: PipelineRun, PipelineRunStep, RunKind, RunStatus, RunStep, StepStatus, repositories
  flow/    the graph: PipelineAgent (thin actions), RunFlow (the blackboard types)
  step/    the steps: SourceIngest (fetch), PostStore (store), AssessmentSelection (select)
  assess/  assessing posts: AssessmentDispatcher (port), ConcurrentAssessmentDispatcher, AssessmentInputs
  state/   conditional state writes: RunRecorder (runs, steps, counters), PostTransitions (posts)
```

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
| `step/PostStore` | new posts with their raw payload, once per `(source, item_type, source_item_id)`; `findKeys` in chunks of 1,000 |
| `step/AssessmentSelection` | one attempt per post and request: range → only the posts it stored first; retry → every leftover; posts → the given ones still assessable |
| `assess/AssessmentDispatcher` | the port of the assess step; a Batch API version may replace the concurrent one |
| `assess/ConcurrentAssessmentDispatcher` | own pool (`PIPELINE_LLM_CONCURRENCY`, default 4); per post: claim → `Assessor.assess` on `AssessmentInputs.of(post)` → record, or release |
| `assess/AssessmentInputs` | a stored post's assessment input, rebuilt from its raw payload by its source's `RawInputMapper` |
| `state/PostTransitions` | the post's stage changes, each a conditional `UPDATE` with `updated_at = now()`: claim (only while the run is running), record (only while the run owns the post), release (back without an attempt) |
| `state/RunRecorder` | run status, steps, counters, error — every change conditional on the expected status |

## Rules

- Every state change is one conditional `UPDATE` whose row count says whether it happened — no row
  locks, no blind `save()` of a run or a post. A worker of an interrupted run (deploy overlap) must
  never change a post another run took over.
- An attempt counts only when a `failed` assessment row is stored; the third makes the post `dead`
  (`state/PostTransitions.Outcome`). A technical error (any exception — a failed LLM call is a
  `failed` result from `Assessor`, not an exception) stores nothing, puts the post back and is counted
  in `processing_errors` (`RunFlow.AssessedBatch.errorSummary` becomes the run's error).
- Code that runs work in parallel passes its own executor (`availableProcessors()` may be 1).
- The root package holds only what other packages (the API in S-02 phase 4) call; the subpackages are
  the run's internals.

## Tests

- Unit (`*Test`, no Spring): `state/RunOutcomeTest`, `step/PostStoreTest`,
  `step/AssessmentSelectionTest`, `step/SourceIngestTest`, `assess/ConcurrentAssessmentDispatcherTest`
  (threads, one client, a technical error).
- `PipelineAgentIT`: the whole graph on Postgres with a source on a local server and a scripted
  `LlmClient` — range, the same range again, a leftover's one attempt per request, recovery and a late
  worker. Re-stub the `@MockitoBean` with `doAnswer(…).when(…)`, not `when(…)` (which calls the old
  answer with `null`).
