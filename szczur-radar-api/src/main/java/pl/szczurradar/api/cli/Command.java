package pl.szczurradar.api.cli;

/** An operator command run from the jar ({@code java -jar szczur-radar-api.jar <name> [args]}). */
public interface Command {

	/** Which command this is: its command-line name and description live in {@link CommandType}. */
	CommandType type();

	/**
	 * Runs the command.
	 *
	 * @param args arguments after the command name
	 * @return process exit code: 0 on success, non-zero on failure
	 */
	int run(String[] args);

}
