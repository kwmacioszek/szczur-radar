# Implementation notes — assessment-contract-harness

Findings from the implementation that the plan did not foresee, and checks they leave open for
later phases. Each open check names the phase that closes it; `/10x-impl-review` reads this file.

## Phase 3 — LLM infrastructure (2026-10-03)

### N1. Gemini does not receive `additionalProperties` / `uniqueItems` — open, check in phase 4

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
- [ ] Checked in phase 4 — result: …

### N2. OpenAI strict mode rejects `uniqueItems` — resolved

- Recording OpenRouter/Luna returned HTTP 400 `'uniqueItems' is not permitted`
  (`invalid_json_schema`). `ProviderSchemas` now drops `uniqueItems` for OpenRouter
  (`ProviderSchemasTest.openRouterAlsoLosesUniqueItemsWhichOpenAiStrictModeRejects`). Uniqueness of
  `modifiers`/`flags` is still enforced by `OutputParser`. Same impact and check as N1 (repeated
  values become `schema_violation`), for OpenRouter.
- [ ] Checked in phase 4 (`LiveSmokeTest` for Luna) — result: …

### N3. Facts confirmed by the recordings — resolved

- OpenRouter passes the flex tier through (`"service_tier": "flex"` in the answer); temperature 0
  is accepted for Luna. One recorded call cost $0.00017.
- DeepSeek accepts `deepseek-v4-flash` and reports the model as `deepseek-flash`; cache hits are
  in `usage.prompt_cache_hit_tokens` (Spring AI drops them, `DeepSeekCacheUsage` reads them).
- `llm.CallFailure` (`provider_error` | `timeout`) instead of the contract's `FailureClass` in
  `LlmCallException`, so `llm` does not import `agents/assessor`; phase 4's `Assessor` maps it.
