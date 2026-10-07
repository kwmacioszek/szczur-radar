<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: S-02 first-assessed-wykop-feed

- **Plan**: context/changes/first-assessed-wykop-feed/plan.md
- **Scope**: Phase 2 of 6
- **Reviewed phases**: 2
- **Date**: 2026-10-06
- **Verdict**: NEEDS ATTENTION
- **Findings**: 0 critical, 3 warnings, 5 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | WARNING |
| Pattern Consistency | PASS |
| Success Criteria | PASS |

Context: V3 is already applied in the Railway PR env database (PR #37, deploy of `4e1a10d` succeeded). A schema fix is either a new migration V4 (fix-forward) or an edit of V3 plus a reset of the PR env database (V3 is not on production). Automated criteria: `./mvnw -B verify` green (458 unit tests, 69 ITs, including MigrationsIT 13, JpaSchemaIT 2, PostRepositoryIT 5, PostgresRawPayloadStoreTest 5, WykopRepositoryTest 8, WykopConfigurationTest 3). The drift check found every planned item MATCH; the extras (`persistence/`, `sources/Source`, `PostRepositoryIT`, doc edits, `RawPayloadStore.store(Source, …)`) are justified.

## Findings

### F1 — A post whose text has U+0000 can never be stored as an attempt, so it never reaches DEAD

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/resources/db/migration/V3__create_pipeline_and_posts.sql:86-87,136-141 (posts.title/body, assessments.input/raw_text)
- **Detail**: Postgres TEXT rejects the NUL character and jsonb rejects the `\u0000` escape. Nothing in `sources/wykop`, `text/Whitespace` or `Assessment` removes it. A Wykop body or a model's `raw_text` with U+0000 fails the per-post transaction. Phase 3 then counts a processing error without an attempt (plan: no attempt for technical errors), so the post is retried by every RETRY and never becomes DEAD.
- **Fix A ⭐ Recommended**: In phase 3, remove U+0000 at the boundaries: drop it in `WykopMapper` (input to the model and the post's text) and replace it with U+FFFD in `Assessment.rawText`, with a test on a real fixture changed minimally.
  - Strength: The data stays storable end to end; a NUL is not meaningful text in a post, so the model loses nothing.
  - Tradeoff: A deliberate (tiny) deviation from "text goes to the model unchanged" (`sources/CLAUDE.md`), to be documented there.
  - Confidence: HIGH — the Postgres behavior is documented; the change is local.
  - Blind spot: Not checked whether Wykop ever returns U+0000 (no occurrence in the fixtures).
- **Fix B**: Keep the text, but count repeated technical errors per post (new column) and make the post DEAD after 3.
  - Strength: Covers every persistent technical error, not only NUL.
  - Tradeoff: New column and rules in phase 3; the post's content is still never stored.
  - Confidence: MEDIUM — changes the plan's attempt model.
  - Blind spot: Other persistent technical errors are not known yet.
- **Decision**: PENDING

### F2 — `posts.updated_at` is not maintained by the pipeline's conditional updates

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/posts/Post.java:90
- **Detail**: `@UpdateTimestamp` fires only on an entity flush. Stage and attempts change through conditional `UPDATE`s (plan, Critical Implementation Details), and V3 has no trigger, so `updated_at` would stay at the insert time.
- **Fix**: Say in `Post`'s Javadoc that every conditional update sets `updated_at = now()`, and queue that as a phase 3 requirement in `follow-ups/review-fixes.md`.
- **Decision**: PENDING

### F3 — CHECKs on `posts.source`/`resource` and `PostResource` in `posts` make a new source touch persistence

- **Severity**: ⚠️ WARNING
- **Impact**: 🔬 HIGH — architectural stakes; think carefully before deciding
- **Dimension**: Architecture
- **Location**: V3__create_pipeline_and_posts.sql:76,113-114; posts/PostResource.java
- **Detail**: The hard rule (REPOSITORY_GUIDELINES.md, PRD § Non-Functional Requirements) says a new source changes only acquisition and mapping, and persistence stays untouched. With `ck_posts_source`, `ck_raw_payloads_source` and `ck_posts_resource` (entry | link), plus `PostResource` living in `posts`, Reddit would need a persistence migration and a change in `posts`. The plan itself asked for these sets, so this is a plan-level conflict with the PRD, which the rules say to flag rather than resolve silently.
- **Fix A ⭐ Recommended**: Make the source-native values belong to `sources`. A V4 drops the three CHECKs (or V3 is edited and the PR env database reset). `PostResource` moves to `sources` as the source-native resource type. The Java enums still keep the sets closed.
  - Strength: A new source adds enum constants in `sources` only; the hard rule holds literally.
  - Tradeoff: The database no longer guards these two columns; only the Java enums do.
  - Confidence: MEDIUM — Reddit's resource types are not designed yet.
  - Blind spot: Whether S-03/S-04 want database-level filters on resource.
- **Fix B**: Keep the CHECKs and reword the rule: "a new source adds its constants and one migration extending the CHECK sets; no other persistence change". Flag the PRD NFR wording to the user.
  - Strength: The strongest data guarantee; no code change now.
  - Tradeoff: Changes a hard rule and contradicts the PRD wording.
  - Confidence: MEDIUM — depends on how strictly the NFR is read.
  - Blind spot: None significant.
- **Decision**: PENDING

### F4 — `ck_pipeline_runs_range` lets a non-range run carry half a range

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: V3__create_pipeline_and_posts.sql:32-34
- **Detail**: `(kind = 'range') = (range_from IS NOT NULL AND range_to IS NOT NULL)` passes for a `retry` run with only `range_from` set (false = false). `PipelineRun`'s factories never do that, so this is only the database guard.
- **Fix**: Tighten it to `(kind = 'range') = (range_from IS NOT NULL) AND (range_from IS NULL) = (range_to IS NULL) AND …`, in the same migration as F3 (or V3 + PR env reset), with a MigrationsIT case.
- **Decision**: PENDING

### F5 — Stored `input` is not byte-exact, and the JSON columns depend on Hibernate's Jackson 2 mapper

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: posts/Assessment.java:65-78; V3__create_pipeline_and_posts.sql:135
- **Detail**: jsonb normalizes key order and white space, so `input_hash` ("of the input's contract JSON as sent") cannot be recomputed from the stored column. The hash is right when written; only re-verification is impossible. Hibernate 7.2 passes a `String` through unchanged, but it resolves its JSON format mapper (Jackson 2 only) at bootstrap. Dropping jackson-databind 2.x from the classpath could break the JSON columns.
- **Fix**: Say both in `Assessment`'s Javadoc (`getInputHash`: hash of the text sent, not of the stored jsonb; the Jackson 2 dependency of the JSON columns).
- **Decision**: PENDING

### F6 — `findKeys` IN list and unbounded `findLeftoverIds` need limits in phase 3

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: posts/PostRepository.java:26-40
- **Detail**: A search may return up to 200 pages × 50 × 2 resources (20,000 ids), which makes one huge IN list. `findLeftoverIds` returns every leftover, so one RETRY could send an unbounded number of LLM calls. Typical days (~270 posts) are far from both.
- **Fix**: Queue for phase 3: `PostStore` asks `findKeys` in chunks of 1,000; decide whether RETRY has a per-run cap.
- **Decision**: PENDING

### F7 — Stored provider/channel values require append-only enum constants

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: posts/*Converter.java; persistence/EnumValueConverter.java:38-45
- **Detail**: `EnumValueConverter` throws on an unknown value. Removing or renaming a `Provider`/`Channel`/`ServiceTier`/`ReasoningEffort`/`OutputMode`/`FailureClass` constant later would make old `assessments` rows unreadable.
- **Fix**: Add to `llm/CLAUDE.md` ("Adding a provider") and `agents/assessor/CLAUDE.md`: stored constants are append-only; a value once stored stays.
- **Decision**: PENDING

### F8 — `RawPayload.getSource()`/`getMediaType()` have no production caller

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: raw/RawPayload.java:64-74
- **Detail**: Only `PostgresRawPayloadStoreTest` uses the two package-private getters; `load` switches on the encoding only. The user does not want code without a caller. The plan's `store(String source, …)` also differs from the code's `store(Source, …)`, a justified ADAPT that is not recorded anywhere.
- **Fix**: Assert source and media type in `JpaSchemaIT` via JDBC and remove the two getters. Note the `Source` parameter in `follow-ups/review-fixes.md`.
- **Decision**: PENDING
