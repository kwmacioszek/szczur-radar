# Implementation notes — assessment-contract-harness

Findings from the implementation that the plan did not foresee, and checks they leave open for
later phases. Each open check names the phase that closes it; `/10x-impl-review` reads this file.

## Phase 3 — LLM infrastructure (2026-10-03)

### N1. Gemini does not receive `additionalProperties` / `uniqueItems` — resolved (phase 4)

- **What:** Spring AI 2.0.1 (`GoogleGenAiChatModel`) converts the response schema to the GenAI
  SDK's `Schema` type, which has no `additionalProperties` and no `uniqueItems`; both are dropped
  silently. Property order, `required`, enums and length limits do reach Gemini. Pinned in
  `GeminiClientTest.sendsTheSchemaWithoutCountLimitsAndReadsTextAndUsage`; the plan (phase 3)
  assumed `additionalProperties` would be sent to every provider.
- **Impact:** no contract risk — `OutputParser` validates every answer against the full V2 schema,
  so an extra key or a repeated modifier/flag is a `schema_violation`, never an accepted
  assessment. Possible cost: a lower compliance rate for Gemini if it emits such answers.
  Evidence so far: Python smoke 2026-10-01 Gemini 100% compliance; the recorded payload
  (`fixtures/gemini/generate-content.json`) passes parser + D1–D15.
- **Open check (phase 4, `LiveSmokeTest` for Gemini):** count `schema_violation`s caused by
  `additionalProperties` (unknown key) or `uniqueItems` (repeated value). If there are any, put
  the count in the PR description and decide: accept, or call the GenAI SDK directly for Gemini
  with the raw schema (first verify that the Gemini API accepts `additionalProperties` in
  `responseJsonSchema`).
- [x] Checked in phase 4 — result: live smoke 2026-10-04 (30 calls): 0 `schema:additionalProperties`, 0 `schema:uniqueItems`. Accepted as is.

### N2. OpenAI strict mode rejects `uniqueItems` — resolved (checked in phase 4)

- Recording OpenRouter/Luna returned HTTP 400 `'uniqueItems' is not permitted`
  (`invalid_json_schema`). `ProviderSchemas` now drops `uniqueItems` for OpenRouter
  (`ProviderSchemasTest.openRouterAlsoLosesUniqueItemsWhichOpenAiStrictModeRejects`). Uniqueness of
  `modifiers`/`flags` is still enforced by `OutputParser`. Same impact and check as N1 (repeated
  values become `schema_violation`), for OpenRouter.
- [x] Checked in phase 4 (`LiveSmokeTest` for Luna) — result: live smoke 2026-10-04 (30 calls, flex): 0 `schema:uniqueItems`, 0 `schema:additionalProperties`.

### N3. Facts confirmed by the recordings — resolved

- OpenRouter passes the flex tier through (`"service_tier": "flex"` in the answer); temperature 0
  is accepted for Luna. One recorded call cost $0.00017.
- DeepSeek accepts `deepseek-v4-flash` and reports the model as `deepseek-flash`; cache hits are
  in `usage.prompt_cache_hit_tokens` (Spring AI drops them, `DeepSeekCacheUsage` reads them).
- `llm.CallFailure` (`provider_error` | `timeout`) instead of the contract's `FailureClass` in
  `LlmCallException`, so `llm` does not import `agents/assessor`; phase 4's `Assessor` maps it.

### N4. Smaller deviations found by the phase 3 review — resolved

- Gemini cannot honour `Retry-After`: the GenAI SDK's `ApiException` exposes no headers, so 429s
  from Gemini wait `min(2^(n-1), 30)` s (1 s, 2 s). OpenRouter and DeepSeek use the header.
- `ProviderSchemas` also inlines `$ref` (from `#/$defs/...`) and drops `$defs` and `$schema` for every
  provider — Spring AI's Gemini mapping loses `$ref`, and one inlined form is simplest to send to all.
- Error texts from providers go through `ProviderMessages` (key → `<hidden>`, capped at 500
  characters) before they become exception messages: some 401 answers quote the key (review F1).
- Test payloads: the success response per provider is a real recording; refusal, blocked answer,
  thought parts and empty choices are that recording changed minimally in the test (review F3).
- The DeepSeek fixture was re-recorded with the client's exact system prompt
  (`DeepSeekClient.SCHEMA_INSTRUCTION`); token counts in `DeepSeekClientTest` follow it.
- DeepSeek finish reasons `insufficient_system_resource` and `aborted` (documented in DeepSeek's
  Chat Completions API) are missing from Spring AI's enum: such an answer failed deserialization
  (`RestClientException`) and became a non-retried `provider_error`. `DeepSeekRawResponse` now
  reads the raw finish reason and the client retries it as a server error (review F6).
