# llm — provider clients

Infrastructure only: no assessment contract here (the contract lives in `agents/assessor`; this
package must not import it). `LlmClient.complete(LlmRequest)` returns the model's raw text and the
call's metadata, or throws `LlmCallException` (`provider_error` | `timeout`).

- One provider per run from `LLM_*` (`config/LlmProviderProperties`); `LlmClientFactory` builds a
  Spring AI `ChatModel` outside Embabel's model beans. Embabel's `createObject` is never used for
  assessments (lenient parsing, semantic retries, schema pasted into the prompt).
- **Retries live only in `TransportRetry`**: up to 3 attempts per tier on 429, 5xx, time-out and
  connection errors; `Retry-After` (≤ 60 s) or `min(2^(n-1), 30)` s; flex → default after three
  429s. Everything below is off and must stay off: Spring AI `RetryTemplate` (`NO_RETRY`),
  google-genai `HttpRetryOptions.attempts(1)`, openai-java `maxRetries(0)`, DeepSeek `RestClient`
  without retry. Parse errors and contract violations are never retried.
- Every HTTP attempt has the `LLM_TIMEOUT` limit (the Gemini SDK has none by default).
- `ProviderSchemas` sends each provider the schema subset it accepts; the full schema stays the
  caller's check. Known losses: Spring AI's Gemini mapping drops `additionalProperties` and
  `uniqueItems`; OpenAI strict mode rejects `uniqueItems` (HTTP 400). Open check in phase 4:
  `context/changes/assessment-contract-harness/implementation-notes.md` (N1, N2).

## Adding a provider

1. A `Provider` constant in `config/LlmProviderProperties` (+ `application-<provider>.properties`
   for Embabel's registry).
2. A client class building its `ChatModel` in `LlmClientFactory` with every SDK/framework retry off
   and the timeout set; translate HTTP errors into `TransportException` (status + `Retry-After`).
3. Its schema subset in `ProviderSchemas`.
4. Record one real success response (`src/test/java/.../measurement/README.md`) into
   `src/test/resources/fixtures/<provider>/` and test it on `ScriptedProviderServer`: what is sent,
   text and usage read, and request counts for 429 → success, 5xx × 4, 400 and time-out (the count
   proves no hidden retries). Test payloads are always real recordings, never hand-written.
