package pl.szczurradar.api.sources.transport;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * An HTTP exchange of a source that {@link HttpRetry} gave up on. The message has passed the client's
 * {@code clean}; the source's client turns this into its own exception of the same kind.
 */
public class HttpRetryException extends RuntimeException {

	/** Why the exchange ended without an answer the client takes. */
	public enum Kind {

		/** A retried status in every attempt ({@link HttpRetryException#status()}). */
		HTTP_STATUS,

		/** No answer in the last attempt: connection error or time-out. */
		TRANSPORT,

		/** No attempt or wait was started that would end after the caller's deadline. */
		DEADLINE_EXCEEDED,

		/** The thread was interrupted during an attempt or a wait; its interrupt flag is kept. */
		INTERRUPTED

	}

	private final Kind kind;

	private final int status;

	private final int attempts;

	/**
	 * @param status the last answer's status for {@link Kind#HTTP_STATUS}, otherwise 0
	 * @param attempts the attempts started before it ended
	 * @param cause only the {@link InterruptedException} of {@link Kind#INTERRUPTED}
	 */
	HttpRetryException(Kind kind, int status, int attempts, String message, @Nullable Throwable cause) {
		super(message, cause);
		this.kind = Objects.requireNonNull(kind, "kind");
		this.status = status;
		this.attempts = attempts;
	}

	public Kind kind() {
		return kind;
	}

	/** The HTTP status for {@link Kind#HTTP_STATUS}, otherwise 0. */
	public int status() {
		return status;
	}

	/** The attempts started before the exchange ended. */
	public int attempts() {
		return attempts;
	}

}
