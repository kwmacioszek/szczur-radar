package pl.szczurradar.api.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** {@code PIPELINE_TRIGGER_TOKEN}: unset disables the routes, too short fails the start, never printed. */
class PipelineTriggerTokenTest {

	/** Exactly the minimum length. */
	private static final String TOKEN = "0123456789abcdef0123456789abcdef";

	@Test
	void unsetOrBlankDisablesTheRoutesAndMatchesNothing() {
		for (MockEnvironment environment : List.of(new MockEnvironment(), environment(""), environment("  \n"))) {
			PipelineTriggerToken token = PipelineTriggerToken.from(environment);

			assertThat(token.enabled()).isFalse();
			assertThat(token.matches(null)).isFalse();
			assertThat(token.matches("")).isFalse();
			assertThat(token.matches(TOKEN)).isFalse();
		}
	}

	@Test
	void aTokenShorterThan32CharactersFailsTheStartWithoutRevealingIt() {
		String tooShort = TOKEN.substring(1);

		assertThatIllegalStateException().isThrownBy(() -> PipelineTriggerToken.from(environment(tooShort)))
			.withMessageStartingWith("Invalid PIPELINE_TRIGGER_TOKEN: ")
			.withMessageContaining("at least 32 characters")
			.withMessageNotContaining(tooShort);
	}

	@Test
	void aConfiguredTokenMatchesOnlyItself() {
		PipelineTriggerToken token = PipelineTriggerToken.from(environment(TOKEN));

		assertThat(token.enabled()).isTrue();
		assertThat(token.matches(TOKEN)).isTrue();
		assertThat(token.matches(null)).isFalse();
		assertThat(token.matches("")).isFalse();
		assertThat(token.matches(TOKEN + "0")).isFalse();
		assertThat(token.matches(TOKEN.substring(1))).isFalse();
		assertThat(token.matches(TOKEN.toUpperCase(Locale.ROOT))).isFalse();
	}

	@Test
	void surroundingWhiteSpaceIsNotPartOfTheToken() {
		// e.g. a value piped with a trailing newline into `railway variable set --stdin`
		PipelineTriggerToken token = PipelineTriggerToken.from(environment(" " + TOKEN + "\n"));

		assertThat(token.matches(TOKEN)).isTrue();
		assertThat(token.matches(TOKEN + " ")).isTrue();
	}

	@Test
	void neverPrintsTheToken() {
		assertThat(PipelineTriggerToken.from(environment(TOKEN)).toString()).doesNotContain(TOKEN).contains("<hidden>");
		assertThat(PipelineTriggerToken.from(new MockEnvironment()).toString()).contains("<disabled>");
	}

	private static MockEnvironment environment(String token) {
		return new MockEnvironment().withProperty(PipelineTriggerToken.NAME, token);
	}

}
