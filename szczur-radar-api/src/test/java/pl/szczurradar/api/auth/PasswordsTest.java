package pl.szczurradar.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Password hashing and policy, ported from the Python tests/test_passwords.py. */
class PasswordsTest {

	private static final String PASSWORD = "correct horse battery";

	private final PasswordEncoder encoder = Passwords.encoder();

	@Test
	void hashIsArgon2idAndVerifies() {
		String hash = encoder.encode(PASSWORD);

		assertThat(hash).isNotEqualTo(PASSWORD).startsWith("$argon2id$");
		assertThat(encoder.matches(PASSWORD, hash)).isTrue();
		assertThat(encoder.matches(PASSWORD + "!", hash)).isFalse();
	}

	@Test
	void malformedHashNeverVerifies() {
		// The encoder logs a WARN with a stack trace for a malformed hash; expected here, so muted.
		Logger logger = (Logger) LoggerFactory.getLogger(Argon2PasswordEncoder.class);
		Level previous = logger.getLevel();
		logger.setLevel(Level.OFF);
		try {
			assertThat(encoder.matches(PASSWORD, "not-a-hash")).isFalse();
		}
		finally {
			logger.setLevel(previous);
		}
	}

	@ParameterizedTest
	@ValueSource(ints = { 12, 128 })
	void policyAcceptsTheBounds(int length) {
		assertThatNoException().isThrownBy(() -> Passwords.validatePolicy("a".repeat(length)));
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, 11, 129 })
	void policyRejectsOutOfBounds(int length) {
		assertThatIllegalArgumentException().isThrownBy(() -> Passwords.validatePolicy("a".repeat(length)));
	}

	@Test
	void policyCountsCharactersNotUtf16Units() {
		// 12 emoji are 24 UTF-16 units but 12 characters.
		assertThatNoException().isThrownBy(() -> Passwords.validatePolicy("😀".repeat(12)));
		assertThatIllegalArgumentException().isThrownBy(() -> Passwords.validatePolicy("😀".repeat(129)));
	}

}
