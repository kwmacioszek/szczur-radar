# sources — data sources → the shared model

A source (Wykop, Reddit; others later) only **acquires** elements and **maps** them to the
shared model: `SourcePost` for storing, `AssessmentInputV1` (`agents/assessor/contract`) for
assessing. Assessment, validation, persistence, the pipeline and the API never change for a new
source (`REPOSITORY_GUIDELINES.md`, PRD § Non-Functional Requirements). Contract rules for the input:
`context/foundation/llm-assessment/contract-v2.md` §2.

## The two ports the pipeline uses

The pipeline (`pipeline/`) knows sources only through two interfaces in this package, collected as
Spring beans (`List<…>` injected, keyed by `source()`). It never names a source.

| Interface | Called by | Contract |
| --- | --- | --- |
| `SourceFetcher` | fetch step of a range run (`pipeline/step/SourceIngest`) | `source()`; `configured()` (e.g. keys set — an unconfigured source is never fetched and a request naming it is refused); `fetch(from, to)` → `Fetched(posts, fetched, skipped)`: the posts added in the range as `SourcePost`s, each once; elements that cannot become a post are counted as `skipped`, never thrown; a failure of the source itself is thrown: the source's result is `failed`, the run goes on with the other sources (`pipeline/step/SourceIngest`) |
| `RawInputMapper` | assess step, for new and retried posts alike (`pipeline/assess/AssessmentInputs`) | `source()`; `map(raw)` → the `AssessmentInputV1` of a stored post, rebuilt from the raw payload the fetcher put in `SourcePost.raw` — so a retry gets exactly the input of the first attempt |

`SourcePost` (`sources/SourcePost`): the shared fields, already mapped — `itemType` (shared
`ItemType`: `post`/`link`/`comment`, never the source's own names), `sourceItemId` (unique per source
and item type), `channel` (the source's channel the post came from, in the source's own spelling:
Wykop's tag `gielda`, a subreddit's name — the same value as the input's `channels[0]`; stored in
`posts.channel`, `V13`), `publishedAt` (an instant), `title`/`body` (mapped as for the input: unchanged text,
blank → `null`), `author`, `slug`, `webUrl`, `tags`, and `raw` — the element's JSON bytes, exactly
what `RawInputMapper.map` will get back.

Who chooses the sources: a range run stores its list (`pipeline_runs.sources`); the admin names it or
leaves it empty for every configured source (`pipeline/step/SourceIngest.resolve`). Retry and posts runs fetch
nothing.

## Adding a source

1. `sources/Source`: the constant (its `value()` is what is stored), and one Flyway migration adding
   that value to the four `CHECK` sets of a source: `posts.source`, `raw_payloads.source`,
   `pipeline_runs.sources` and `pipeline_run_sources.source` (registering the source; nothing else in
   persistence changes). `persistence/ClosedSetsIT` is red when the constant has no such migration (or
   the migration no constant) in any of the four columns. Both sources are registered and implemented:
   `WYKOP`, and `REDDIT` (registered by `V12` in S-20, its code `sources/reddit/` since S-27); a new
   source still adds its own migration.
2. `sources/<source>/`: client, repository, item, mapper and configuration as in the layout below.
3. `<Source>Fetcher implements SourceFetcher` (`@Component`): `configured()` from the configuration's
   optional bean (Reddit: and a non-empty subreddit list); `fetch` = repository →
   `<Source>Posts.post(element)` per element, `skipped` for the unmappable ones.
4. `<Source>Posts implements RawInputMapper` (`@Component`) with a static `post(element)` →
   `SourcePost`; both go through `<Source>Mapper`, so storing and assessing map the same way.
