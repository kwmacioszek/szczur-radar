# sources — data sources → assessment input

A source (Wykop now; Reddit and others later) only **acquires** elements and **maps** them to the
shared model `AssessmentInputV1` (`agents/assessor/contract`). Assessment, validation, persistence and
the API never change for a new source (`REPOSITORY_GUIDELINES.md`, PRD § Non-Functional Requirements).
Contract rules for the input: `context/foundation/llm-assessment/contract-v2.md` §2.

## Layout of a source: `sources/<source>/`

Reference implementation: `sources/wykop/`.

| Class | Job | Must not |
| --- | --- | --- |
| `<Source>ApiClient` | transport: base URL, auth and token handling, one request → parsed JSON, HTTP errors as `<Source>ApiException` (status + short body) | know about tags, dates, paging or the contract; retry silently; put keys, secrets or tokens in messages, logs or `toString()` |
| `<Source>Repository` | reads elements: queries (tag/subreddit, time range), paging to the end, pause between pages, a page limit; returns each element once as a `<Source>Item` next to its raw `JsonNode` (`WykopSearchResult.Element`) | map to the contract, filter by content, anonymize, store |
| `<Source>Item` | record of the fields the assessment needs, in the source's names (`@JsonIgnoreProperties(ignoreUnknown = true)`) | carry more than mapping needs (raw payloads are kept apart, see below) |
| `<Source>Configuration` | the Spring bean of the repository from `<SOURCE>_KEY`/`<SOURCE>_SECRET`; no keys → no bean (consumers inject `Optional<…Repository>`), only one of them → start fails | require the keys for the API, CI or tests to start |
| `<Source>Mapper` | `<Source>Item` → `AssessmentInputV1`: item type (`post`, `link`, `comment`), title/body, `published_at` with the zone offset, `channels` | pass author, URL or source id to the input; drop an unsupported element silently (`UnmappableItemException`) |

- Text goes to the model unchanged (hard spaces, line breaks, emoji); empty or white-space-only text
  becomes `null` via `text/Whitespace`, never `String.strip`/`trim`.
- Closed value sets of a source (resource types, endpoints) are enums, not free strings.
- Keys come from the environment (`<SOURCE>_KEY`, …), never from the repo.
- Raw payloads go to separate storage from the normalized records (`raw/RawPayloadStore`;
  `context/foundation/lessons.md`, RAW vs. operational data): the repository hands out the raw
  element, the pipeline stores it.
- A new source adds a constant to `sources/Source` (the stored `source` value, + the `CHECK` sets of
  `posts.source` and `raw_payloads.source` in a new migration).
- The no-content prefilter (`agents/assessor/Prefilter`) runs after mapping and is the same for
  every source; a source adds no own content filter.
- Not in a source: scheduling, deduplication, persistence (pipeline slices S-02/S-03).

## Tests

- `<Source>MapperTest`, `<Source>ApiClientTest`, `<Source>RepositoryTest` as `*Test` (no Spring, no
  Docker, no network). Client and repository run against a local `HttpServer` (`LocalWykopServer`):
  assert what is sent (path, query, auth header), paging and its end, re-auth, errors without secrets.
- Answers are **real payloads**, anonymized (nicks → `uzytkownikN`, URLs → `<url>`, ids renumbered,
  media keys removed), in `src/test/resources/fixtures/<source>/`. A case that cannot be recorded
  (more pages than the recording has, a missing field) is the recording changed minimally in the
  test, with a comment saying what changed. Auth answers and error bodies may be short synthetic JSON
  in the API's shape (a real one holds a live token).
- Time zones: test a winter and a summer timestamp.

## Live use

The application uses the source only through the pipeline (S-02), with keys from the environment
(`WykopConfiguration`). Measurement code (`src/test/java/.../measurement/`, tag `measurement`), e.g.
`WykopGoldSampler`, builds its own client with keys in the shell — see its `README.md`.
