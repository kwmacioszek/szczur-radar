package pl.szczurradar.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** {@code COOKIE_SECURE}: the cookie is Secure unless the value is 0/false/no (any case). */
class SessionConfigTest {

	@ParameterizedTest
	@ValueSource(strings = { "0", "false", "no", "FALSE", " No " })
	void insecureOnlyForExplicitOptOut(String value) {
		assertThat(SessionConfig.secureCookie(value)).isFalse();
	}

	@ParameterizedTest
	@ValueSource(strings = { "true", "1", "yes", "off", "", "anything" })
	void secureOtherwise(String value) {
		assertThat(SessionConfig.secureCookie(value)).isTrue();
	}

}
