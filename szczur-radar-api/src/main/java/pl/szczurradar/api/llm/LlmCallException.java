package pl.szczurradar.api.llm;

/**
 * A model call that produced no text, after {@link #attempts()} HTTP attempts. As such (not a subclass):
 * the provider refused this request or failed it with a non-retryable error. Subclasses:
 * {@link LlmConfigurationException} (the configuration is refused) and {@link LlmUnavailableException}
 * (the provider stayed unavailable after the retries).
 */
public class LlmCallException extends RuntimeException {

	private final CallFailure failure;

	private final int attempts;

	private final ServiceTier serviceTier;

	private final boolean flexFallback;

	public LlmCallException(CallFailure failure, String message, int attempts, ServiceTier serviceTier,
			boolean flexFallback, Throwable cause) {
		super(message, cause);
		this.failure = failure;
		this.attempts = attempts;
		this.serviceTier = serviceTier;
		this.flexFallback = flexFallback;
	}

	public CallFailure failure() {
		return failure;
	}

	public int attempts() {
		return attempts;
	}

	/** The tier of the last attempt. */
	public ServiceTier serviceTier() {
		return serviceTier;
	}

	public boolean flexFallback() {
		return flexFallback;
	}

}
