package pl.szczurradar.api.cli;

import java.io.PrintStream;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import org.springframework.context.ConfigurableApplicationContext;

import pl.szczurradar.api.SzczurRadarApiApplication;
import pl.szczurradar.api.user.Emails;

/**
 * {@code create-admin --email <e-mail>}: bootstraps the first administrator without the web server
 * (in production through {@code railway ssh}). The password comes from a hidden prompt, never from
 * an argument, so it stays out of shell history and logs.
 *
 * <p>Exit codes: 0 created, 1 refused (see {@link AdminBootstrap}) or failed, 2 usage or
 * configuration error (missing/invalid e-mail, no database configuration, no interactive console).
 * Usage errors are detected before any database work.
 */
public final class CreateAdminCommand implements Command {

	static final int USAGE_ERROR = CommandLine.USAGE_ERROR;

	/** The database settings the JDBC URL is built from (application.properties). */
	static final List<String> DATABASE_VARIABLES = List.of("PGHOST", "PGPORT", "PGDATABASE", "PGUSER", "PGPASSWORD");

	private final Function<String, @Nullable String> environment;

	private final Supplier<@Nullable PasswordPrompt> promptFactory;

	private final Function<String[], ConfigurableApplicationContext> contextStarter;

	private final PrintStream out;

	private final PrintStream err;

	public CreateAdminCommand() {
		this(System::getenv, PasswordPrompt::console,
				args -> SzczurRadarApiApplication.commandApplication().run(args), System.out, System.err);
	}

	CreateAdminCommand(Function<String, @Nullable String> environment, Supplier<@Nullable PasswordPrompt> promptFactory,
			Function<String[], ConfigurableApplicationContext> contextStarter, PrintStream out, PrintStream err) {
		this.environment = environment;
		this.promptFactory = promptFactory;
		this.contextStarter = contextStarter;
		this.out = out;
		this.err = err;
	}

	@Override
	public CommandType type() {
		return CommandType.CREATE_ADMIN;
	}

	@Override
	public int run(String[] args) {
		String rawEmail = emailArgument(args);
		if (rawEmail == null) {
			err.println("usage: " + type().cliName() + " --email <e-mail>");
			return USAGE_ERROR;
		}
		String email = Emails.normalize(rawEmail);
		if (!email.contains("@") || email.length() > Emails.MAX_EMAIL_LENGTH) {
			err.println("Invalid e-mail address: '" + rawEmail + "'");
			return USAGE_ERROR;
		}
		List<String> missing = DATABASE_VARIABLES.stream().filter(name -> isBlank(environment.apply(name))).toList();
		if (!missing.isEmpty()) {
			err.println("Database is not configured: set " + String.join(", ", missing) + ".");
			return USAGE_ERROR;
		}
		PasswordPrompt prompt = promptFactory.get();
		if (prompt == null) {
			err.println("No interactive console: run " + type().cliName() + " in a terminal (e.g. railway ssh) to type the password.");
			return USAGE_ERROR;
		}

		try (ConfigurableApplicationContext context = contextStarter.apply(new String[0])) {
			return context.getBean(AdminBootstrap.class).createAdmin(email, prompt, out, err);
		}
		catch (RuntimeException ex) {
			err.println(type().cliName() + " failed: " + ex.getMessage());
			return AdminBootstrap.REFUSED;
		}
	}

	/** {@code --email <value>} or {@code --email=<value>}; null when absent or anything else is passed. */
	private static @Nullable String emailArgument(String[] args) {
		if (args.length == 2 && args[0].equals("--email")) {
			return args[1];
		}
		if (args.length == 1 && args[0].startsWith("--email=")) {
			return args[0].substring("--email=".length());
		}
		return null;
	}

	private static boolean isBlank(@Nullable String value) {
		return value == null || value.isBlank();
	}

}
