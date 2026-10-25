package pl.szczurradar.api.llm.transport;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

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
	void cleanCauseCopiesTheChainWithoutTheKeyButWithItsTrace() {
		ProviderMessages messages = new ProviderMessages(KEY);
		IOException root = new IOException();
		RuntimeException top = new RuntimeException("401: invalid key " + KEY, root);
		top.addSuppressed(new IllegalStateException("suppressed " + KEY));

		Throwable cleaned = messages.cleanCause(top);

		assertThat(cleaned).isInstanceOf(CleanedProviderException.class)
			.hasMessage("java.lang.RuntimeException: 401: invalid key <hidden>");
		assertThat(cleaned.getStackTrace()).isEqualTo(top.getStackTrace());
		assertThat(cleaned.getSuppressed()).isEmpty();
		// A null message leaves only the class name.
		assertThat(cleaned.getCause()).isInstanceOf(CleanedProviderException.class).hasMessage("java.io.IOException")
			.hasNoCause();
		assertThat(cleaned.getCause().getStackTrace()).isEqualTo(root.getStackTrace());
		assertThat(messages.cleanCause(null)).isNull();
	}

	@Test
	void cleanCauseEndsWhereTheChainComesBackOrGetsTooDeep() {
		ProviderMessages messages = new ProviderMessages(KEY);
		RuntimeException first = new RuntimeException("first " + KEY);
		RuntimeException second = new RuntimeException("second", first);
		first.initCause(second);

		Throwable cleaned = messages.cleanCause(first);

		assertThat(cleaned).hasMessage("java.lang.RuntimeException: first <hidden>");
		assertThat(cleaned.getCause()).hasMessage("java.lang.RuntimeException: second").hasNoCause();

		Throwable deep = new RuntimeException("0");
		for (int i = 1; i < 100; i++) {
			deep = new RuntimeException(String.valueOf(i), deep);
		}
		int depth = 0;
		for (Throwable cause = messages.cleanCause(deep); cause != null; cause = cause.getCause()) {
			depth++;
		}
		assertThat(depth).isEqualTo(ProviderMessages.MAX_CAUSE_DEPTH);
	}

	@Test
	void toStringNeverShowsTheKey() {
		assertThat(new ProviderMessages(KEY).toString()).doesNotContain(KEY).contains("<hidden>");
	}

}
