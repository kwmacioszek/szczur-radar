package pl.szczurradar.api.sources.reddit;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * A failed Reddit (Arctic Shift) call or fetch. Arctic Shift has no key, so a message has no secret to
 * hide; it is capped at 500 characters.
 */
public class RedditApiException extends RuntimeException {

	private final RedditFailure failure;

	private final int status;

	public RedditApiException(RedditFailure failure, String message) {
		this(failure, 0, message, null);
	}

	public RedditApiException(RedditFailure failure, String message, @Nullable Throwable cause) {
		this(failure, 0, message, cause);
	}

	/** A non-2xx answer. */
	public static RedditApiException httpStatus(int status, String message) {
		return new RedditApiException(RedditFailure.HTTP_STATUS, status, message, null);
	}

	private RedditApiException(RedditFailure failure, int status, String message, @Nullable Throwable cause) {
		super(message, cause);
		this.failure = Objects.requireNonNull(failure, "failure");
		this.status = status;
	}

	public RedditFailure failure() {
		return failure;
	}

	/** The HTTP status for {@link RedditFailure#HTTP_STATUS}, otherwise 0. */
	public int status() {
		return status;
	}

}
