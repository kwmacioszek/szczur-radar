package pl.szczurradar.api.cli;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Dispatcher for operator commands. Returns an {@link ExitCode} instead of calling {@link System#exit}
 * so it can be tested without Spring or Docker; {@code main} exits with its code.
 */
public final class CommandLine {

	/** A usage line: the name, padded to the longest ({@code reset-password}), and the description. */
	private static final String USAGE_LINE = "  %-14s  %s%n";

	private final Map<CommandType, Command> commands = new EnumMap<>(CommandType.class);

	private final PrintStream err;

	public CommandLine(List<Command> commands, PrintStream err) {
		for (Command command : commands) {
			if (this.commands.putIfAbsent(command.type(), command) != null) {
				throw new IllegalArgumentException("duplicate command: " + command.type().cliName());
			}
		}
		this.err = err;
	}

	/** The commands shipped with the jar. */
	public static CommandLine standard() {
		return new CommandLine(List.of(new MigrateCommand(), new CreateAdminCommand(), new CreateUserCommand(),
				new ResetPasswordCommand()), System.err);
	}

	/**
	 * Runs the command named by {@code args[0]} with the remaining arguments.
	 *
	 * @return the command's exit code, or {@link ExitCode#USAGE} for a missing or unknown command
	 */
	public ExitCode run(String[] args) {
		if (args.length == 0) {
			printUsage("no command given");
			return ExitCode.USAGE;
		}
		Command command = CommandType.fromCliName(args[0]).map(commands::get).orElse(null);
		if (command == null) {
			printUsage("unknown command: " + args[0]);
			return ExitCode.USAGE;
		}
		return command.run(Arrays.copyOfRange(args, 1, args.length));
	}

	private void printUsage(String problem) {
		err.println(problem);
		err.println("usage: java -jar szczur-radar-api.jar [<command> [args]]");
		err.printf(USAGE_LINE, "(no command)", "start the HTTP server");
		for (Command command : commands.values()) {
			err.printf(USAGE_LINE, command.type().cliName(), command.type().description());
		}
	}

}
