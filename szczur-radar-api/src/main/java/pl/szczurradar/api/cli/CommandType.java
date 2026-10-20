package pl.szczurradar.api.cli;

import java.util.Arrays;
import java.util.Optional;

/** The operator commands of the jar: the name typed on the command line and its usage description. */
public enum CommandType {

	MIGRATE("migrate", "apply pending database migrations and exit"),

	CREATE_ADMIN("create-admin", "create the first administrator (--email <e-mail>; password asked twice, hidden)"),

	CREATE_USER("create-user",
			"create an account without a password and print its one-time activation link"
					+ " (--email <e-mail> [--role user|admin])"),

	RESET_PASSWORD("reset-password",
			"remove an account's password, end its sessions and print a new one-time activation link"
					+ " (--email <e-mail>)"),

	SEND_TEST_ALERT("send-test-alert",
			"send a test event to the healthchecks.io check in HEALTHCHECK_PING_URL and exit (no database)");

	private final String cliName;

	private final String description;

	CommandType(String cliName, String description) {
		this.cliName = cliName;
		this.description = description;
	}

	/** Name used on the command line ({@code java -jar szczur-radar-api.jar <cliName>}). */
	public String cliName() {
		return cliName;
	}

	/** One-line description shown in the usage message. */
	public String description() {
		return description;
	}

	/** The command typed as {@code cliName}, if any. */
	public static Optional<CommandType> fromCliName(String cliName) {
		return Arrays.stream(values()).filter(type -> type.cliName.equals(cliName)).findFirst();
	}

}
