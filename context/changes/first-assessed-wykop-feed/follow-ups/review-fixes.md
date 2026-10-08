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
- [ ] **→ phase 4 (API contract change vs. the plan):** `POST /api/admin/pipeline-runs` with a range
  takes an optional `"sources": ["wykop", …]` (empty or absent = every configured source); the error
  `wykop_not_configured` (503) becomes `source_not_configured` (503), raised when a named source or
  every source is not configured (`SourceNotConfiguredException`). `GET /api/admin/pipeline-runs`
  shows each run's `sources`.
- [ ] **→ phase 5:** the admin form offers the sources (all checked by default); the
  `source_not_configured` message in Polish.
