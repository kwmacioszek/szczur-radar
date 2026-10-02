package pl.szczurradar.api.config;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonValue;
import org.springframework.http.HttpStatus;

/**
 * Every application error the API answers with, and its HTTP status. On the wire it is the
 * lower-case name in {@code {"detail": "<code>"}} — part of the contract with the SPA
 * ({@code szczur-radar-web/src/api/client.ts}), so renaming a constant changes the API.
 */
public enum ErrorCode {

	/** Unknown e-mail, wrong password or an account without a password (indistinguishable). */
	INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),

	/** No valid session, or the session's account no longer exists. */
	NOT_AUTHENTICATED(HttpStatus.UNAUTHORIZED),

	/** Logged in but lacking the role. */
	FORBIDDEN(HttpStatus.FORBIDDEN),

	/** Login limiter refused the attempt (sent with {@code Retry-After}). */
	TOO_MANY_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS),

	/** State-changing request from a foreign {@code Origin}. */
	CROSS_ORIGIN(HttpStatus.FORBIDDEN),

	/** Malformed JSON, unknown fields, wrong types or failed validation. */
	INVALID_REQUEST(HttpStatus.BAD_REQUEST),

	/** A body in a format other than JSON. */
	UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE);

	private final HttpStatus status;

	ErrorCode(HttpStatus status) {
		this.status = status;
	}

	public HttpStatus status() {
		return status;
	}

	/** The value of {@code detail}, e.g. {@code invalid_credentials}. */
	@JsonValue
	public String code() {
		return name().toLowerCase(Locale.ROOT);
	}

}
