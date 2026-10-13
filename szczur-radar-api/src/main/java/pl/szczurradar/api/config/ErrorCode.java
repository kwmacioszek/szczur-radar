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

	/**
	 * Unknown e-mail, wrong password, an account without a password or one that cannot log in (a
	 * system account) — indistinguishable.
	 */
	INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),

	/**
	 * No valid session, or the session's account no longer exists; on {@code /internal/**} no valid
	 * trigger token.
	 */
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
	UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),

	/** An activation link that is unknown, used, expired or replaced by a newer one (indistinguishable). */
	ACTIVATION_LINK_INVALID(HttpStatus.BAD_REQUEST),

	/** A new password outside the policy: 12–128 characters (code points, {@code auth/Passwords}). */
	PASSWORD_POLICY(HttpStatus.BAD_REQUEST),

	/** A pipeline run is already queued or running; the request created nothing. */
	PIPELINE_BUSY(HttpStatus.CONFLICT),

	/** A posts run named a dead post (3 failed attempts); reviving one is outside S-02. */
	POST_DEAD(HttpStatus.BAD_REQUEST),

	/** A range run needs a source that is not configured here (e.g. no keys), or no source is. */
	SOURCE_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE),

	/** The addressed resource (e.g. a pipeline run by its id) does not exist. */
	NOT_FOUND(HttpStatus.NOT_FOUND);

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
