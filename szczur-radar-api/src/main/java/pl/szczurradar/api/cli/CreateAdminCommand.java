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
 * <p>Exit codes: {@link ExitCode#OK} created, {@link ExitCode#REFUSED} (see {@link AdminBootstrap}) or
 * {@link ExitCode#FAILED}, {@link ExitCode#USAGE} for a usage or configuration error (missing/invalid
 * e-mail, no database configuration, no interactive console, an invalid setting such as
 * {@code LLM_TIMEOUT} that fails the context start). Usage errors are detected before any database work.
 */
public final class CreateAdminCommand implements Command {

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
	public ExitCode run(String[] args) {
		Map<Option, String> options = CommandChecks.options(args, Set.of(Option.EMAIL));
		String rawEmail = options == null ? null : options.get(Option.EMAIL);
		if (rawEmail == null) {
			err.println("usage: " + type().cliName() + " " + Option.EMAIL.flag() + " <e-mail>");
			return ExitCode.USAGE;
		}
		String email = CommandChecks.email(rawEmail, err);
		if (email == null) {
			return ExitCode.USAGE;
		}
		if (!CommandChecks.databaseConfigured(environment, err)) {
			return ExitCode.USAGE;
		}
		PasswordPrompt prompt = promptFactory.get();
		if (prompt == null) {
			err.println("No interactive console: run " + type().cliName() + " in a terminal (e.g. railway ssh) to type the password.");
			return ExitCode.USAGE;
		}

		try (ConfigurableApplicationContext context = contextStarter.apply(new String[0])) {
			return context.getBean(AdminBootstrap.class).createAdmin(email, prompt, out, err);
		}
		catch (RuntimeException ex) {
			return CommandChecks.contextFailed(type(), ex, err);
		}
	}

}
