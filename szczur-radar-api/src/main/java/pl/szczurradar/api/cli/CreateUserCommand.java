package pl.szczurradar.api.cli;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;
import org.springframework.context.ConfigurableApplicationContext;

import pl.szczurradar.api.SzczurRadarApiApplication;
import pl.szczurradar.api.auth.activation.IssuedLink;
import pl.szczurradar.api.user.Role;

/**
 * {@code create-user --email <e-mail> [--role user|admin]}: creates an account without a password,
 * role {@code user} unless given, and prints its one-time activation link (S-07, FR-027). The person
 * sets a password through the link, then logs in. In production it runs through {@code railway ssh},
 * like {@code create-admin}; the steps and exit codes are in {@link LinkCommand}.
 *
 * <p>An e-mail that already has an account is refused (exit 1): {@code reset-password} issues a new
 * link to an existing account.
 */
public final class CreateUserCommand extends LinkCommand {

	/** {@code --role}: one of {@link Role}'s values, {@code user} by default. */
	static final String ROLE = "role";

	private static final List<String> ROLE_VALUES = Arrays.stream(Role.values()).map(Role::value).toList();

	public CreateUserCommand() {
		this(System::getenv, args -> SzczurRadarApiApplication.commandApplication().run(args), System.out, System.err);
	}

	CreateUserCommand(Function<String, @Nullable String> environment,
			Function<String[], ConfigurableApplicationContext> contextStarter, PrintStream out, PrintStream err) {
		super("--email <e-mail> [--" + ROLE + " " + String.join("|", ROLE_VALUES) + "]",
				Set.of(CommandChecks.EMAIL, ROLE), environment, contextStarter, out, err);
	}

	@Override
	public CommandType type() {
		return CommandType.CREATE_USER;
	}

	@Override
	@Nullable LinkRequest request(String email, Map<String, String> given, PrintStream err) {
		Role role = role(given.getOrDefault(ROLE, Role.USER.value()), err);
		if (role == null) {
			return null;
		}
		return (links, ttl) -> links.createUser(email, role, ttl);
	}

	@Override
	String describe(IssuedLink link) {
		return "Created account " + link.email() + " (role " + link.role().value() + ") without a password.";
	}

	private static @Nullable Role role(String value, PrintStream err) {
		try {
			return Role.fromValue(value);
		}
		catch (IllegalArgumentException ex) {
			err.println("Unknown role: '" + value + "' (use " + String.join(" or ", ROLE_VALUES) + ").");
			return null;
		}
	}

}
