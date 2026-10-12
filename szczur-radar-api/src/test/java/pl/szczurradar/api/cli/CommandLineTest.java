package pl.szczurradar.api.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class CommandLineTest {

	private final ByteArrayOutputStream errBytes = new ByteArrayOutputStream();

	private final PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

	private final RecordingCommand migrate = new RecordingCommand(CommandType.MIGRATE, ExitCode.OK);

	private final CommandLine commandLine = new CommandLine(List.of(migrate), err);

	@Test
	void unknownCommandExitsWithUsageError() {
		ExitCode code = commandLine.run(new String[] { "frobnicate" });

		assertThat(code).isEqualTo(ExitCode.USAGE);
		assertThat(stderr()).contains("unknown command: frobnicate").contains("usage:").contains(CommandType.MIGRATE.cliName());
		assertThat(migrate.calls).isEmpty();
	}

	@Test
	void missingCommandExitsWithUsageError() {
		assertThat(commandLine.run(new String[0])).isEqualTo(ExitCode.USAGE);
		assertThat(stderr()).contains("usage:");
	}

	@Test
	void dispatchesToNamedCommandWithRemainingArgs() {
		RecordingCommand failing = new RecordingCommand(CommandType.CREATE_ADMIN, ExitCode.REFUSED);
		CommandLine cli = new CommandLine(List.of(migrate, failing), err);

		assertThat(cli.run(new String[] { CommandType.MIGRATE.cliName(), "--x=1" })).isEqualTo(ExitCode.OK);
		assertThat(cli.run(new String[] { CommandType.CREATE_ADMIN.cliName() })).isEqualTo(ExitCode.REFUSED);

		assertThat(migrate.calls).containsExactly(List.of("--x=1"));
		assertThat(failing.calls).containsExactly(List.of());
		assertThat(stderr()).isEmpty();
	}

	@Test
	void knownButUnregisteredCommandIsUnknown() {
		assertThat(commandLine.run(new String[] { CommandType.CREATE_ADMIN.cliName() })).isEqualTo(ExitCode.USAGE);
		assertThat(stderr()).contains("unknown command: " + CommandType.CREATE_ADMIN.cliName());
	}

	@Test
	void duplicateRegistrationIsRejected() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new CommandLine(List.of(migrate, new RecordingCommand(CommandType.MIGRATE, ExitCode.OK)), err));
	}

	@Test
	void standardRegistersEveryCommandType() {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		// Only the usage text is checked; no command is run.
		PrintStream original = System.err;
		try {
			System.setErr(new PrintStream(bytes, true, StandardCharsets.UTF_8));
			assertThat(CommandLine.standard().run(new String[] { "nope" })).isEqualTo(ExitCode.USAGE);
		}
		finally {
			System.setErr(original);
		}
		for (CommandType type : CommandType.values()) {
			assertThat(bytes.toString(StandardCharsets.UTF_8)).contains(type.cliName()).contains(type.description());
		}
	}

	private String stderr() {
		return errBytes.toString(StandardCharsets.UTF_8);
	}

	private static final class RecordingCommand implements Command {

		private final CommandType type;

		private final ExitCode exitCode;

		final List<List<String>> calls = new ArrayList<>();

		RecordingCommand(CommandType type, ExitCode exitCode) {
			this.type = type;
			this.exitCode = exitCode;
		}

		@Override
		public CommandType type() {
			return type;
		}

		@Override
		public ExitCode run(String[] args) {
			calls.add(List.of(args));
			return exitCode;
		}

	}

}
