<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Kontrakt oceny LLM V2 z walidatorem i pomiarem na wzorcu

- **Plan**: context/changes/assessment-contract-harness/plan.md
- **Scope**: Phase 3 of 6
- **Reviewed phases**: 3
- **Date**: 2026-10-03
- **Verdict**: NEEDS ATTENTION
- **Findings**: 0 critical, 3 warnings, 7 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | WARNING |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | WARNING |
| Success Criteria | PASS |

Success criteria re-run on fdedac9: `./mvnw -B test` without LLM settings — 323 passed; `./mvnw -B verify` — 323 + 57 passed; CI run 37129654125 green (backend, web-unit, web-container-smoke, railway-config). Manual 3.7 confirmed by the user. No hidden retry layer left (Spring AI `NO_RETRY`, google-genai `attempts(1)` = `max(attempts,1)`, openai-java `maxRetries(0)` for sync and async clients); timeouts enforced per provider; `llm` main code does not import `agents`.

## Findings

### F1 — Provider error bodies go into exception messages uncapped and unmasked

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/llm/OpenRouterClient.java:101, GeminiClient.java:81
- **Detail**: OpenRouter and Gemini error texts go into `TransportException`/`LlmCallException` messages without a length cap (DeepSeek is capped at 500 in `HttpStatusErrors`). 401 bodies of OpenAI-style APIs quote a partially masked key; phase 4 will record failure messages. No 401 test exists.
- **Fix**: One helper in `TransportException` that caps every provider message at 500 characters and replaces the configured key with `<hidden>`; a 401 test per client asserting one attempt and no key in the message.
- **Decision**: FIXED — `ProviderMessages` (key → `<hidden>`, cap 500) used by all three clients and `HttpStatusErrors`; 401 test per client (break-checked: red without masking).

### F2 — 200 without choices is a success with empty text for DeepSeek and OpenRouter

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/llm/OpenRouterClient.java:107, DeepSeekClient.java:80
- **Detail**: Gemini rejects an answer without candidates (`GeminiClient:66`); DeepSeek and OpenRouter return `rawText = ""`, which surfaces later as `invalid_structured_output` instead of `provider_error`. Spring AI also logs the whole prompt at WARN ("No choices returned for prompt").
- **Fix**: Treat empty results as `REJECTED` in DeepSeek and OpenRouter, like Gemini; test each.
- **Decision**: FIXED — empty results are `REJECTED` (provider_error, no retry) in DeepSeek and OpenRouter; test per client. Spring AI's WARN log of the prompt on empty choices remains (library behaviour).

### F3 — "Only real payloads" rule vs. hand-modified payloads in tests

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/llm/CLAUDE.md; GeminiClientTest.java:73-88; OpenRouterClientTest.java:117
- **Detail**: `llm/CLAUDE.md` says test payloads are "always real recordings, never hand-written", but the thought-part, refusal and blocked cases mutate a real payload or are hand-written (they are hard to provoke live). Rule and tests disagree.
- **Fix**: Refine the rule: the success payload per provider is a real recording; edge cases (refusal, blocked, thoughts, errors) may be derived from it by a minimal, commented mutation in the test.
- **Decision**: FIXED — rule refined in `llm/CLAUDE.md` (real success recording; edge cases = commented minimal change of it); Gemini "blocked" case now derived from the real recording.

### F4 — Undocumented small drifts and loose ends

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: GeminiClient.java:81; ProviderSchemas.java:59-66; measurement/README.md (DeepSeek recipe); DeepSeekClient.java:74; GeminiClientTest
- **Detail**: Gemini cannot honour `Retry-After` (SDK does not expose it); `ProviderSchemas` also inlines `$ref` and drops `$defs`/`$schema` for every provider; the DeepSeek recording recipe uses "JSON Schema:" instead of the client's `SCHEMA_INSTRUCTION`; a stale comment names "DeepSeekErrors"; Gemini's `cachedTokens` (null in the payload) is not asserted. Only the code javadocs mention the first two.
- **Fix**: Add N4 to implementation-notes (Gemini `Retry-After`, `$ref` inlining), align the README recipe with `SCHEMA_INSTRUCTION`, fix the comment, assert Gemini `cachedTokens` is null.
- **Decision**: FIXED — N4 in implementation-notes; README DeepSeek recipe uses `SCHEMA_INSTRUCTION` and the fixture was re-recorded with it (696/141/512 tokens); stale comment fixed; Gemini `cachedTokens` asserted null.

