# Review follow-ups — first-assessed-wykop-feed

Items queued by `/10x-impl-review` triage that are not code fixes of the reviewed phase.

## Phase 2 review (2026-10-06, `reviews/impl-review-phase-2.md`)

- [x] **F2 → phase 3 (done):** every conditional `UPDATE` of `posts` (claim, result, recovery, final clean-up) sets
  `updated_at = now()`; `@UpdateTimestamp` covers only entity saves and V3 has no trigger
  (`posts/Post.java` Javadoc).
- [x] **F1 → done in the triage (2026-10-07), not phase 3:** U+0000 never reaches the database —
  `text/Nul`: `WykopMapper` and `Post` remove it from source text; `Assessment` stores U+FFFD in
  `raw_text` and the JSON columns. Phase 3 only has to build posts and assessments through these
  constructors (no native `INSERT` of text).
- [x] **F3/F4 → done in the triage (2026-10-07), V4:** the plan's `posts.resource ∈ {entry, link}`
  is replaced by `posts.item_type` (shared `ItemType`); phases 3–4 use `item_type` wherever the plan
  says `resource` (e.g. the API element's `resource` field becomes the item type).
- [ ] **Plan ADAPT (phase 2) → record:** `RawPayloadStore.store(Source, byte[])` instead of the plan's
  `store(String source, …)` (closed sets as enums).
- [x] **F6 → phase 3 (done; RETRY has no cap in S-02 — a day is ~270 posts; revisit with S-05):** `PostStore` asks `PostRepository.findKeys` in chunks of 1,000 ids (a search may
  return up to 200 pages × 50 × 2 resources); decide in phase 3 whether a RETRY run has a per-run cap
  on leftovers (`findLeftoverIds` returns all of them).

## Phase 3 (2026-10-08, user's decision before the phase 3 commit)

- [x] **Sources behind ports, not Wykop in `pipeline`:** fetching goes through `sources/SourceFetcher`
  (`WykopFetcher`), assessing through `sources/RawInputMapper` (`WykopPosts`); `pipeline` names no
  source. A range run stores the sources it fetches (`pipeline_runs.sources`, V5): the admin's list,
  or every configured source when it is empty. Guide: `sources/CLAUDE.md`, `pipeline/CLAUDE.md`.
- [x] **→ phase 4 (API contract change vs. the plan; done, `PipelineRunControllerIT`, `PipelineRunWithoutSourceIT`):** `POST /api/admin/pipeline-runs` with a range
  takes an optional `"sources": ["wykop", …]` (empty or absent = every configured source); the error
  `wykop_not_configured` (503) becomes `source_not_configured` (503), raised when a named source or
  every source is not configured (`SourceNotConfiguredException`). `GET /api/admin/pipeline-runs`
  shows each run's `sources`.
- [x] **→ phase 5 (done):** the admin form offers the sources (configured ones checked by default); the
  `source_not_configured` message in Polish.
- [x] **Readability refactor after phase 3 (user's request, 2026-10-08):** package layout `pipeline/{run,flow,step,assess,state}`,
  every enum converter in `persistence/converters/`; the dispatcher calls `Assessor.assess` (it duplicated
  prepare → call → evaluate/failed; the `AssessmentDispatcher` port stays the Batch API seam — ADAPT of
  the plan's "prepare + LlmClient + evaluate"), input building in `assess/AssessmentInputs`; run-creation
  queries in the repositories; `AssessedBatch.errorSummary`. No behavior change.
- [x] **Phase 4 ADAPT:** controllers and their DTOs live in `posts/api/` and `pipeline/api/` (not the
  plan's `posts/PostController.java`, `pipeline/PipelineRunController.java`), keeping the domain packages
  small; the feed item's `resource` is `itemType` (shared `ItemType`); the pipeline's exceptions are
  mapped in `PipelineRunController`, so `config` does not depend on `pipeline`.
- [x] **Phase 5 addition (backend):** `GET /api/admin/pipeline-runs/sources` → `[{source, configured}]` (from the
  `SourceFetcher` beans), so the SPA knows no source by name and a new source needs no SPA change; in
  `ADMIN_ROUTES`, tested in `PipelineRunControllerIT` and `PipelineRunWithoutSourceIT`. `HomeView` is
  replaced by `FeedView`; the top bar is the shared `AppHeader`.

## For the final review (2026-10-08, user's manual check 5.3 locally)

- [ ] **Run-wide provider errors burn every post's attempts:** with a revoked DeepSeek key every post got
  `provider_error` (HTTP 401) as a counted `failed` attempt (155 of 163 in one range run); three such runs
  would make every post `dead`. Decide in `/10x-impl-review`: an authentication/authorization answer
  (401/403) from the provider stops the run (run `failed`, posts released without an attempt) instead of
  counting per post.
- [ ] **UI hardening (last phase or with the review fixes, user's decision):** buttons disabled while a
  request is pending; clear messages for 409 `pipeline_busy` and 503 `source_not_configured`; form
  labels and focus; an expired session mid-work (401 → login); narrow screens; plus the user's own list
  from the manual check.
  From the check: the failed-posts list shows only its first page (~50 of 155) with no paging and no
  sign that more exist; no "select all" for retry; the run's counters need a short explanation
  (e.g. "ocenione" includes no-content posts decided by the prefilter without a model call).
  The admin did not expect the retry run that a range request queues before the range (leftovers
  first), nor that a range over stored posts assesses nothing ("nowe 0"): the form should say what a
  start will queue (e.g. "first a retry of N failed posts").
  From the PR-env check (2026-10-09): the runs list does not show a range run's dates (the API already
  returns `rangeFrom`/`rangeTo`); a running run shows only its finished steps' counters (e.g. "pobrane 244"
  while `assess` works for minutes) — show the current step and progress; while a run is active the start
  button should say why a second run is refused (one run at a time, 409 `pipeline_busy`) instead of only
  failing.
  A failed post queued for retry or being assessed should leave the failed list (or show "w kolejce" /
  "oceniany") instead of staying there unchanged until the run ends.
- [ ] **Store step is slow in the PR env:** `store` of 244 posts took 1 min 44 s (≈0.4 s per post over Railway's
  private network) — check for per-post round trips (one `INSERT`/`SELECT` per post and RAW) and batch them.
- [ ] **Investigate: the PR env web deployed an old commit (2026-10-09):** on the phase 5 push (`65e321f` +
  `7d280d1`, 22:56) Railway deployed `szczur-radar-web` in `szczur-radar-pr-37` from `154bed8` (phase 4)
  with status SUCCESS, so `/admin` was missing; the phase 6 push was SKIPPED (no web paths changed) and the
  stale SPA stayed until `railway redeploy --from-source -s szczur-radar-web -e szczur-radar-pr-37`. Find why
  (Wait for CI picking the previous commit's check suite? two commits in one push? watch paths?), and add a
  check to the PR-env verification / `deploy-plan.md` troubleshooting: the web deployment's commit equals the
  branch head.
- [ ] **UI visual redesign (user's decision, 2026-10-08):** after the last phase (6), on this branch and PR,
  before the hand-over: a new look for the feed, admin panel and login, together with the hardening above;
  direction and styling approach to be decided then.

## Final review (2026-10-09, `reviews/impl-review.md`) — queued fixes

- [x] **F1:** LLM configuration failure (401/403, 404 for a wrong model) as its own class: no attempt
  counted, the post released, the run stops as `failed` with the cause; plus a preflight check of key
  and model at the start of the assess step, and `GET /api/admin/llm/check` (provider, model, ok/error).
  ADAPT: the check is one tiny real call (`LlmClient.check()`), not a model listing — DeepSeek's
  `GET /models` does not list the alias `deepseek-v4-flash` it accepts. 403 stays per request
  (OpenRouter moderation). The panel button comes with the UI change.
- [x] **F2:** Postgres to `europe-west4` (operator action per environment; IaC does not move it —
  dry-run plan with `postgres("postgres", { region })` shows no change). `railway.ts` declares the
  region; `deploy-plan.md` documents the move; check the next PR env's region.
- [x] **F3:** chunked store step. ADAPT: chunks of 500 in one transaction each, raw payloads then
  posts sent as JDBC batches (`hibernate.jdbc.batch_size`, `reWriteBatchedInserts`), a chunk hitting
  the unique key stored again post by post — instead of native `ON CONFLICT … RETURNING`, so the
  `Post` constructor (NUL removal, content hash) stays the only way a post is built.
- [x] **F4:** catch-all in `PipelineRunService.execute`; recovery and `onClose` release `assessing`
  posts whose run is not `queued`/`running`.
- [x] **F5:** `PIPELINE_LLM_CONCURRENCY: preserve()` in `railway.ts`.
- [x] **F6:** tests for a `failed` run (source failing) and for `onClose`.
- [x] **F7:** record the unplanned additions (below) and list them in the PR body.
- [x] **F8:** stage/status values bound as enum parameters in `PostTransitions` and `RunRecorder`.
- [x] **F9:** S-05 unknown in `roadmap.md`: Wykop paging can skip an element when the result shrinks
  between pages; the daily run needs an overlap or a second pass of the previous day.
- [x] **F10:** the UI items above ("UI hardening", "UI visual redesign", SPA nits, 5.3 narrow screen) move
  to a new change (`/10x-new`, then `/10x-ui` per view); here only the deploy-plan check that the web
  deployment's commit is the branch head.
- [x] **Found while fixing F6:** Embabel retried a failed pipeline action by default (a failed Wykop
  fetch every 10 s and more): every `PipelineAgent` action is now `FIRE_ONCE`.
- [x] **F2, operator (done 2026-10-09):** `postgres` moved to `europe-west4` in production and in
  `szczur-radar-pr-37` (`railway status`: `europe-west4-drams3a`; production `/api/ready` 200) —
  `deploy-plan.md`, section `first-assessed-wykop-feed`, "Postgres region".

Unplanned additions (F7): the `llm` package split into `gemini/`, `deepseek/`, `openrouter/`,
`transport/` (user's request, no behavior change, `ab90652`); startup recovery only in a web context
(`@ConditionalOnWebApplication`, so the pre-deploy `migrate` never interrupts the old instance's run)
and logged instead of failing the start; the PRD NFR clarification on registering a source; `skipped`
counted in the fetch step (a consequence of the source ports).
