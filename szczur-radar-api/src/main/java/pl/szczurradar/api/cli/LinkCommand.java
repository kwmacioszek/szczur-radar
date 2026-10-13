package pl.szczurradar.api.cli;

import java.io.PrintStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;
import org.springframework.context.ConfigurableApplicationContext;

import pl.szczurradar.api.auth.activation.ActivationLinks;
import pl.szczurradar.api.auth.activation.ActivationSettings;
import pl.szczurradar.api.auth.activation.IssueOutcome;
import pl.szczurradar.api.auth.activation.IssuedLink;

/**
 * The operator commands that issue a one-time activation link (S-07): {@link CreateUserCommand} and
 * {@link ResetPasswordCommand}. Both run the same steps, so the link is handled in one place:
 *
 * <ol>
 *   <li>Before the Spring context starts: the options, the e-mail and the database settings
 *       ({@link CommandChecks}), then the link settings ({@link ActivationSettings}: {@code WEB_PUBLIC_URL},
 *       {@code ACTIVATION_LINK_TTL}). An error exits {@link ExitCode#USAGE} and names the option or
 *       setting.
 *   <li>One call of {@link ActivationLinks} in the command's own Spring context.
 *   <li>After the context is closed, on stdout: one line about the account and the link's validity, then
 *       the link alone on the last line.
 * </ol>
 *
 * <p>The link is a secret: it is printed only there, never to stderr or a log, and the line before it
 * does not contain it. Neither command asks for a password, so neither needs an interactive console.
 *
 * <p>Exit codes: {@link ExitCode#OK} link issued, {@link ExitCode#REFUSED} (an {@link IssueOutcome.Refused},
 * nothing written), {@link ExitCode#FAILED} (e.g. the database is unreachable), {@link ExitCode#USAGE}
 * for a usage or configuration error.
 */
abstract sealed class LinkCommand implements Command permits CreateUserCommand, ResetPasswordCommand {

	private final String usage;

	private final Set<Option> options;

	private final Function<String, @Nullable String> environment;

	private final Function<String[], ConfigurableApplicationContext> contextStarter;

	private final PrintStream out;

	private final PrintStream err;

	/**
	 * @param usage the arguments shown after the command name in the usage message
	 * @param options the options the command accepts, {@link Option#EMAIL} included
	 */
	LinkCommand(String usage, Set<Option> options, Function<String, @Nullable String> environment,
			Function<String[], ConfigurableApplicationContext> contextStarter, PrintStream out, PrintStream err) {
		this.usage = usage;
		this.options = options;
		this.environment = environment;
		this.contextStarter = contextStarter;
		this.out = out;
		this.err = err;
	}

	/** The one call of {@link ActivationLinks} a command makes, for a link valid for {@code ttl}. */
	@FunctionalInterface
	interface LinkRequest {

		IssueOutcome issue(ActivationLinks links, Duration ttl);

	}

	/**
	 * Reads the command's own options into its call; runs before any database work.
	 *
	 * @param email the normalized {@code --email}
	 * @param given every option given, {@code --email} included
	 * @return the call, or {@code null} after printing a usage error on {@code err}
	 */
	abstract @Nullable LinkRequest request(String email, Map<Option, String> given, PrintStream err);

	/** The start of the line before the link: what happened to which account. Never the token. */
	abstract String describe(IssuedLink link);

	@Override
	public final ExitCode run(String[] args) {
		Map<Option, String> given = CommandChecks.options(args, options);
		String rawEmail = given == null ? null : given.get(Option.EMAIL);
		if (given == null || rawEmail == null) {
			err.println("usage: " + type().cliName() + " " + usage);
			return ExitCode.USAGE;
		}
		String email = CommandChecks.email(rawEmail, err);
		if (email == null) {
			return ExitCode.USAGE;
		}
		LinkRequest request = request(email, given, err);
		if (request == null || !CommandChecks.databaseConfigured(environment, err)) {
			return ExitCode.USAGE;
		}
		ActivationSettings settings = settings();
		if (settings == null) {
			return ExitCode.USAGE;
		}

		IssueOutcome outcome;
		try (ConfigurableApplicationContext context = contextStarter.apply(new String[0])) {
			outcome = request.issue(context.getBean(ActivationLinks.class), settings.linkTtl());
		}
		catch (RuntimeException ex) {
			err.println(type().cliName() + " failed: " + ex.getMessage());
			return ExitCode.FAILED;
		}
		return switch (outcome) {
			case IssuedLink link -> {
				out.println(describe(link) + " One-time activation link, valid until " + link.expiresAt() + ":");
				out.println(settings.link(link.token()));
				yield ExitCode.OK;
			}
			case IssueOutcome.Refused(IssueOutcome.Reason reason) -> {
				err.println(refusal(reason, email));
				yield ExitCode.REFUSED;
			}
		};
	}

	/** The link settings, or {@code null} after printing every problem (each names its setting). */
	private @Nullable ActivationSettings settings() {
		return switch (ActivationSettings.from(environment)) {
			case ActivationSettings.Result.Valid(ActivationSettings settings) -> settings;
			case ActivationSettings.Result.Invalid(List<String> problems) -> {
				problems.forEach(problem -> err.println("Activation links are not configured: " + problem + "."));
				yield null;
			}
		};
	}

	/** Why nothing was issued, and the command the operator probably wanted. */
	private static String refusal(IssueOutcome.Reason reason, String email) {
		return switch (reason) {
			case EMAIL_TAKEN -> "An account with e-mail " + email + " already exists; nothing was changed."
					+ " To issue it a new link: " + CommandType.RESET_PASSWORD.cliName() + " "
					+ Option.EMAIL.flag() + " " + email;
			case NO_ACCOUNT -> "No account with e-mail " + email + "; nothing was changed. To create one: "
					+ CommandType.CREATE_USER.cliName() + " " + Option.EMAIL.flag() + " " + email;
			case SYSTEM_ACCOUNT -> "The account " + email + " is a system account: it never logs in, so it gets"
					+ " no activation link; nothing was changed.";
		};
	}

}
