package pl.szczurradar.api.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;

/** Exit-code mapping of {@code migrate}; the migration itself is covered by MigrationsIT. */
class MigrateCommandTest {

	private final ByteArrayOutputStream errBytes = new ByteArrayOutputStream();

	private final PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

	@Test
	void successExitsZeroAndClosesContext() {
		ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);

		int code = new MigrateCommand(args -> context, err).run(new String[0]);

		assertThat(code).isZero();
		verify(context).close();
	}

	@Test
	void failedStartupExitsNonZero() {
		MigrateCommand command = new MigrateCommand(args -> {
			throw new IllegalStateException("Migration V2 failed");
		}, err);

		assertThat(command.run(new String[0])).isNotZero();
		assertThat(errBytes.toString(StandardCharsets.UTF_8)).contains("migrate failed").contains("Migration V2 failed");
	}

}
