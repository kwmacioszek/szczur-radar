package pl.szczurradar.api.sources.wykop;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * A failed Wykop API call. The message never contains the app key, secret or token.
 */
public class WykopApiException extends RuntimeException {

	private final WykopFailure failure;

	private final int status;

	public WykopApiException(WykopFailure failure, String message) {
		this(failure, 0, message, null);
	}

	public WykopApiException(WykopFailure failure, String message, @Nullable Throwable cause) {
		this(failure, 0, message, cause);
	}

	/** A non-2xx answer. */
	public static WykopApiException httpStatus(int status, String message) {
		return new WykopApiException(WykopFailure.HTTP_STATUS, status, message, null);
	}

	private WykopApiException(WykopFailure failure, int status, String message, @Nullable Throwable cause) {
		super(message, cause);
		this.failure = Objects.requireNonNull(failure, "failure");
		this.status = status;
	}

	public WykopFailure failure() {
		return failure;
	}

	/** The HTTP status for {@link WykopFailure#HTTP_STATUS}, otherwise 0. */
	public int status() {
		return status;
	}

}
