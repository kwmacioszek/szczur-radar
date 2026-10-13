package pl.szczurradar.api.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.jspecify.annotations.Nullable;
import org.springframework.core.env.PropertyResolver;

/**
 * {@code PIPELINE_TRIGGER_TOKEN}: the shared secret of the daily cron, which calls
 * {@code /internal/**} over Railway's private network with {@code Authorization: Bearer <token>}.
 * A secret: never logged or echoed, also not in error messages.
 *
 * <p>Optional. Unset (or blank) disables the routes: every {@code /internal/**} request is then 401
 * (fail closed). Set, it must have at least {@value #MIN_LENGTH} characters (e.g.
 * {@code openssl rand -hex 32}), otherwise the server does not start. Only the server reads it
 * ({@code config/SecurityConfig}); the operator commands ({@code migrate}, {@code create-admin}) do not.
 */
public final class PipelineTriggerToken {

	public static final String NAME = "PIPELINE_TRIGGER_TOKEN";

	public static final int MIN_LENGTH = 32;

	/** SHA-256 of the configured token, or {@code null} when the routes are disabled. */
	private final byte @Nullable [] digest;

	private PipelineTriggerToken(byte @Nullable [] digest) {
		this.digest = digest;
	}

	/**
	 * Reads and validates the setting.
	 *
	 * @throws IllegalStateException when it is set but too short; the message never contains the value
	 */
	public static PipelineTriggerToken from(PropertyResolver environment) {
		String value = environment.getProperty(NAME);
		String token = value == null ? "" : value.strip();
		if (token.isEmpty()) {
			return new PipelineTriggerToken(null);
		}
		if (token.length() < MIN_LENGTH) {
			throw new IllegalStateException("Invalid " + NAME + ": must be at least " + MIN_LENGTH
					+ " characters (e.g. openssl rand -hex 32), or unset to disable /internal/**.");
		}
		return new PipelineTriggerToken(sha256(token));
	}

	/** Whether a token is configured; without one nothing matches. */
	public boolean enabled() {
		return digest != null;
	}

	/**
	 * Whether {@code presented} is the configured token. Compares the SHA-256 of both in constant
	 * time, so neither the content nor the length of the token leaks through timing.
	 */
	public boolean matches(@Nullable String presented) {
		if (digest == null || presented == null) {
			return false;
		}
		return MessageDigest.isEqual(digest, sha256(presented.strip()));
	}

	/** Never prints the token. */
	@Override
	public String toString() {
		return "PipelineTriggerToken[" + (enabled() ? "<hidden>" : "<disabled>") + "]";
	}

	private static byte[] sha256(String text) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is part of every Java platform", ex);
		}
	}

}
