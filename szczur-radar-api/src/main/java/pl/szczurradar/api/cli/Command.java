package pl.szczurradar.api.cli;

/** An operator command run from the jar ({@code java -jar szczur-radar-api.jar <name> [args]}). */
public interface Command {

	/** Name used on the command line, e.g. {@code migrate}. */
	String name();

	/** One-line description shown in the usage message. */
	String description();

	/**
	 * Runs the command.
	 *
	 * @param args arguments after the command name
	 * @return process exit code: 0 on success, non-zero on failure
	 */
	int run(String[] args);

}
