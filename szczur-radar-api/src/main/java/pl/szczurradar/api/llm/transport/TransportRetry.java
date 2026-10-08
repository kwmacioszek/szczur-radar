package pl.szczurradar.api.llm.transport;

import java.time.Duration;

import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;

import pl.szczurradar.api.config.LlmProviderProperties.ServiceTier;
import pl.szczurradar.api.llm.CallFailure;
import pl.szczurradar.api.llm.LlmCallException;

/**
 * The only retry loop of a model call. Up to {@value #ATTEMPTS_PER_TIER} attempts per tier on
 * {@link TransportException.Kind#retryable() retryable} failures, waiting {@code Retry-After} (capped
 * at 60 s) or {@code min(2^(n-1), 30)} s between them; anything else fails at once. With flex, three
 * consecutive 429 refusals switch the same call to the default tier for up to three more attempts.
 * Contract violations and parse errors never reach this loop — they are not retried.
 */
public final class TransportRetry {

	/** Spring AI's own retry layer, off: every chat model is built with it (only this class retries). */
	public static final RetryTemplate SPRING_AI_OFF = new RetryTemplate(RetryPolicy.withMaxRetries(0));

	public static final int ATTEMPTS_PER_TIER = 3;

	static final Duration MAX_RETRY_AFTER = Duration.ofSeconds(60);

	static final Duration MAX_BACKOFF = Duration.ofSeconds(30);

	/** Waits between attempts; replaced in tests. */
	@FunctionalInterface
	public interface Sleeper {

		void sleep(Duration duration) throws InterruptedException;

		Sleeper REAL = Thread::sleep;

	}

	/** One HTTP attempt in the given tier. */
	@FunctionalInterface
	public interface Attempt<T> {

		T call(ServiceTier tier);

	}

	/** The value of the successful attempt and how it was reached. */
	public record Outcome<T>(T value, int attempts, ServiceTier serviceTier, boolean flexFallback) {
	}

	private final Sleeper sleeper;

	public TransportRetry(Sleeper sleeper) {
		this.sleeper = sleeper;
	}

	/**
	 * Runs {@code attempt} until it succeeds or the attempts are used up.
	 *
	 * @throws LlmCallException with {@link CallFailure#TIMEOUT} if the last attempt timed out,
	 *     otherwise {@link CallFailure#PROVIDER_ERROR}
	 */
	public <T> Outcome<T> run(ServiceTier requested, Attempt<T> attempt) {
		ServiceTier tier = requested;
		boolean flexFallback = false;
		int attempts = 0;
		while (true) {
			int rateLimited = 0;
			TransportException last = null;
			for (int n = 1; n <= ATTEMPTS_PER_TIER; n++) {
				attempts++;
				try {
					return new Outcome<>(attempt.call(tier), attempts, tier, flexFallback);
				}
				catch (TransportException ex) {
					last = ex;
					if (!ex.kind().retryable()) {
						throw new LlmCallException(CallFailure.PROVIDER_ERROR, ex.getMessage(), attempts, tier,
								flexFallback, ex);
					}
					if (ex.kind() == TransportException.Kind.RATE_LIMITED) {
						rateLimited++;
					}
					if (n < ATTEMPTS_PER_TIER) {
						sleep(delay(ex, n), attempts, tier, flexFallback);
					}
				}
			}
			if (tier == ServiceTier.FLEX && rateLimited == ATTEMPTS_PER_TIER) {
				tier = ServiceTier.DEFAULT;
				flexFallback = true;
				continue;
			}
			CallFailure failure = last.kind() == TransportException.Kind.TIMEOUT ? CallFailure.TIMEOUT
					: CallFailure.PROVIDER_ERROR;
			throw new LlmCallException(failure, "failed after " + attempts + " attempts: " + last.getMessage(),
					attempts, tier, flexFallback, last);
		}
	}

	/** The wait before attempt {@code n + 1}. */
	static Duration delay(TransportException failure, int n) {
		Duration retryAfter = failure.retryAfter();
		if (retryAfter != null) {
			return retryAfter.compareTo(MAX_RETRY_AFTER) > 0 ? MAX_RETRY_AFTER : retryAfter;
		}
		Duration backoff = Duration.ofSeconds(1L << (n - 1));
		return backoff.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : backoff;
	}

	private void sleep(Duration duration, int attempts, ServiceTier tier, boolean flexFallback) {
		try {
			sleeper.sleep(duration);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new LlmCallException(CallFailure.PROVIDER_ERROR, "interrupted between attempts", attempts, tier,
					flexFallback, ex);
		}
	}

}
