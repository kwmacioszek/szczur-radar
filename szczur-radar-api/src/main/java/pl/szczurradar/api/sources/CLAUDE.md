# sources — data sources → the shared model

A source (Wykop now; Reddit and others later) only **acquires** elements and **maps** them to the
shared model: `SourcePost` for storing, `AssessmentInputV1` (`agents/assessor/contract`) for
assessing. Assessment, validation, persistence, the pipeline and the API never change for a new
source (`REPOSITORY_GUIDELINES.md`, PRD § Non-Functional Requirements). Contract rules for the input:
`context/foundation/llm-assessment/contract-v2.md` §2.

## The two ports the pipeline uses

The pipeline (`pipeline/`) knows sources only through two interfaces in this package, collected as
Spring beans (`List<…>` injected, keyed by `source()`). It never names a source.

| Interface | Called by | Contract |
| --- | --- | --- |
| `SourceFetcher` | fetch step of a range run (`pipeline/step/SourceIngest`) | `source()`; `configured()` (e.g. keys set — an unconfigured source is never fetched and a request naming it is refused); `fetch(from, to)` → `Fetched(posts, fetched, skipped)`: the posts added in the range as `SourcePost`s, each once; elements that cannot become a post are counted as `skipped`, never thrown; a failure of the source itself is thrown (the run fails) |
| `RawInputMapper` | assess step, for new and retried posts alike (`pipeline/assess/AssessmentInputs`) | `source()`; `map(raw)` → the `AssessmentInputV1` of a stored post, rebuilt from the raw payload the fetcher put in `SourcePost.raw` — so a retry gets exactly the input of the first attempt |

`SourcePost` (`sources/SourcePost`): the shared fields, already mapped — `itemType` (shared
`ItemType`: `post`/`link`/`comment`, never the source's own names), `sourceItemId` (unique per source
and item type), `publishedAt` (an instant), `title`/`body` (mapped as for the input: unchanged text,
blank → `null`), `author`, `slug`, `webUrl`, `tags`, and `raw` — the element's JSON bytes, exactly
what `RawInputMapper.map` will get back.

Who chooses the sources: a range run stores its list (`pipeline_runs.sources`); the admin names it or
leaves it empty for every configured source (`pipeline/step/SourceIngest.resolve`). Retry and posts runs fetch
nothing.

## Adding a source (e.g. Reddit)

1. `sources/Source`: the constant (its `value()` is what is stored), and one Flyway migration adding
   that value to the `CHECK` sets of `posts.source`, `raw_payloads.source` and
   `pipeline_runs.sources` (registering the source; nothing else in persistence changes).
2. `sources/<source>/`: client, repository, item, mapper and configuration as in the layout below.
3. `<Source>Fetcher implements SourceFetcher` (`@Component`): `configured()` from the configuration's
   optional bean; `fetch` = repository → `<Source>Posts.post(element)` per element, `skipped` for the
   unmappable ones.
4. `<Source>Posts implements RawInputMapper` (`@Component`) with a static `post(element)` →
   `SourcePost`; both go through `<Source>Mapper`, so storing and assessing map the same way.
