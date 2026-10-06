package pl.szczurradar.api.llm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Provider error texts and the holder of the key never show the key. */
class ProviderMessagesTest {

	private static final String KEY = "sk-or-v1-test-key-0123456789";

	@Test
	void cleanHidesTheKeyAndCapsTheText() {
		ProviderMessages messages = new ProviderMessages(KEY);

		assertThat(messages.clean("401: invalid key " + KEY)).isEqualTo("401: invalid key <hidden>");
		assertThat(messages.clean("x".repeat(600))).hasSize(ProviderMessages.MAX_LENGTH + 1).endsWith("…");
	}

	@Test
	void toStringNeverShowsTheKey() {
		assertThat(new ProviderMessages(KEY).toString()).doesNotContain(KEY).contains("<hidden>");
	}

}
