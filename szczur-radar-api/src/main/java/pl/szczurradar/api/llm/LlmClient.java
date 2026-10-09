package pl.szczurradar.api.llm;

import pl.szczurradar.api.llm.transport.TransportRetry;

/**
 * One logical model call: a system prompt, a user message and the JSON Schema of the expected output
 * go in, the model's raw text and the call's metadata come out. The text is never parsed, repaired
 * or validated here — that is the caller's job (for assessments: {@code agents/assessor}).
 *
 * <p>Implementations retry only transport failures (429, 5xx, time-outs, connection errors) with
 * {@link TransportRetry}; no SDK or framework below them retries.
 */
public interface LlmClient {

	/**
	 * Cap on the output tokens of one call, reasoning included: a guard against a runaway answer
	 * (e.g. a repetition loop), not a size target. The measured maximum is ~4 500 (Luna, reasoning
	 * {@code medium}); a cut-off answer fails parsing as {@code invalid_structured_output} and is not
	 * retried.
	 */
	int MAX_OUTPUT_TOKENS = 16_000;

	/**
	 * The request of {@link #check()}: a tiny answer through the same path as a real call (same
	 * channel, mode and options), so it costs a few dozen tokens.
	 */
	LlmRequest CHECK_REQUEST = new LlmRequest("You check that the API works. Answer only with JSON.",
			"Return the JSON object {\"ok\": true}.",
			"{\"type\":\"object\",\"properties\":{\"ok\":{\"type\":\"boolean\"}},\"required\":[\"ok\"],"
					+ "\"additionalProperties\":false}");

	/**
	 * Calls the model.
	 *
	 * @throws LlmCallException when the provider refused the call or the last attempt failed
	 *     — every provider or transport failure ends here. A bug in our own code during an attempt
	 *     surfaces as the original {@code RuntimeException} on purpose: it is not a provider error
	 *     and must not be recorded as one. An {@link LlmConfigurationException} (a subclass) says the
	 *     provider refused the key, the credit or the model, not this request.
	 */
	LlmResponse complete(LlmRequest request);

	/**
	 * Checks the configured key, model and credit with one real, tiny call ({@link #CHECK_REQUEST}):
	 * listing the provider's models would not do (DeepSeek accepts model aliases it does not list).
	 *
	 * @return the answer's metadata (the model that answered, tokens)
	 * @throws LlmCallException as {@link #complete}
	 */
	default LlmResponse check() {
		return complete(CHECK_REQUEST);
	}

}
