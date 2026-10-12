package pl.szczurradar.api.cli;

import java.io.PrintStream;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import org.springframework.context.ConfigurableApplicationContext;

import pl.szczurradar.api.SzczurRadarApiApplication;

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
		Map<String, String> options = CommandChecks.options(args, Set.of(CommandChecks.EMAIL));
		String rawEmail = options == null ? null : options.get(CommandChecks.EMAIL);
		if (rawEmail == null) {
			err.println("usage: " + type().cliName() + " --email <e-mail>");
			return USAGE_ERROR;
		}
		String email = CommandChecks.email(rawEmail, err);
		if (email == null) {
			return USAGE_ERROR;
		}
		if (!CommandChecks.databaseConfigured(environment, err)) {
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

}
