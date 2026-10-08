# pipeline — one run: fetch, store, assess

A run is an Embabel agent planned deterministically (GOAP) from the types in `RunFlow`; no LLM
decides the flow. The durable state is in Postgres (`pipeline_runs`, `pipeline_run_steps`, `posts`,
`assessments`), not in Embabel. Sources are reached only through `sources/SourceFetcher` and
`sources/RawInputMapper` — never name a source here (`sources/CLAUDE.md`).

```
PipelineRunService.startRange / startPosts   queue (advisory lock, one run at a time, background thread)
        │
        ▼
PipelineAgent — thin actions; each calls one service and is recorded as a step (RunRecorder.step)
  route ──► RANGE: fetch (SourceIngest → SourceFetcher beans) ──► store (PostStore) ──► select ──┐
        └─► RETRY / POSTS ─────────────────────────────────────────────────────────► select ──┤
                                                                       (AssessmentSelection)  ▼
                                    assess (AssessmentDispatcher) ──► summarize (goal: RunSummary)
```

| Class | Job |
| --- | --- |
| `RunFlow` | the blackboard types; the graph follows from them. A new stage (e.g. ML) = new types and actions between `StoredBatch` and `PendingAssessments` |
| `PipelineAgent` | the actions — a few lines each, no logic; exactly one action can run for any blackboard state |
| `SourceIngest` | which sources a range run fetches (default: every configured one) and the fetch over them |
| `PostStore` | new posts with their raw payload, once per `(source, item_type, source_item_id)`; `findKeys` in chunks of 1,000 |
| `AssessmentSelection` | one attempt per post and request: range → only the posts it stored first; retry → every leftover; posts → the given ones still assessable |
| `assess/AssessmentDispatcher` | sends the assessments and stores each result as it arrives; `ConcurrentAssessmentDispatcher` uses its own pool (`PIPELINE_LLM_CONCURRENCY`, default 4); a Batch API version may replace it |
| `PostTransitions` | the post's stage changes, each a conditional `UPDATE` with `updated_at = now()`: claim (only while the run is running), record (only while the run owns the post), release (back without an attempt) |
| `RunRecorder` | run status, steps, counters, error — every change conditional on the expected status |
| `PipelineRunService` | creating and executing runs; on context close marks its own runs `interrupted` |
| `PipelineStartupRecovery` | server start (web only): unfinished runs → `interrupted`, their `assessing` posts put back |

## Rules

- Every state change is one conditional `UPDATE` whose row count says whether it happened — no row
  locks, no blind `save()` of a run or a post. A worker of an interrupted run (deploy overlap) must
  never change a post another run took over.
- An attempt counts only when a `failed` assessment row is stored; the third makes the post `dead`
  (`PostTransitions.Outcome`). A technical error (anything but `LlmCallException`) stores nothing,
  puts the post back and is counted in `processing_errors`.
- A failed call becomes a result only through `Assessor.failed`; the dispatcher never builds one.
- Code that runs work in parallel passes its own executor (`availableProcessors()` may be 1).

## Tests

- Unit (`*Test`, no Spring): `RunOutcomeTest`, `PostStoreTest`, `AssessmentSelectionTest`,
  `SourceIngestTest`, `assess/ConcurrentAssessmentDispatcherTest` (threads, one client, a technical
  error).
- `PipelineAgentIT`: the whole graph on Postgres with a source on a local server and a scripted
  `LlmClient` — range, the same range again, a leftover's one attempt per request, recovery and a late
  worker. Re-stub the `@MockitoBean` with `doAnswer(…).when(…)`, not `when(…)` (which calls the old
  answer with `null`).