5. Retries follow `WykopApiClient`'s policy: the same statuses (429, 500–599, transport errors), up to
   3 attempts, `Retry-After` capped at 30 s or 2 s / 4 s, never past the deadline the repository sets for
   the whole fetch (below the watchdog's `PipelineStallSettings.MIN_DEFAULT`). The second source with
   retries moves the shared loop into `sources/` instead of copying it a third time
   (`context/foundation/lessons.md`, abstraction while the code is small).
6. Tests: `<Source>FetcherTest` and the mapper/client/repository tests below, on real fixtures.
7. The second source adds to `pipeline/step/SourceIngestTest` the case of two sources, one of them
   without a post: `NoPostsFetchedException` names only the empty source, and its counters are the sum
   of both. It cannot be written with one source (`SourceIngest` keys its fetchers by `Source`). Today an
   empty source fails the whole `fetch` step, also the other sources' posts; isolating the sources is
   Parked (`context/foundation/roadmap.md`, `## Parked`).

Nothing in `pipeline/`, `posts/`, `raw/`, `agents/` or the API changes. If a step seems to need it,
the port is missing something: change the port (and the plan), don't special-case a source.

## Layout of a source: `sources/<source>/`

Reference implementation: `sources/wykop/`.

| Class | Job | Must not |
| --- | --- | --- |
| `<Source>ApiClient` | transport: base URL, auth and token handling, one request → parsed JSON, HTTP errors as `<Source>ApiException` (status + short body); retries only transient failures (429, 5xx, transport), each retry a WARN line, within the deadline the repository passes in | know about tags, dates, paging or the contract; retry silently, retry past the deadline or retry anything else; put keys, secrets or tokens in messages, logs, causes or `toString()` |
| `<Source>Repository` | reads elements: queries (tag/subreddit, time range), paging to the source's total with a consistency check per resource (distinct elements seen vs. page 1's total; an empty or short page before it, or a total that changes between pages, is inconsistent) and one second read from page 1 that replaces the first, pause between pages, a page limit; returns each element once as a `<Source>Item` next to its raw `JsonNode` (`WykopSearchResult.Element`) | map to the contract, filter by content, anonymize, store; return an incomplete result as complete — paging inconsistent in both reads is a failure of the source (`WykopFailure.INCOMPLETE_PAGING`) |
| `<Source>Item` | record of the fields the assessment needs, in the source's names (`@JsonIgnoreProperties(ignoreUnknown = true)`) | carry more than mapping needs (raw payloads are kept apart, see below) |
| `<Source>Configuration` | the Spring bean of the repository from `<SOURCE>_KEY`/`<SOURCE>_SECRET`; no keys → no bean (consumers inject `Optional<…Repository>`), only one of them → start fails | require the keys for the API, CI or tests to start |
| `<Source>Mapper` | `<Source>Item` → `AssessmentInputV1`: item type (`post`, `link`, `comment`), title/body, `published_at` with the zone offset, `channels` | pass author, URL or source id to the input; drop an unsupported element silently (`UnmappableItemException`) |
| `<Source>Posts` | `SourcePost` from an element; `RawInputMapper` from a stored payload | differ from `<Source>Mapper` in what the model sees |
| `<Source>Fetcher` | `SourceFetcher`: the range in the source's own clock and filter (Wykop: Warsaw time, tag `gielda`) | store, deduplicate against the database, assess |

- Text goes to the model unchanged (hard spaces, line breaks, emoji) except U+0000, which is removed
  (`text/Nul`; the database rejects it); empty or white-space-only text becomes `null` via
  `text/Whitespace`, never `String.strip`/`trim`.
- Closed value sets of a source (resource types, endpoints) are enums, not free strings.
- Keys come from the environment (`<SOURCE>_KEY`, …), never from the repo.
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
  (`LocalWykopServer`): assert what is sent (path, query, auth header), paging and its end, re-auth,
  errors without secrets. Paging consistency is tested with answers by read and page number and with
  request counts: a quiet night asks for each page once, a deleted element (changed total), an empty
  or short page and duplicates hiding a gap are read again, two inconsistent reads fail. Retries are
  tested with request counts (the count proves no hidden retries) and recorded waits on a fake clock: 5xx → success, 5xx × 3, 429 with `Retry-After`, 4xx once, auth
  retried, transport errors, the deadline, an interrupted wait, and no secret in the error or the WARN
  lines.
- Answers are **real payloads**, anonymized (nicks → `uzytkownikN`, URLs → `<url>`, ids renumbered,
  media keys removed), in `src/test/resources/fixtures/<source>/`. A case that cannot be recorded
  (more pages than the recording has, a missing field) is the recording changed minimally in the
  test, with a comment saying what changed. Auth answers and error bodies may be short synthetic JSON
  in the API's shape (a real one holds a live token).
- Time zones: test a winter and a summer timestamp.
- The whole run over a source: `pipeline/PipelineRunIT` (Wykop on `LocalWykopServer`).

## Live use

The application uses a source only through the pipeline, with keys from the environment
(`WykopConfiguration`). Measurement code (`src/test/java/.../measurement/`, tag `measurement`), e.g.
`WykopGoldSampler`, builds its own client with keys in the shell — see its `README.md`.
