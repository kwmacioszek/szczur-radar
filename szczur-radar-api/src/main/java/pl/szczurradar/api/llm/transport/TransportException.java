package pl.szczurradar.api.llm.transport;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * One failed HTTP attempt, classified for {@link TransportRetry}. Provider clients translate their
 * SDK's errors into this — only those they can classify: an exception they cannot is not a transport
 * failure and leaves the client outside this type ({@link #unclassified}).
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

		/**
		 * Any other HTTP 4xx status, a model refusal, a blocked answer or an answer without text. Never
		 * retried.
		 */
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
	 * An exception without an HTTP status as a transport failure, if its cause chain says it is one: a
	 * time-out ({@link Kind#TIMEOUT}) or an I/O error ({@link Kind#CONNECTION}), with the chain without
	 * the key ({@link ProviderMessages#cleanCause}) as the cause. Empty otherwise — the exception is not
	 * classified ({@link #unclassified}).
	 */
	public static Optional<TransportException> ofTimeoutOrConnection(Throwable error, ProviderMessages messages) {
		for (Throwable cause = error; cause != null; cause = cause.getCause()) {
			if (cause instanceof InterruptedIOException || cause instanceof HttpTimeoutException) {
				String message = messages.clean("time-out: " + cause);
				return Optional.of(new TransportException(Kind.TIMEOUT, message, null, messages.cleanCause(error)));
			}
		}
		for (Throwable cause = error; cause != null; cause = cause.getCause()) {
			if (cause instanceof IOException) {
				String message = messages.clean("I/O error: " + cause);
				return Optional.of(new TransportException(Kind.CONNECTION, message, null, messages.cleanCause(error)));
			}
		}
		return Optional.empty();
	}

	/**
	 * An exception a provider client cannot classify — a bug in our code or an unknown SDK error — as
	 * what leaves the client instead of a transport failure: never retried and outside
	 * {@code LlmCallException}, so it is not recorded as a provider error. The message is cleaned (it may
	 * be recorded), and the cause is the original's chain without the key ({@link ProviderMessages#cleanCause}).
	 */
	public static IllegalStateException unclassified(Throwable error, ProviderMessages messages) {
		return new IllegalStateException(messages.clean(String.valueOf(error)), messages.cleanCause(error));
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