- `LlmResponse.channel` is the enum `Channel` (review F5); one `LlmClient` per run (review F8).

## Phase 4 — prompt, Assessor, agent (2026-10-04)

### N5. Gemini never answered `null` to a nullable enum — resolved

- **What:** first live smoke for Gemini (`gemini-3.1-flash-lite`, prompt v2.2): 19/30 = 63.3%
  compliance, all 11 failures D12 — `assertiveness` set for `not_expressed` / `unclear` stances.
  The schema sent `assertiveness` as `{"enum": ["hedged", "unhedged", null]}` (no type); the Python
  client (smoke 2026-10-01, 100%) sent Pydantic's nullable form. Same prompt, no D12 at DeepSeek or
  Luna — so the schema, not the prompt.
- **Fix:** `ProviderSchemas` rewrites, for Gemini only, a string enum that contains `null` as
  `{"anyOf": [{"type": "string", "enum": [...]}, {"type": "null"}]}`. Checked on the client's real
  request path: Spring AI's GenAI mapping keeps this form, while `"type": ["string", "null"]` fails
  in the GenAI SDK (`Cannot deserialize ... Type from Array`) and `nullable` is dropped. Tests:
  `ProviderSchemasTest.onlyGeminiGetsTheNullableEnumAsAnyOf`, `GeminiClientTest`.
- [x] Gemini `LiveSmokeTest` re-run after the fix — result: 30/30 = 100% (2026-10-04, prompt v2.2), no failures; D12 gone.

### N6. Live smoke 2026-10-04 — other observations

- DeepSeek Flash (`deepseek-v4-flash`, reported as `deepseek-flash`): 27/30 = 90.0%, exactly at the
  phase 4 bar. 2× `schema_violation` — the last key of a target (`reference`) missing, the same
  family as the dropped closing braces of 2026-10-01; 1× `fabricated_evidence`. To watch in the
  phase 6 report (gold threshold 95%).
- Luna flex: 28/30 = 93.3%, both failures `fabricated_evidence` in an anchor; 0 retries, 0 flex
  fallbacks (no 429 in 30 calls); median 9.1 s.
- Cache: DeepSeek and OpenRouter report cached tokens on every call; Gemini reported none.

## Phase 5 — gold set (2026-10-04)

### N7. Wykop fixtures — resolved (real search answers)

- First version: fixtures rebuilt from the pilot sample (no raw pages in the repo). Replaced on
  2026-10-04 by real answers of the search API for #gielda, 2026-10-01, anonymized (nicks →
  `uzytkownikN`, URLs → `<url>`, ids renumbered, media keys removed; checked: no real nick left):
  `search-entries-page.json` (last page of the day, 12 of 262 entries), `search-links-page.json`
  (all 4 links), `entry.json` / `link.json` (one element of each). Field names match the official
  schemas (`doc.wykop.pl`, `components/schemas/entry.yaml`, `link.yaml`).

### N8. `AnchorMatcherTest` separate from `GoldMetricsTest`

- The plan put the anchor boundary cases into `GoldMetricsTest`; they live in `AnchorMatcherTest`
  (same cases plus 1:1 maximum matching and tie order). `WykopAnonymizerTest` covers the sample's
  anonymization offline, in CI.

### N9. Wykop search API and a connector in `src/main` — decided with the user (2026-10-04)

- **Endpoint:** user asked for the search API with a date range. Checked live (#gielda,
  2026-10-01): `GET search/all` has no pages and returns a preview (entries: `total` 262, 3 items);
  `GET search/entries` + `GET search/links` with `tags[]`, `date_from`/`date_to`, `sort=newest`,
  `limit=50`, `page` return the whole day (5 × 50 + 12 = 262 entries; 4 links). Without `votes`
  the search is not limited to popular elements (entries with 0 votes come back). User chose
  entries + links.
- **Connector:** user asked to split the connector out of `WykopGoldSampler`:
  `sources/wykop/WykopApiClient` (auth with key/secret → token, re-auth once on 401, `GET`, errors
  without secrets, no retries) and `WykopRepository` (search by tag and time range, paging to
  `pagination.total`, pause between pages, page limit), plus `WykopResource` (enum) and
  `sources/CLAUDE.md` (how to add a source). Production code beyond the plan's phase 5 (which put
  only the mapper in `src/main`); not wired into any bean, route or schedule — S-02 does that, so the
  deploy does not change the service's behaviour.
