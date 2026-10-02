package pl.szczurradar.api.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class CommandLineTest {

	private final ByteArrayOutputStream errBytes = new ByteArrayOutputStream();

	private final PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

	private final RecordingCommand migrate = new RecordingCommand("migrate", 0);

	private final CommandLine commandLine = new CommandLine(List.of(migrate), err);

	@Test
	void unknownCommandExitsWithUsageError() {
		int code = commandLine.run(new String[] { "frobnicate" });

		assertThat(code).isEqualTo(CommandLine.USAGE_ERROR).isEqualTo(2);
		assertThat(stderr()).contains("unknown command: frobnicate").contains("usage:").contains("migrate");
		assertThat(migrate.calls).isEmpty();
	}

	@Test
	void missingCommandExitsWithUsageError() {
		assertThat(commandLine.run(new String[0])).isEqualTo(2);
		assertThat(stderr()).contains("usage:");
	}

	@Test
	void dispatchesToNamedCommandWithRemainingArgs() {
		RecordingCommand failing = new RecordingCommand("other", 7);
		CommandLine cli = new CommandLine(List.of(migrate, failing), err);

		assertThat(cli.run(new String[] { "migrate", "--x=1" })).isZero();
		assertThat(cli.run(new String[] { "other" })).isEqualTo(7);

		assertThat(migrate.calls).containsExactly(List.of("--x=1"));
		assertThat(failing.calls).containsExactly(List.of());
		assertThat(stderr()).isEmpty();
	}

	@Test
	void standardRegistersMigrate() {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		// Only the usage text is checked; no command is run.
		PrintStream original = System.err;
		try {
			System.setErr(new PrintStream(bytes, true, StandardCharsets.UTF_8));
			assertThat(CommandLine.standard().run(new String[] { "nope" })).isEqualTo(2);
		}
		finally {
			System.setErr(original);
		}
		assertThat(bytes.toString(StandardCharsets.UTF_8)).contains("migrate");
	}

	private String stderr() {
		return errBytes.toString(StandardCharsets.UTF_8);
	}

	private static final class RecordingCommand implements Command {

		private final String name;

		private final int exitCode;

		final List<List<String>> calls = new ArrayList<>();

		RecordingCommand(String name, int exitCode) {
			this.name = name;
			this.exitCode = exitCode;
		}

		@Override
		public String name() {
			return name;
		}

		@Override
		public String description() {
			return "test command";
		}

		@Override
		public int run(String[] args) {
			calls.add(List.of(args));
			return exitCode;
		}

	}

}
