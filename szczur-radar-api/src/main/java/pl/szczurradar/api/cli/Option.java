package pl.szczurradar.api.cli;

import java.util.Arrays;
import java.util.Optional;

import pl.szczurradar.api.user.Role;

/** The options operator commands accept, given as {@code --name value} or {@code --name=value}. */
enum Option {

	/** {@code --email}: the account a command works on. */
	EMAIL("email"),

	/** {@code --role}: one of {@link Role}'s values ({@code create-user}; {@code user} by default). */
	ROLE("role");

	private final String cliName;

	Option(String cliName) {
		this.cliName = cliName;
	}

	/** The option as typed, with its {@code --}. */
	String flag() {
		return "--" + cliName;
	}

	/** The option typed as {@code --cliName}, if any. */
	static Optional<Option> fromCliName(String cliName) {
		return Arrays.stream(values()).filter(option -> option.cliName.equals(cliName)).findFirst();
	}

}
