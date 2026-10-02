package pl.szczurradar.api.auth;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Password hashing (argon2id) and the password policy: the single source of truth for login, the
 * CLI and S-07 (activation, recovery). Plain passwords are never logged or returned.
 */
public final class Passwords {

	public static final int MIN_LENGTH = 12;

	public static final int MAX_LENGTH = 128;

	private Passwords() {
	}

	/** Argon2id with Spring Security's current defaults (16 MB, 2 iterations). */
	public static PasswordEncoder encoder() {
		return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
	}

	/**
	 * Checks the policy; length counts characters (code points), not UTF-16 units.
	 *
	 * @throws IllegalArgumentException with a user-facing message when {@code plain} is too short
	 *     or too long
	 */
	public static void validatePolicy(String plain) {
		int length = plain.codePointCount(0, plain.length());
		if (length < MIN_LENGTH) {
			throw new IllegalArgumentException("Password must be at least " + MIN_LENGTH + " characters long.");
		}
		if (length > MAX_LENGTH) {
			throw new IllegalArgumentException("Password must be at most " + MAX_LENGTH + " characters long.");
		}
	}

}
