package pl.szczurradar.api.llm.transport;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

import org.jspecify.annotations.Nullable;

/**
 * One failed HTTP attempt, classified for {@link TransportRetry}. Provider clients translate their
 * SDK's errors into this.
 */
public class TransportException extends RuntimeException {

	/** What went wrong in the attempt. */
	public enum Kind {

		/** HTTP 429 (also flex refusals). Retried. */
		RATE_LIMITED(true),

		/** HTTP 5xx. Retried. */
		SERVER_ERROR(true),

		/** The attempt ran out of time (or HTTP 408). Retried. */
		TIMEOUT(true),

		/** No connection or a broken one. Retried. */
		CONNECTION(true),

		/**
		 * HTTP 401 (key), 402 (credit) or 404 (model), and OpenRouter's 403 for a spent budget
		 * ({@code OpenRouterClient}): the configuration is refused, every call would fail the same way.
		 * Never retried; ends as {@code LlmConfigurationException}.
		 */
		CONFIGURATION(false),

		/** Any other error, including a model refusal or a blocked answer. Never retried. */
		REJECTED(false);

		private final boolean retryable;

		Kind(boolean retryable) {
			this.retryable = retryable;
		}

		public boolean retryable() {
			return retryable;
		}

	}

	private final Kind kind;

	private final @Nullable Duration retryAfter;

	public TransportException(Kind kind, String message, @Nullable Duration retryAfter, @Nullable Throwable cause) {
		super(message, cause);
		this.kind = kind;
		this.retryAfter = retryAfter;
	}

	public Kind kind() {
		return kind;
	}

	/** The server's {@code Retry-After}, if it sent one. */
	public @Nullable Duration retryAfter() {
		return retryAfter;
	}

	/** The kind for an HTTP error status. */
	public static Kind kindOf(int status) {
		if (status == 429) {
			return Kind.RATE_LIMITED;
		}
		if (status == 408) {
			return Kind.TIMEOUT;
		}
		if (status == 401 || status == 402 || status == 404) {
			return Kind.CONFIGURATION;
		}
		return status >= 500 ? Kind.SERVER_ERROR : Kind.REJECTED;
	}

	/** An HTTP error status as a transport failure. */
	public static TransportException ofStatus(int status, String message, @Nullable Duration retryAfter,
			@Nullable Throwable cause) {
		return ofStatus(status, kindOf(status), message, retryAfter, cause);
	}

	/** An HTTP error status as a transport failure of a kind the provider client decided. */
	public static TransportException ofStatus(int status, Kind kind, String message, @Nullable Duration retryAfter,
			@Nullable Throwable cause) {
		return new TransportException(kind, "HTTP " + status + ": " + message, retryAfter, cause);
	}

	/**
	 * An exception without an HTTP status: a time-out or I/O error found in its cause chain, otherwise
	 * {@link Kind#REJECTED}.
	 */
	public static TransportException ofThrowable(Throwable error, ProviderMessages messages) {
		for (Throwable cause = error; cause != null; cause = cause.getCause()) {
			if (cause instanceof InterruptedIOException || cause instanceof HttpTimeoutException) {
				return new TransportException(Kind.TIMEOUT, messages.clean("time-out: " + cause), null, error);
			}
		}
		for (Throwable cause = error; cause != null; cause = cause.getCause()) {
			if (cause instanceof IOException) {
				return new TransportException(Kind.CONNECTION, messages.clean("I/O error: " + cause), null, error);
			}
		}
		return new TransportException(Kind.REJECTED, messages.clean(String.valueOf(error)), null, error);
	}

	/** {@code Retry-After} in seconds (the HTTP-date form is not used by the providers), or {@code null}. */
	public static @Nullable Duration parseRetryAfter(@Nullable String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			long seconds = Long.parseLong(value.strip());
			return seconds >= 0 ? Duration.ofSeconds(seconds) : null;
		}
		catch (NumberFormatException ex) {
			return null;
		}
	}

}