5. Retries go through the shared loop `sources/transport/HttpRetry` (never a copy of it): the client
   names its retried statuses (`HttpRetry.transientStatus` — 429, 500–599 — plus any of its own) and its
   wait headers in order; transport errors are always retried; up to 3 attempts, the first readable wait
   header (seconds or an HTTP date) capped at 30 s or 2 s / 4 s, never past the deadline the repository
   sets for the whole fetch (below the watchdog's `PipelineStallSettings.MIN_DEFAULT`). The client keeps
   its messages' masking (`clean`, passed to the loop) and turns `HttpRetryException` into its own
   `<Source>ApiException` of the same kind.
6. Tests: `<Source>FetcherTest` and the mapper/client/repository tests below, on real fixtures.
7. The engine's tests of two sources (S-20; on the real Reddit since S-27): `pipeline/step/SourceIngestTest`
   with the scripted `ScriptedSourceFetcher` of the tests (unit, no Spring: posts of both, one empty,
   one throwing, neither with a post) and, on Postgres, `pipeline/PipelineRunIT` with both production
   sources over local servers — Wykop on `LocalWykopServer`, Reddit on `sources/reddit/LocalReddit` (one
   failing, empty or stopped next to the other; a Reddit post assessed on the input its raw payload maps
   to, equal to `RedditMapper`'s input of its fixture element — gate F1; removed posts neither fetched
   nor skipped; the same range again storing nothing twice). A source that fails or gives no post is
   its own result (`failed` / `empty` in `pipeline_run_sources`): the other sources' posts are stored
   and assessed and the run ends `partial` with `source_failed` / `source_empty`; only a run where no
   source gave a post fails. A new source adds its own fetcher and mapper tests (6) and its scenarios in
   the whole run on its own fixtures, through a test configuration like `LocalReddit`: its repository
   bean over its local server, configured per scenario, never by its environment setting.
8. The SPA names the source (S-29): its value in `values.source` of `szczur-radar-web/src/contract/rules.json`,
   its label in `labels.source` and its channel's prefix in `CHANNEL_PREFIXES` (`src/labels.ts`), which give
   `Wykop · #gielda`, `Reddit · r/stocks`. `SpaRulesTest` and `rules.test.ts` are red when one is missing. The
   API gives `channel` without a prefix, and `GET /api/freshness` lists the source after its first update.

Nothing in `pipeline/`, `posts/`, `raw/`, `agents/` or the API changes — in behaviour too: a new
source that fails or stays silent does not fail the other sources' run. If a step seems to need it,
the port is missing something: change the port (and the plan), don't special-case a source.

## Layout of a source: `sources/<source>/`

Reference implementations: `sources/wykop/` and `sources/reddit/` (Reddit's differences in the rows).

| Class | Job | Must not |
| --- | --- | --- |
| `<Source>ApiClient` | transport: base URL, auth and token handling, one request → parsed JSON, HTTP errors as `<Source>ApiException` (status + short body); retries only transient failures (429, 5xx, transport, and a source's own transient status) through `sources/transport/HttpRetry`, each retry a WARN line, within the deadline the repository passes in. Reddit: Arctic Shift has no key; its own transient status is 422 ("slow down"), its wait headers `Retry-After`, then `X-RateLimit-Reset`; an `error` in a 2xx envelope is `MALFORMED_ANSWER` | know about tags, dates, paging or the contract; retry silently, retry past the deadline or retry anything else; put keys, secrets or tokens in messages, logs, causes or `toString()` |
| `<Source>Repository` | reads elements: queries (tag/subreddit, time range), paging to the source's total with a consistency check per resource (distinct elements seen vs. page 1's total; an empty or short page before it, or a total that changes between pages, is inconsistent) and one second read from page 1 that replaces the first, pause between pages, a page limit; returns each element once as a `<Source>Item` next to its raw `JsonNode` (`WykopSearchResult.Element`). Reddit: Arctic Shift has no total or cursor and exclusive bounds, so it pages by time — the range one second wider at each end, each next page from the last post's second again (kept once by id), a short page ends a subreddit, a full page without a new id or going back in time is `INCOMPLETE_PAGING` —, every subreddit within one deadline; a post taken down (`removed_by_category`, `selftext` `[removed]` / `[deleted]`) is the element's state, not content: left out before counting (neither fetched nor skipped) and counted in an INFO line | map to the contract, filter by content, anonymize, store; return an incomplete result as complete — paging inconsistent in both reads is a failure of the source (`WykopFailure.INCOMPLETE_PAGING`) |
| `<Source>Item` | record of the fields the assessment needs, in the source's names (`@JsonIgnoreProperties(ignoreUnknown = true)`) | carry more than mapping needs (raw payloads are kept apart, see below) |
| `<Source>Configuration` | the Spring bean of the repository from `<SOURCE>_KEY`/`<SOURCE>_SECRET`; no keys → no bean (consumers inject `Optional<…Repository>`), only one of them → start fails. Reddit: from `REDDIT_SUBREDDITS` (a comma-separated list, not a secret); unset → no bean, a list naming no subreddit or an invalid name → start fails | require the keys or settings for the API, CI or tests to start |
| `<Source>Mapper` | `<Source>Item` → `AssessmentInputV1`: item type (`post`, `link`, `comment`), title/body, `published_at` with the zone offset, `channels` | pass author, URL or source id to the input; drop an unsupported element silently (`sources/UnmappableItemException`, shared by every source) |
| `<Source>Posts` | `SourcePost` from an element, with `channel` = the channel the mapper writes as the input's `channels[0]` (Wykop: `TAG`; Reddit: the subreddit); `RawInputMapper` from a stored payload | differ from `<Source>Mapper` in what the model sees |
| `<Source>Fetcher` | `SourceFetcher`: the range in the source's own clock and filter (Wykop: Warsaw time, tag `gielda`; Reddit: UTC seconds, the configured subreddits) | store, deduplicate against the database, assess |

- Text goes to the model unchanged (hard spaces, line breaks, emoji) except U+0000, which is removed
  (`text/Nul`; the database rejects it); empty or white-space-only text becomes `null` via
  `text/Whitespace`, never `String.strip`/`trim`.
- `SourcePost.channel` is the source's channel (Wykop's tag, a subreddit) in the source's own spelling,
  never empty, and the same value as the input's `channels[0]`; `posts.channel` is an open set (no enum,
  only an empty value is rejected).
