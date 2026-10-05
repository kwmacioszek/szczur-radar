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

### N10. Media flags and local links in the gold sample (2026-10-04)

- The labeling sheet showed text only, so a post whose sense is in a picture (e.g. 2026-10-01-003
  „Taka ciekawostka z GPW”) gave no hint; the pilot had `has_photo`, the gold sampler dropped it.
  `posts.json` now has `media: {photo, embed, survey}` per post (34 photos, 4 embeds in 120) and the
  sampler writes `{post_id: url}` to `~/.config/szczur-radar/gold-links.json` (outside the repo); the
  sheet shows 📷/🎬 and „otwórz na Wykopie” and states the rule: label the text as the model sees it,
  a picture only tells that the sense is elsewhere (§5.5). Re-sampled with the same days and seed:
  all 120 `post_id`s and inputs identical, so labels made earlier still apply.
- Pictures as model input: parked in the roadmap (v2.7) as nice-to-have after MVP; the gold set
  (`media.photo` + labels) and the phase 6 report give the size of the gap.

### N11. Gold set labeled; contract §6.5a and prompt v2.3 (2026-10-04)

- `labels.json`: 120/120 posts labeled by the user (instructions v1.0 → v1.1), 8 "hard", 112 counted;
  relevance 86 / 18 / 16 (investment / not / unclear); directions bearish 32, not_expressed 23,
  bullish 22, unclear 13 (no neutral, no mixed left). `GoldDataTest` green.
- Labeling went through a mechanical rule check (anchor is a fragment of the post, dictionary
  non-targets, tags, one target per subject, `neutral`/`mixed` against their definitions); every
  correction was the user's decision, asked post by post. The assistant did not judge the meaning
  of posts, so the gold set stays the user's.
- Contract change (§16), user's decision: slang of a whole-market move ("dzien kibla", "pompa",
  "spadki", "bessa", "krach") without a named target → target "rynek" (`macro_topic`), as "na
  peronie" (§7.1.6) — new §6.5a; the prompt said "NIE są celami", which would have penalized models
  for following it. §6.4 gets the mood-tag example (`#krachsmieciuchindex`). Prompt v2.3 (hash in
  `AssessmentPromptTest`); `examples.md` example 6 stays valid (the move there concerns the named
  index). Phase 4's live smoke was on v2.2; phase 6 records v2.3.

### N12. Phase 5 review fixes (2026-10-04, `reviews/impl-review-phase-5.md`)

- Prompt v2.4: the slang dictionary had „peron … (NIE jest celem)” against §7.1.6/§6.5a; v2.3 was
  never recorded. Phase 6 records v2.4.
- `WykopRepository` returns `WykopSearchResult` (items once by resource and id, skipped count); a page
  short of `pagination.total` is an error; `WykopApiException` carries `WykopFailure`.
- Anonymizer: case-insensitive author nicks, e-mails → `<email>`; changes future samples only — the
  committed `posts.json` was checked clean and is not re-drawn (its labels stay valid).
- Wykop fixtures: `gender`, `rank.position` nulled, `type_id` renumbered.

## Phase 6 — measurement (2026-10-05)

### N13. First gold report, gold completion and contract §7.1.7 (prompt v2.5)

- Recordings on prompt v2.4 (120 posts × 3 models; Luna's 6 posts whose duration included the laptop
  sleeping were recorded again). No model met all thresholds; compliance, relevance and recall
  passed for all three.
- Precision: models named targets the labels missed. Candidates found by ≥ 2 models (34) were put to
  the user one by one (survey); each decision was the user's (add with direction / reject / alias /
  relevance change) — 16 posts changed, 2 aliases (`gold/aliases.json`: „#PKN” = „#ORLEN”,
  „ogłoszenie pokoju” = „iranem”, §6.7), rest rejected. Recomputed offline: precision 80–83 % for all.
  The report states that the candidates came from models.
