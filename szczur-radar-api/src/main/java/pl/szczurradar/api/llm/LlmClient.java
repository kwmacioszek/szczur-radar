package pl.szczurradar.api.llm;

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
	 * Calls the model.
	 *
	 * @throws LlmCallException when the provider refused the call or the last attempt failed
	 *     — every provider or transport failure ends here. A bug in our own code during an attempt
	 *     surfaces as the original {@code RuntimeException} on purpose: it is not a provider error
	 *     and must not be recorded as one.
	 */
	LlmResponse complete(LlmRequest request);

}
