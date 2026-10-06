# llm — provider clients

Infrastructure only: no assessment contract here (the contract lives in `agents/assessor`; this
package must not import it). `LlmClient.complete(LlmRequest)` returns the model's raw text and the
call's metadata, or throws `LlmCallException` (`provider_error` | `timeout`). White space (e.g. a
blank answer) is judged with the shared `text/Whitespace`, never `isBlank`. An answer without text is
`REJECTED` (`provider_error`, no retry) for every provider, whatever the finish reason
(`ChatResponses.requireText`).

- One provider per run from `LLM_*` (`config/LlmProviderProperties`); `LlmClientFactory` builds a
  Spring AI `ChatModel` outside Embabel's model beans. Embabel's `createObject` is never used for
  assessments (lenient parsing, semantic retries, schema pasted into the prompt).
- **Retries live only in `TransportRetry`**: up to 3 attempts per tier on 429, 5xx, time-out and
  connection errors; `Retry-After` (≤ 60 s) or `min(2^(n-1), 30)` s; flex → default after three
  429s. Everything below is off and must stay off: Spring AI `RetryTemplate` (`NO_RETRY`),
  google-genai `HttpRetryOptions.attempts(1)`, openai-java `maxRetries(0)`, DeepSeek `RestClient`
  without retry. Parse errors and contract violations are never retried.
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
- `ProviderSchemas` sends each provider the schema subset it accepts; the full schema stays the
  caller's check. Known losses: Spring AI's Gemini mapping drops `additionalProperties` and
  `uniqueItems`; OpenAI strict mode rejects `uniqueItems` (HTTP 400). Gemini gets a nullable enum
  (`assertiveness`) as `anyOf` [string enum, `{"type": "null"}`]: with `{"enum": [..., null]}` it
  never answered `null` (D12). See
  `context/archive/2026-09-27-assessment-contract-harness/implementation-notes.md` (N1, N2, N5).

## Adding a provider

1. A `Provider` constant in `config/LlmProviderProperties` (+ `application-<provider>.properties`
   for Embabel's registry), and its value (and any new `Channel`) in the `CHECK` sets of
   `assessments.provider` / `assessments.channel` through a new Flyway migration.
2. A client class building its `ChatModel` in `LlmClientFactory` with every SDK/framework retry off
   and the timeout set; translate HTTP errors into `TransportException` (status + `Retry-After`).
3. Its schema subset in `ProviderSchemas`.
4. Record one real success response (`src/test/java/.../measurement/README.md`) into
   `src/test/resources/fixtures/<provider>/` and test it on `ScriptedProviderServer`: what is sent,
   text and usage read, and request counts for 429 → success, 5xx × 4, 400 and time-out (the count
   proves no hidden retries). The success payload is always a real recording, never hand-written.
   Cases that cannot be provoked live (refusal, blocked answer, thought parts, no choices) are that
   recording changed minimally in the test, with a comment saying what was changed. HTTP error
   bodies are short synthetic JSON in the provider's error shape.
