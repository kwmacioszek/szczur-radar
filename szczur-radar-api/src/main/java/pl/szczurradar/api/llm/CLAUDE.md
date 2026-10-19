# llm — provider clients

## Where things are

```
llm/            the API — the only part the rest of the application imports
  LlmClient (complete, check), LlmRequest, LlmResponse, LlmCallException, LlmConfigurationException,
  CallFailure, Channel, OutputMode, LlmConfiguration (the lazy bean), LlmClientFactory (a switch over
  the provider), LlmProviderProperties (the LLM_* contract, read and validated by from()), Provider,
  ServiceTier, ReasoningEffort
  api/          LlmCheckController — GET /api/admin/llm/check (admin)
  gemini/       GeminiClient       — create(): GenAI client, 1 attempt, timeout
  deepseek/     DeepSeekClient, DeepSeekRawResponse — create(): RestClient, no retry, timeouts
  openrouter/   OpenRouterClient, OpenRouterRawResponse — create(): openai-java clients (the sync one
                on OpenRouterRawResponse), 0 retries, timeout
  transport/    shared by the clients: TransportRetry (the only retries), TransportException,
                HttpStatusErrors, ProviderMessages, ChatResponses, ProviderSchemas
```

Each provider package builds its own SDK client in a static `create(properties, baseUrl, retry,
messages)`; `LlmClientFactory.create(properties, baseUrl, sleeper)` is the test seam (a local
`ScriptedProviderServer`, recorded waits).

Infrastructure only: no assessment contract here (the contract lives in `agents/assessor`; this
package must not import it). `LlmClient.complete(LlmRequest)` returns the model's raw text and the
call's metadata, or throws `LlmCallException` (`provider_error` | `timeout`). White space (e.g. a
blank answer) is judged with the shared `text/Whitespace`, never `isBlank`. An answer without text is
`REJECTED` (`provider_error`, no retry) for every provider, whatever the finish reason
(`ChatResponses.requireText`).

- One provider per run from `LLM_*` (`LlmProviderProperties`); the provider's `create()`
  builds a Spring AI `ChatModel` outside Embabel's model beans. Embabel's `createObject` is never used for
  assessments (lenient parsing, semantic retries, schema pasted into the prompt).
- **Retries live only in `TransportRetry`**: up to 3 attempts per tier on 429, 5xx, time-out and
  connection errors; `Retry-After` (≤ 60 s) or `min(2^(n-1), 30)` s; flex → default after three
  429s. Everything below is off and must stay off: Spring AI `RetryTemplate` (`TransportRetry.SPRING_AI_OFF`),
  google-genai `HttpRetryOptions.attempts(1)`, openai-java `maxRetries(0)`, DeepSeek `RestClient`
  without retry. Parse errors and contract violations are never retried.
- **A refused configuration is not a failed request:** HTTP 401 (key), 402 (credit) and 404 (model)
  are `TransportException.Kind.CONFIGURATION`, never retried, and end as `LlmConfigurationException`
  (a subclass of `LlmCallException`). `Assessor` rethrows it instead of recording a `failed` result,
  and the pipeline stops the run (`pipeline/CLAUDE.md`), so a revoked key never uses up posts'
  attempts. 403 stays `REJECTED`: a provider may refuse one request's content with it (OpenRouter
  moderation). The exception is OpenRouter's 403 for a spent budget — `error.message` starting with
  "Key limit exceeded", or `error.metadata.limit_source` `openrouter_key_limit` /
  `openrouter_workspace_budget` / `openrouter_guardrail_budget` (openrouter.ai/docs/api_reference/limits,
  openrouter.ai/docs/guides/best-practices/capping-shared-agent-keys): `OpenRouterClient` makes it
  `CONFIGURATION`, so a key whose credit limit runs out stops the run until the limit resets;
  `TransportException.kindOf` stays generic. The docs also list a spent key limit under 402, already the
  configuration. Gemini answers an invalid key with 400 — `REJECTED` per call, caught by the check below.
- `LlmClient.check()`: one tiny real call (`CHECK_REQUEST`, a few dozen tokens) through the same path
  as an assessment — it proves the key, the model and the credit together. Listing models would not:
  DeepSeek accepts model ids it does not list (e.g. `deepseek-v4-flash`). Used before every pipeline
  assess step and by `GET /api/admin/llm/check`.
