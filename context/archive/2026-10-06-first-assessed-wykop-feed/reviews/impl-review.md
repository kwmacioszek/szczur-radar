<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: S-02 first-assessed-wykop-feed

- **Plan**: context/changes/first-assessed-wykop-feed/plan.md
- **Scope**: Full plan
- **Reviewed phases**: 1, 2, 3, 4, 5, 6
- **Date**: 2026-10-09
- **Verdict**: NEEDS ATTENTION
- **Findings**: 0 critical, 6 warnings, 4 observations
- **Triage**: all 10 fixed (F2's database move is an operator step); found while fixing F6: Embabel retried failed pipeline actions by default — every action is now FIRE_ONCE

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | WARNING |
| Scope Discipline | WARNING |
| Safety & Quality | WARNING |
| Architecture | WARNING |
| Pattern Consistency | WARNING |
| Success Criteria | WARNING |

Context: phases 1 and 2 had their own reviews (`impl-review-phase-1.md`, `impl-review-phase-2.md`, triaged); their code is unchanged since then except for the documented source ports, so this review re-checks them only for regressions. The approved adaptations in `follow-ups/review-fixes.md` (item type, source ports, `source_not_configured`, package layout, dispatcher via `Assessor.assess`, sources endpoint) are not findings. Automated checks on `5b56090`: `./mvnw -B verify` (484 unit + 88 IT, 0 failures), `npm test` (51), `npm run build` and CI on PR #37 green; the manual checks 1.3, 5.3 and 6.3–6.7 were done with the user in this session (local run and PR env `szczur-radar-pr-37`). Clean: SQL is parameterized everywhere, no `v-html`/`href` from post data in the SPA, all four admin routes under `/api/admin/**` and in `ADMIN_ROUTES`, secrets masked in Wykop and LLM errors, own executors, V3–V5 safe (not on `main` yet).

## Findings

### F1 — A provider's 401/403 (bad key or model) uses up every post's attempts

- **Severity**: ⚠️ WARNING
- **Impact**: 🔬 HIGH — architectural stakes; think carefully before deciding
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/llm/transport/TransportException.java:66-74; llm/CallFailure.java:11-17; pipeline/state/PostTransitions.java (Outcome.after); pipeline/flow/RunFlow.java:136
- **Detail**: `kindOf` maps 401/403 (and 400/404) to `REJECTED`, which becomes `LlmCallException(PROVIDER_ERROR)` → a stored `failed` assessment → one attempt counted; the 3rd makes the post `dead` (refused for retry, recoverable only by SQL). `CallFailure` has no way to say "configuration", so the pipeline cannot tell a bad key from a bad answer. Every range request also queues a retry of all leftovers, so three clicks with a revoked key kill the whole backlog, and the run ends `partial` even at 100% failures. Happened twice in this session (local: 155 posts, PR env: 233 posts, revoked DeepSeek keys).
- **Fix A ⭐ Recommended**: Classify 401/403 (and 404 for a wrong model) as a new configuration failure: the post is released as a technical error (no stored attempt), the dispatcher stops claiming further posts, and the run ends `failed` with "LLM credentials or model rejected (HTTP 401)".
  - Strength: One bad call stops the run; no attempts are burnt and the admin sees the cause on the run, not on 200 posts.
  - Tradeoff: New `CallFailure` (or a status on `LlmCallException`) through `llm`, `Assessor` and the dispatcher; new tests.
  - Confidence: HIGH — the path is confirmed in code and observed twice.
  - Blind spot: Whether a provider ever answers 403 for a single content item (content policy) — then that post would stop the run; check provider docs (OpenRouter moderation is 403).
- **Fix B**: Only treat such failures as technical errors (no attempt counted), without stopping the run.
  - Strength: Smaller change, no new run-level behavior.
  - Tradeoff: A bad key still makes one wasted call per post and a run full of technical errors.
  - Confidence: MEDIUM — fixes the data damage, not the noise.
  - Blind spot: None significant.
- **Decision**: FIXED (Fix A + preflight; the check is one tiny real call, not a model listing) — Fix A (configuration failure for 401/403/404: no attempt, run stops as failed) + user's addition: a preflight check of key and model (no generation) at the start of the assess step, and GET /api/admin/llm/check (provider, model, ok/error); panel button comes with the UI change. Provider endpoints to be checked in the docs first.

### F2 — Postgres runs in `sfo` (US), the API in `europe-west4`

- **Severity**: ⚠️ WARNING
- **Impact**: 🔬 HIGH — architectural stakes; think carefully before deciding
- **Dimension**: Architecture
- **Location**: .railway/railway.ts:51-53 (`postgres("postgres")` without a region)
- **Detail**: `railway status --json`: `postgres` has `multiRegionConfig {sfo: 1}` in `production` and in `szczur-radar-pr-37`; api and web are in `europe-west4`. `infrastructure.md` and `deploy-plan.md` assume one EU region. Every JDBC round trip crosses the Atlantic (~140 ms): the `store` step (3 round trips per post) took 1 min 44 s for 244 posts, and every API request pays it via the JDBC session. Pre-existing (not introduced by this branch), but S-02 is the first workload where it matters.
- **Fix A ⭐ Recommended**: The operator moves Postgres (and its volume) to `europe-west4` in production before the merge — production holds only users and sessions — then `railway.ts` declares the region so IaC keeps it; check `railway config plan` before applying.
  - Strength: Removes the latency for every path at once; matches the documented topology.
  - Tradeoff: A volume migration with a short database downtime; an operator action outside the code.
  - Confidence: MED — the region is confirmed; how Railway migrates a volume between regions (and whether the plan marks it destructive) is not verified.
  - Blind spot: Railway's region-change procedure for a service with a volume; whether PR envs inherit the production region.
- **Fix B**: Leave the region and only batch the store step (F3).
  - Strength: No infrastructure change before the merge.
  - Tradeoff: Every request and every assessment write stays ~140 ms per round trip.
  - Confidence: HIGH — F3 alone cuts the store step, not the rest.
  - Blind spot: None significant.
- **Decision**: FIXED in code and docs; the move itself is an operator action still open (deploy-plan.md, "Postgres region") — Fix A (downtime accepted by the user). Checked: Railway migrates a service's volume on a region change, with downtime (docs.railway.com/deployments/regions#volumes); IaC does not manage it — a dry-run plan with postgres("postgres", { region }) shows no change in production or the PR env, so the move is an operator action (service Settings → Scale → Regions) per environment: production by the operator, the PR env's own Postgres separately; railway.ts declares the region anyway (no diff) so a new project is created in the EU; deploy-plan documents it; next PR env checked for the inherited region.

### F3 — The store step does one transaction and two single-row INSERTs per post

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/step/PostStore.java:54-58; raw/PostgresRawPayloadStore.java:31
- **Detail**: Each post gets its own `TransactionTemplate` with a RAW INSERT, a post INSERT and a COMMIT (no `hibernate.jdbc.batch_size`), so 3 sequential round trips per post. Duplicates rely on catching the unique violation per post.
- **Fix**: Store in chunks (e.g. 500) in one transaction each: batch the RAW rows, insert posts with `ON CONFLICT (source, item_type, source_item_id) DO NOTHING RETURNING id`, and drop the RAW rows of conflicting posts in the same transaction; keep `StoredBatch` = the returned ids.
  - Strength: Round trips per chunk instead of per post; also removes exception-driven deduplication.
  - Tradeoff: Native SQL instead of entity saves for this step; `PostStoreTest` and `PipelineAgentIT` must keep the dedupe and RAW-orphan guarantees.
  - Confidence: HIGH — standard Postgres pattern; the guarantees are already covered by tests.
  - Blind spot: Behavior when two runs store the same key concurrently (today impossible: one run at a time).
- **Decision**: FIXED (ADAPT: chunks of 500 sent as JDBC batches, post by post on a conflict, instead of native ON CONFLICT — the Post constructor stays the only way a post is built).

### F4 — A run or a post can stay blocked until restart or forever

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/PipelineRunService.java:152-172, 181-185; pipeline/PipelineStartupRecovery.java:54-55; pipeline/state/PostTransitions.java:51-54
- **Detail**: (1) In `execute`, an exception from `recorder.start`, `runs.findById` or the `finish` inside the catch escapes: the run stays `queued`/`running` with no thread, so every start answers 409 until a restart (plan: an unhandled exception ends the run `failed` and frees the queue). (2) `claim` checks the run status in an `EXISTS` evaluated at statement start; a claim racing with an interruption can commit after `releaseAll`, and if the JVM exits before the worker's `finally`, the post stays `assessing` owned by an `interrupted` run. Recovery and `onClose` release only posts of runs they interrupt themselves, so nothing ever releases it: the post is in no list and no retry.
- **Fix**: Catch everything in `execute` and try to finish the run `failed`; let recovery (and `onClose`) also release `assessing` posts whose owner is not `queued`/`running`.
- **Decision**: FIXED — Fix now (after the triage).

### F5 — `PIPELINE_LLM_CONCURRENCY` is not preserved in `railway.ts`

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: .railway/railway.ts:104-117
- **Detail**: Phase 6 documents the variable as optional on production (`deploy-plan.md`, README), but `railway.ts` has no `preserve()` for it. If the operator sets it, the next plan proposes `Delete variable`, the CI gate refuses it, `railway-config` goes red and Wait for CI blocks production deploys.
- **Fix**: Add `PIPELINE_LLM_CONCURRENCY: preserve()` next to the `WYKOP_*` entries.
- **Decision**: FIXED — Fix now (after the triage).

### F6 — Gaps in tests and in manual check 5.3

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Success Criteria
- **Location**: szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/state/RunOutcomeTest.java; PipelineRunService.onClose (no test)
- **Detail**: The `failed` run status (a source failing mid-run → `failed` + `error` + queue freed) is never tested, and the `ContextClosedEvent` handler has no test. Manual check 5.3 is `[x]` with "narrow screen" explicitly deferred (documented in the row).
- **Fix**: Add a `PipelineAgentIT` case with a failing source (expect `failed`, `error`, a following request accepted) and a test of `onClose` (own run `interrupted`, its posts released).
- **Decision**: FIXED — Fix now: the two tests (after the triage); 5.3 narrow screen stays deferred to the UI change.

### F7 — Undocumented additions beyond the plan

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Scope Discipline
- **Location**: commit ab90652 (llm package split); PipelineStartupRecovery.java:31,59; context/foundation/prd.md; PipelineAgent.java:75
- **Detail**: Not in the plan nor in `review-fixes.md`: the `llm` split into `gemini/deepseek/openrouter/transport` (user's request, no behavior change); recovery only in a web context (so the pre-deploy `migrate` never interrupts the old instance) and logged instead of failing the start; the PRD NFR clarification; `skipped` counted in the fetch step (a consequence of the source ports).
- **Fix**: Record them in `follow-ups/review-fixes.md` and list them in the PR body.
- **Decision**: FIXED — Fix now: record them in follow-ups/review-fixes.md and the PR body.

### F8 — Stage and status values as string literals in SQL

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/state/PostTransitions.java:27-29,51-54,72-73; pipeline/state/RunRecorder.java:36,43,52-53,65,79,115,126
- **Detail**: `'stored'`, `'assessing'`, `'failed'`, `'running'`, `'queued'`, `'interrupted'` are written inline although `PostStage`/`RunStatus` exist and are bound as parameters elsewhere in the same files (lessons.md: closed sets in enums). A misspelled value in a WHERE clause matches nothing silently.
- **Fix**: Bind `PostStage.X.value()` / `RunStatus.X.value()` as parameters.
- **Decision**: FIXED — Fix now (after the triage).

### F9 — Wykop paging can skip an element when the result set shrinks

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/sources/wykop/WykopRepository.java:126-142
- **Detail**: Paging counts `received` (with duplicates) against `total`; if an element is deleted between pages, the next element moves onto a page already read and is never fetched, while the loop still ends as complete. A later run over the same day picks it up, but the daily run (S-05) will not repeat a day.
- **Fix**: Note it for S-05 (e.g. an overlap or a second pass of the previous day); no change in S-02.
- **Decision**: FIXED — Fix now: an S-05 unknown in roadmap.md (overlap or a second pass of the previous day); no S-02 code change.

### F10 — UI/UX items and the stale PR-env web deploy are still open

- **Severity**: 💡 OBSERVATION
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Scope Discipline
- **Location**: context/changes/first-assessed-wykop-feed/follow-ups/review-fixes.md ("For the final review")
- **Detail**: Queued during the manual checks: UI hardening (paging and "select all" in the failed list, dates and live progress of runs, why a second start is refused, hiding queued retries, counter explanations, narrow screens, SPA nits such as `invalid_request` wording for retries and a page beyond the last), the UI redesign, and the investigation of the PR-env web deploying `154bed8` instead of the branch head.
- **Fix A ⭐ Recommended**: Move the UI items to a new change (`/10x-new`, then `/10x-ui` per view); here only document the deploy check (web deployment commit = branch head) in the deploy-plan troubleshooting.
  - Strength: Keeps this PR reviewable; the UI work gets its own research and plan (some items need backend changes, e.g. live counters).
  - Tradeoff: Production gets the bare admin panel for a while (admin-only).
  - Confidence: HIGH — the user leaned towards a separate change.
  - Blind spot: None significant.
- **Fix B**: Do the UI hardening (not the redesign) in this PR before the merge.
  - Strength: The first production run uses a clearer panel.
  - Tradeoff: A large addition to an already large PR, without its own plan.
  - Confidence: MED — scope is not planned.
  - Blind spot: How many backend changes the live progress needs.
- **Decision**: FIXED — Fix A: the UI items (hardening, redesign, SPA nits, 5.3 narrow screen) move to a new change (/10x-new, then /10x-ui per view); here only the deploy-plan check that the web deployment's commit is the branch head.
