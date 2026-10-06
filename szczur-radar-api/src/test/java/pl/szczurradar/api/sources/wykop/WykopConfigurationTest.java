package pl.szczurradar.api.sources.wykop;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** {@link WykopConfiguration}: the keys are optional, but never only one of them. */
class WykopConfigurationTest {

	private final ApplicationContextRunner context = new ApplicationContextRunner()
		.withUserConfiguration(WykopConfiguration.class)
		// Empty values override any real WYKOP_* variables of the machine.
		.withPropertyValues(WykopConfiguration.KEY + "=", WykopConfiguration.SECRET + "=");

	@Test
	void withoutKeysThereIsNoRepositoryAndTheContextStarts() {
		context.run(started -> {
			assertThat(started).hasNotFailed();
			assertThat(started).doesNotHaveBean(WykopRepository.class);
		});
	}

	@Test
	void withBothKeysThereIsARepository() {
		context.withPropertyValues(WykopConfiguration.KEY + "=synthetic-key", WykopConfiguration.SECRET + "=synthetic-secret")
			.run(started -> assertThat(started).hasSingleBean(WykopRepository.class));
	}

	@Test
	void onlyOneKeyFailsTheStartWithoutRevealingIt() {
		context.withPropertyValues(WykopConfiguration.KEY + "=very-secret-key").run(started -> {
			assertThat(started).hasFailed();
			assertThat(started.getStartupFailure()).rootCause()
				.hasMessageContaining("WYKOP_KEY and WYKOP_SECRET must be set together (only WYKOP_KEY is set)")
				.hasMessageNotContaining("very-secret-key");
		});
		context.withPropertyValues(WykopConfiguration.SECRET + "= very-secret ")
			.run(started -> assertThat(started.getStartupFailure()).rootCause()
				.hasMessageContaining("(only WYKOP_SECRET is set)"));
	}

}
