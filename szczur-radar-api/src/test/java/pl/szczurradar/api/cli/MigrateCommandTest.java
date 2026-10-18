package pl.szczurradar.api.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.ConfigurableApplicationContext;

import pl.szczurradar.api.settings.InvalidSettingsException;

/** Exit-code mapping of {@code migrate}; the migration itself is covered by MigrationsIT. */
class MigrateCommandTest {

	private final ByteArrayOutputStream errBytes = new ByteArrayOutputStream();

	private final PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

	@Test
	void successExitsZeroAndClosesContext() {
		ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);

		ExitCode code = new MigrateCommand(args -> context, err).run(new String[0]);

		assertThat(code).isEqualTo(ExitCode.OK);
		verify(context).close();
	}

	@Test
	void failedStartupExitsNonZero() {
		MigrateCommand command = new MigrateCommand(args -> {
			throw new IllegalStateException("Migration V2 failed");
		}, err);

		assertThat(command.run(new String[0])).isEqualTo(ExitCode.FAILED);
		assertThat(errBytes.toString(StandardCharsets.UTF_8)).contains("migrate failed").contains("Migration V2 failed");
	}

	@Test
	void anInvalidSettingIsAConfigurationError() {
		MigrateCommand command = new MigrateCommand(args -> {
			throw new InvalidSettingsException("Invalid LLM configuration: LLM_TIMEOUT needs a unit");
		}, err);

		assertThat(command.run(new String[0])).isEqualTo(ExitCode.USAGE);
		assertThat(errBytes.toString(StandardCharsets.UTF_8))
			.isEqualTo("migrate: invalid configuration: Invalid LLM configuration: LLM_TIMEOUT needs a unit"
					+ System.lineSeparator());
	}

	@Test
	void anInvalidSettingThrownFromABeanIsAConfigurationError() {
		MigrateCommand command = new MigrateCommand(args -> {
			throw new BeanCreationException("runWatchdog", "Failed to instantiate",
					new InvalidSettingsException("Invalid configuration: PIPELINE_STALL_TIMEOUT needs a unit"));
		}, err);

		assertThat(command.run(new String[0])).isEqualTo(ExitCode.USAGE);
		assertThat(errBytes.toString(StandardCharsets.UTF_8))
			.contains("migrate: invalid configuration: Invalid configuration: PIPELINE_STALL_TIMEOUT needs a unit")
			.doesNotContain("failed");
	}

}
