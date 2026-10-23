package pl.szczurradar.api.llm;

/**
 * The provider was unavailable, not refusing this request: every attempt failed on a retryable error
 * (429, 5xx, time-out, connection) until the retries ran out, or the call was interrupted between
 * attempts ({@code transport/TransportRetry}). The request itself may well be fine, so callers that
 * count attempts per request can leave it uncounted. Still an {@link LlmCallException} — with
 * {@link CallFailure#TIMEOUT} when the last attempt timed out, otherwise
 * {@link CallFailure#PROVIDER_ERROR} — for callers that only report failed calls.
 */
public class LlmUnavailableException extends LlmCallException {

	public LlmUnavailableException(CallFailure failure, String message, int attempts, ServiceTier serviceTier,
			boolean flexFallback, Throwable cause) {
		super(failure, message, attempts, serviceTier, flexFallback, cause);
	}

}