- Direction: most disagreements were a definition gap, not model error — the user labels the price
  move a post describes („czemu spadki na XTB?”, „Samsung +5%, Micron na czerwono”) as a direction,
  the contract said `not_expressed`. User's decision: contract §7.1.7 (described move or question
  about its cause → the move's direction, hedged), §7.1.2 ironic praise of a loss, guidelines v1.2;
  prompt v2.5 also tells the model not to fall back to `not_expressed` when holding, buying or a loss
  points to a direction (Luna's main error). These prompt rules come from analyzing disagreements on
  the gold set itself; the next report must say so.
- All three configurations are recorded again on v2.5 (older recordings no longer count).
- Luna is measured on the default tier (`models.json` id `luna`, user's decision 2026-10-05: flex took
  ~20 s per post and hours with retries). Same model; flex changes queueing and price, not answers —
  the report also prices the recorded tokens at the flex rate. Production can still run flex
  (`LLM_SERVICE_TIER`); its refusal rate (429) and latency were seen in phase 4 (0 refusals in 30
  calls, median 9 s) and in the v2.4 recording run (0 fallbacks in 120 calls).

### N14. Direction metric, sample screen of six variants (2026-10-05)

- Direction accuracy counts `unclear` and `not_expressed` as the same answer ("no usable direction"),
  user's decision; contract unchanged (`unclear` keeps its meaning; merging it is a post-MVP §16
  candidate). Prompt v2.5, full set: Luna 73.0 %, DeepSeek and Gemini 64.9 % (threshold 75 %).
- `GoldRecorder -Dgold.sample=4` (every 4th post, 30) and the report's subset section screened six
  OpenRouter variants: DeepSeek with reasoning low/high, Luna low/high, GLM 5.3 Flash (low),
  MiMo v2.6 Flash. More reasoning did not improve direction or contradictions (DeepSeek high: 63 %
  direction, 27 % contradictions) and costs 10–25× more; GLM and MiMo were weaker; Luna low matched
  Luna medium at half the output tokens. No variant meets direction ≥ 75 % and contradictions ≤ 3 %
  (irony on Wykop). Next: full run of `luna-low` (default tier, user's choice: flex is slow), then a
  decision on thresholds and the default model.
- OpenRouter routed DeepSeek between providers (cache hits ~54 %); pinning a provider
  (`provider.only`) would need a new `LLM_*` setting — not done, since reasoning did not pay off.
- The report bills DeepSeek by the recording's time of day (peak 01–04 and 06–10 UTC on weekdays).

### N15. Full run of luna-low and four more OpenRouter models (2026-10-05)

- `luna-low` on all 120 posts: direction 63.5 %, dominant 66.7 %, contradictions 11.7 % — worse than
  Luna medium (73.0 % / 76.2 % / 8.3 %); the 30-post screen had hidden it (noise).
- 30-post screen: GPT-4o-mini compliance 93.3 %, direction 52.6 %; Qwen3 235B Instruct 2507 compliance
  53.3 % (12 × `schema:additionalProperties` — OpenRouter's upstreams do not enforce strict schema);
  Llama 4 Scout 25/30 `provider_error` (HTTP 429 from the provider after 3 attempts — rate limit, not
  measured); Qwen3.5-9B not recorded (user: problems with the run).
- The report no longer bills calls without an answer (`provider_error`, `timeout`).

### N16. Jev (pilot 9), default model and relaxed thresholds (2026-10-06)

- Pilot 9 (`frame.md`): Jev as a direction classifier on the gold targets 60.8 % (Luna 70.3 % on the
  same approximate matching), irony score vs the user's irony labels AUC 0.49, with the day's other
  posts as context too (9b). Per the user's irony labels most contradictions are on literal posts, so
  the "mostly irony" claim in `hardening-sources.md` and the roadmap was corrected.
- User's decision (plan phase 6, step 4 compromise): `default` = `luna`; direction accuracy ≥ 70 %
  (was 75 %) and contradiction rate ≤ 10 % (was 3 %) in `GoldMetrics` and criterion 6.3 — chosen over
  a per-model exception as a margin, with the original thresholds as the post-MVP hardening goal.
  Luna's margin is small: 2 targets on direction, 1 on contradictions.
- Luna was recorded on the standard tier; flex is the same model at Batch prices, so the operator
  picks the tier in production (`deploy-plan.md`, "F-02: model domyślny"). The app does not log the
  `LLM_*` settings at start, so the runbook verifies them with `railway variable list` (adaptation of
  "weryfikacja w logu startu").

### N17. GPT-6.1 Sol on the 30-post sample (2026-10-06)

- `gpt-6.1-sol` (OpenRouter, provider-default reasoning, $2 / $0.10 cached / $10 per 1M): compliance
  100 %, direction 63.2 % vs Luna 73.7 %, contradictions 4/15 vs 2/15 on the same posts; $0.14 for 30
  posts (92 % of input from cache, ~380 output tokens per post), ~$55/month at 12k posts. A stronger
  model did not fix direction — consistent with N14/N15; not measured on the full set.
- User approved Luna's cost (~$5.9/month standard, ~$2.9 flex) on 2026-10-06.
