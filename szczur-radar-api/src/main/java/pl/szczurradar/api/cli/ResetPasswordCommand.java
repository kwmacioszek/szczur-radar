package pl.szczurradar.api.cli;

import java.io.PrintStream;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;
import org.springframework.context.ConfigurableApplicationContext;

import pl.szczurradar.api.SzczurRadarApiApplication;
import pl.szczurradar.api.auth.activation.ActivationLinks;
import pl.szczurradar.api.auth.activation.IssuedLink;

/**
 * {@code reset-password --email <e-mail>}: puts any existing account, an admin's too, back to
 * "waiting for activation" and prints a new one-time activation link (S-07, FR-028). In one
 * transaction ({@link ActivationLinks#resetPassword}) it removes the password, deletes the account's
 * sessions, so it is signed out everywhere, and replaces its earlier link. This is also how an admin
 * who lost the password gets back in. The steps and exit codes are in {@link LinkCommand}.
 *
 * <p>An e-mail without an account is refused (exit 1): {@code create-user} creates one.
 */
public final class ResetPasswordCommand extends LinkCommand {

	public ResetPasswordCommand() {
		this(System::getenv, args -> SzczurRadarApiApplication.commandApplication().run(args), System.out, System.err);
	}

	ResetPasswordCommand(Function<String, @Nullable String> environment,
			Function<String[], ConfigurableApplicationContext> contextStarter, PrintStream out, PrintStream err) {
		super("--email <e-mail>", Set.of(CommandChecks.EMAIL), environment, contextStarter, out, err);
	}

	@Override
	public CommandType type() {
		return CommandType.RESET_PASSWORD;
	}

	@Override
	LinkRequest request(String email, Map<String, String> given, PrintStream err) {
		return (links, ttl) -> links.resetPassword(email, ttl);
	}

	@Override
	String describe(IssuedLink link) {
		return "Reset account " + link.email() + " (role " + link.role().value()
				+ "): password removed, signed out everywhere, earlier links invalid.";
	}

}