- Every HTTP attempt has the `LLM_TIMEOUT` limit (the Gemini SDK has none by default). For DeepSeek
  the connect phase (≤ 30 s) comes on top of it, so one attempt can last slightly longer.
- Known re-send below our loop: OkHttp (inside the GenAI and openai-java SDKs) may resend a
  request once when a pooled connection turns out dead, within the same attempt's time limit. The
  SDKs do not expose the switch; such a re-send never reaches the provider twice as a completed
  call.
- One `LlmClient` per run: `LlmClientFactory.create()` opens HTTP pools and threads that are not
  closed (the app has one lazy bean); measurement code creates the client once and reuses it.
- Error texts from providers pass through `ProviderMessages` (key → `<hidden>`, ≤ 500 characters)
  before they become exception messages.
- DeepSeek: Spring AI's finish-reason enum lacks `insufficient_system_resource` and `aborted`;
  `DeepSeekRawResponse` reads them from the raw answer and they are retried as server errors.
- OpenRouter reports an error that occurs once the model has started on the request in the body of
  an **HTTP 200**: `{"error":{"code","message","metadata":{"error_type"}}}` instead of `choices`
  (openrouter.ai/docs/api/reference/errors-and-debugging) — e.g. a flex refusal (seen on production,
  2026-10-09). openai-java then fails with "`choices` is not set". `OpenRouterRawResponse` (an
  openai-java `HttpClient` around its OkHttp client; the SDK builder has no interceptor) keeps the raw
  2xx answer per thread, and `OpenRouterClient` classifies `error.code` like the HTTP status (429
  retried and flex → default, 5xx retried, 401/402/404 and a 403 for a spent budget the configuration,
  other 4xx `REJECTED`). A 2xx answer without `choices` and without an error (missing or empty list) is
  retried like a 5xx. If generation fails after partial output, the error is in `choices[0].error`; it
  is classified before accepting the result, and the partial text is discarded.
- `ProviderSchemas` sends each provider the schema subset it accepts; the full schema stays the
  caller's check. Known losses: Spring AI's Gemini mapping drops `additionalProperties` and
  `uniqueItems`; OpenAI strict mode rejects `uniqueItems` (HTTP 400). Gemini gets a nullable enum
  (`assertiveness`) as `anyOf` [string enum, `{"type": "null"}`]: with `{"enum": [..., null]}` it
  never answered `null` (D12). See
  `context/archive/2026-09-27-assessment-contract-harness/implementation-notes.md` (N1, N2, N5).

## Adding a provider

1. A `Provider` constant in `llm/Provider` (+ `application-<provider>.properties`
   for Embabel's registry), and its value (and any new `Channel`) in the `CHECK` sets of
   `assessments.provider` / `assessments.channel` through a new Flyway migration. Constants whose
   values are stored (`Provider`, `Channel`, `ServiceTier`, `ReasoningEffort`, `OutputMode`) are
   append-only: never rename or remove one — old `assessments` rows would no longer load
   (`persistence/EnumValueConverter` rejects unknown values).
2. A package `llm/<provider>/` with `<Provider>Client implements LlmClient` and its static `create()`
   building the `ChatModel` with every SDK/framework retry off and the timeout set; translate HTTP
   errors into `TransportException` (status + `Retry-After`); one `case` in `LlmClientFactory`.
3. Its schema subset in `transport/ProviderSchemas`.
4. Record one real success response (`src/test/java/.../measurement/README.md`) into
   `src/test/resources/fixtures/<provider>/` and test it in `llm/<provider>/<Provider>ClientTest` on
   `ScriptedProviderServer`: what is sent,
   text and usage read, and request counts for 429 → success, 5xx × 4, 400 and time-out (the count
   proves no hidden retries). The success payload is always a real recording, never hand-written.
   Cases that cannot be provoked live (refusal, blocked answer, thought parts, no choices) are that
   recording changed minimally in the test, with a comment saying what was changed. HTTP error
   bodies are short synthetic JSON in the provider's error shape.