- Closed value sets of a source (resource types, endpoints) are enums, not free strings.
- Keys and settings come from the environment (`<SOURCE>_KEY`, `REDDIT_SUBREDDITS`, …), never from the
  repo.
- Raw payloads go to separate storage from the normalized records (`raw/RawPayloadStore`;
  `context/foundation/lessons.md`, RAW vs. operational data): the source hands out the raw element in
  `SourcePost.raw`, the pipeline stores it. The source's own type names stay there.
- The no-content prefilter (`agents/assessor/Prefilter`) runs after mapping and is the same for
  every source; a source adds no own content filter.
- Not in a source: scheduling, deduplication against stored posts, persistence, assessment
  (`pipeline/`).

## Tests

- `<Source>MapperTest`, `<Source>ApiClientTest`, `<Source>RepositoryTest`, `<Source>FetcherTest` as
  `*Test` (no Spring, no Docker, no network). Client and repository run against a local `HttpServer`
  (`sources/LocalApiServer`, with a thin subclass per source for its API's root and fixtures:
  `LocalWykopServer`, `LocalRedditServer`): assert what is sent (path, query, auth header), paging and
  its end, re-auth, errors without secrets. Paging consistency is tested with answers by read and page
  number and with request counts: a quiet night asks for each page once, a deleted element (changed
  total), an empty or short page and duplicates hiding a gap are read again, two inconsistent reads
  fail; Reddit's paging by time: a post in the second that ends a full page read again and kept once, a
  full page without a new post or going back in time failing, removed posts left out and counted. Retries are
  tested with request counts (the count proves no hidden retries) and recorded waits on a fake clock: 5xx → success, 5xx × 3, 429 with `Retry-After`, 4xx once, auth
  retried, transport errors, the deadline, an interrupted wait, and no secret in the error or the WARN
  lines. The loop itself (status predicate, order of wait headers, cap, deadline, interrupt) is tested
  once in `transport/HttpRetryTest`; a client's test keeps its own messages and masking.
- Answers are **real payloads**, anonymized (nicks → `uzytkownikN`, URLs → `<url>`, ids renumbered,
  media keys removed), in `src/test/resources/fixtures/<source>/` (`fixtures/wykop/`; `fixtures/reddit/`,
  whose `README.md` gives the recording, its addresses and the anonymization command). A case that
  cannot be recorded (more pages than the recording has, a missing field) is the recording changed minimally in the
  test, with a comment saying what changed. Auth answers and error bodies may be short synthetic JSON
  in the API's shape (a real one holds a live token).
- Time zones: test a winter and a summer timestamp.
- The whole run over a source: `pipeline/PipelineRunIT` (Wykop on `LocalWykopServer`; Reddit on
  `sources/reddit/LocalReddit` — the production client, repository, fetcher and mapper over
  `LocalRedditServer`, configured per scenario by `LocalReddit.configure`, its answers switched by
  `down`, `empty`, `page`, `hold`, `delay`). No Spring test sets `REDDIT_SUBREDDITS`: a second
  `RedditRepository` bean next to `LocalReddit`'s would make the fetcher's `Optional` ambiguous.

## Live use

The application uses a source only through the pipeline, with its settings from the environment:
Wykop's keys (`WykopConfiguration`), Reddit's subreddits `REDDIT_SUBREDDITS` (`RedditConfiguration`;
optional, without it Reddit is not configured). Measurement code (`src/test/java/.../measurement/`, tag
`measurement`), e.g. `WykopGoldSampler`, builds its own client with keys in the shell — see its `README.md`.