### F5 — `LlmResponse.channel` is a free string for a closed set

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/llm/LlmResponse.java:27
- **Detail**: `channel` takes one of `gemini`, `deepseek`, `openrouter`, `openai/flex`, `openai` — a closed set; lessons.md ("Zamknięte zbiory wartości trzymaj w enumach") and the guidelines want an enum.
- **Fix**: `enum Channel` with `@JsonValue` values (`openai/flex` included), used by the three clients and the tests.
- **Decision**: FIXED — `enum Channel` (`gemini`, `deepseek`, `openrouter`, `openai/flex`, `openai`) in `LlmResponse` and the clients; the flex routing slug comes from it.

### F6 — DeepSeek finish reason `insufficient_system_resource` is treated as success

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/llm/DeepSeekClient.java:80
- **Detail**: Only `content_filter` is checked. DeepSeek also ends answers with `insufficient_system_resource` (truncated, server-side transient) — not verified against current docs. Today it would pass as success and fail parsing without a retry.
- **Fix**: Map `insufficient_system_resource` to `SERVER_ERROR` (retried) after confirming the value in DeepSeek's docs.
- **Decision**: FIXED — confirmed in DeepSeek API docs (`insufficient_system_resource`, `aborted`); Spring AI's enum lacks them so such answers failed deserialization. `DeepSeekRawResponse` reads the raw finish reason → SERVER_ERROR (retried); test for both values, break-checked.

### F7 — Hidden re-sends and timeout budget not documented

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/llm/LlmClientFactory.java:87-119
- **Detail**: OkHttp's default `retryOnConnectionFailure` may silently resend a request on a stale pooled connection (Gemini, OpenRouter; within the call timeout). For DeepSeek the connect timeout (≤30 s) adds to the read timeout, so one attempt can exceed `LLM_TIMEOUT`. Neither is in `llm/CLAUDE.md`.
- **Fix**: Document both in `llm/CLAUDE.md` (the SDKs do not expose the OkHttp flag; connect timeouts are bounded).
- **Decision**: FIXED — both documented in `llm/CLAUDE.md`.

### F8 — Clients are never closed

- **Severity**: 💡 OBSERVATION
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/llm/LlmClientFactory.java:87, 108-119
- **Detail**: Two OkHttp clients, a GenAI `Client` and a JDK `HttpClient` per `create()` are never closed. Harmless for the lazy singleton bean; measurement code calling `create()` repeatedly would leak pools and threads.
- **Fix A ⭐ Recommended**: Document "one `LlmClient` per run" in `measurement/README.md` and `llm/CLAUDE.md`; phase 4/6 create it once.
  - Strength: no API change; matches how the bean and the planned measurement runs use it.
  - Tradeoff: relies on discipline, not the type system.
  - Confidence: HIGH — every planned caller creates one client per run.
  - Blind spot: future S-02 code creating clients per post.
- **Fix B**: Make `LlmClient` `AutoCloseable` and close the SDK clients.
  - Strength: leaks become impossible with try-with-resources.
  - Tradeoff: every implementation and test must handle `close()`; more code now for a hypothetical caller.
  - Confidence: MED — google-genai `Client.close()` and OkHttp shutdown semantics not verified.
  - Blind spot: Spring's handling of `AutoCloseable` lazy beans at shutdown.
- **Decision**: FIXED via Fix A — "one LlmClient per run" in `llm/CLAUDE.md` and `measurement/README.md`.

### F9 — No test for a refused connection

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Success Criteria
- **Location**: szczur-radar-api/src/test/java/pl/szczurradar/api/llm/*ClientTest.java
- **Detail**: The CONNECTION kind (retried) is covered only in `TransportRetryTest` with scripted failures; no client test proves that a refused connection is classified as retryable for each SDK.
- **Fix**: One test per client against a closed port: 3 attempts, `provider_error`.
- **Decision**: FIXED — `refusedConnectionIsRetriedThreeTimes` per client against a refusing local port: 3 attempts, `provider_error`, waits 1 s and 2 s.

### F10 — Programming errors inside an attempt escape as raw RuntimeException

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/llm/TransportRetry.java:66
- **Detail**: Code inside the attempt but outside each client's `try` (building options, `ChatResponses.*`) can throw a raw `RuntimeException`, breaking the "throws `LlmCallException`" contract — only for bugs, not provider behaviour.
- **Fix**: Leave as is (bugs should surface loudly) and state it in `LlmClient`'s javadoc.
- **Decision**: FIXED — kept on purpose and documented in `LlmClient#complete` javadoc.
