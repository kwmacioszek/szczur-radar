package pl.szczurradar.api.cli;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import pl.szczurradar.api.user.Emails;

/**
 * What the operator commands check before their Spring context starts, so that a usage or
 * configuration error never reaches the database: the options, the e-mail and the database settings.
 * A failed check is a usage error ({@link CommandLine#USAGE_ERROR}); the checks that print say why on
 * {@code err}.
 */
final class CommandChecks {

	/** {@code --email}: the account a command works on. */
	static final String EMAIL = "email";

	/** The database settings the JDBC URL is built from (application.properties). */
	static final List<String> DATABASE_VARIABLES = List.of("PGHOST", "PGPORT", "PGDATABASE", "PGUSER", "PGPASSWORD");

	private CommandChecks() {
	}

	/**
	 * Reads options given as {@code --name value} or {@code --name=value}, each at most once.
	 *
	 * @param names the options the command accepts, without {@code --}
	 * @return the value of each option given, or {@code null} for a usage error: an unknown or repeated
	 *     option, an option without a value, or an argument that is not an option
	 */
	static @Nullable Map<String, String> options(String[] args, Set<String> names) {
		Map<String, String> values = new HashMap<>();
		for (int i = 0; i < args.length; i++) {
			if (!args[i].startsWith("--")) {
				return null;
			}
			String option = args[i].substring(2);
			int equals = option.indexOf('=');
			String name;
			String value;
			if (equals >= 0) {
				name = option.substring(0, equals);
				value = option.substring(equals + 1);
			}
			else if (i + 1 < args.length) {
				name = option;
				value = args[++i];
			}
			else {
				return null;
			}
			if (!names.contains(name) || values.putIfAbsent(name, value) != null) {
				return null;
			}
		}
		return values;
	}

	/**
	 * The address normalized as login normalizes it ({@link Emails}), or {@code null} after printing
	 * that login would not accept it (no {@code @}, or longer than {@link Emails#MAX_EMAIL_LENGTH}).
	 */
	static @Nullable String email(String raw, PrintStream err) {
		String email = Emails.normalize(raw);
		if (!email.contains("@") || email.length() > Emails.MAX_EMAIL_LENGTH) {
			err.println("Invalid e-mail address: '" + raw + "'");
			return null;
		}
		return email;
	}

	/** Whether every {@link #DATABASE_VARIABLES database setting} is set; otherwise prints the missing ones. */
	static boolean databaseConfigured(Function<String, @Nullable String> environment, PrintStream err) {
		List<String> missing = DATABASE_VARIABLES.stream().filter(name -> isBlank(environment.apply(name))).toList();
		if (!missing.isEmpty()) {
			err.println("Database is not configured: set " + String.join(", ", missing) + ".");
			return false;
		}
		return true;
	}

	private static boolean isBlank(@Nullable String value) {
		return value == null || value.isBlank();
	}

}
