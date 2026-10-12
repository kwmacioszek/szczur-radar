package pl.szczurradar.api.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;

import pl.szczurradar.api.auth.activation.ActivationLinks;
import pl.szczurradar.api.auth.activation.ActivationSettings;
import pl.szczurradar.api.auth.activation.IssueOutcome;
import pl.szczurradar.api.auth.activation.IssuedLink;
import pl.szczurradar.api.user.Role;

/**
 * {@code create-user} without a database: usage and configuration errors exit 2 before the Spring
 * context starts; the output and exit codes of a call are checked against a mocked context.
 */
class CreateUserCommandTest {

	private static final String WEB = "https://web.example.test";

	private static final String TOKEN = "t0ken-".repeat(7) + "x"; // 43 characters, like a real token

	private static final Instant EXPIRES_AT = Instant.parse("2026-10-13T08:15:30Z");

	private final Map<String, String> environment = new HashMap<>(Map.of("PGHOST", "db", "PGPORT", "5432",
			"PGDATABASE", "railway", "PGUSER", "postgres", "PGPASSWORD", "secret", ActivationSettings.WEB_PUBLIC_URL,
			WEB));

	private final AtomicInteger contextStarts = new AtomicInteger();

	/** What the command's context starter returns; {@code null}: the start fails. */
	private ConfigurableApplicationContext context;

	private final ByteArrayOutputStream outBytes = new ByteArrayOutputStream();

	private final ByteArrayOutputStream errBytes = new ByteArrayOutputStream();

	private final CreateUserCommand command = new CreateUserCommand(environment::get, args -> {
		contextStarts.incrementAndGet();
		if (context == null) {
			throw new IllegalStateException("no database in a unit test");
		}
		return context;
	}, new PrintStream(outBytes, true, StandardCharsets.UTF_8),
			new PrintStream(errBytes, true, StandardCharsets.UTF_8));

	// --- usage and configuration errors: exit 2, no context ---------------------------------------

	@Test
	void missingEmailIsAUsageError() {
		assertUsageError(new String[0], "usage: create-user --email <e-mail> [--role user|admin]");
		assertUsageError(new String[] { "--email" }, "usage: create-user");
		assertUsageError(new String[] { "--role", "admin" }, "usage: create-user");
	}

	@Test
	void unknownRepeatedOrStrayArgumentsAreUsageErrors() {
		assertUsageError(new String[] { "--email", "jan@example.com", "--name", "Jan" }, "usage: create-user");
		assertUsageError(new String[] { "--email", "jan@example.com", "--email", "ola@example.com" },
				"usage: create-user");
		assertUsageError(new String[] { "--email", "jan@example.com", "admin" }, "usage: create-user");
	}

	@Test
	void invalidEmailIsAUsageError() {
		assertUsageError(new String[] { "--email", "not-an-email" }, "Invalid e-mail address: 'not-an-email'");
		assertUsageError(new String[] { "--email=" + "a".repeat(309) + "@example.com" }, "Invalid e-mail address");
	}

	@Test
	void unknownRoleIsAUsageError() {
		assertUsageError(new String[] { "--email", "jan@example.com", "--role", "owner" },
				"Unknown role: 'owner' (use user or admin).");
		assertUsageError(new String[] { "--email", "jan@example.com", "--role=ADMIN" }, "Unknown role: 'ADMIN'");
	}

	@Test
	void missingDatabaseConfigurationIsAUsageError() {
		environment.remove("PGHOST");
		assertUsageError(new String[] { "--email", "jan@example.com" }, "Database is not configured: set PGHOST.");
	}

	@Test
	void missingOrInvalidWebPublicUrlIsAUsageError() {
		environment.remove(ActivationSettings.WEB_PUBLIC_URL);
		assertUsageError(new String[] { "--email", "jan@example.com" }, "WEB_PUBLIC_URL is not set");

		environment.put(ActivationSettings.WEB_PUBLIC_URL, "https://");
		assertUsageError(new String[] { "--email", "jan@example.com" },
				"WEB_PUBLIC_URL must be an absolute http(s) URL");

		environment.put(ActivationSettings.WEB_PUBLIC_URL, WEB + "/app");
		assertUsageError(new String[] { "--email", "jan@example.com" }, "WEB_PUBLIC_URL must be only the scheme");
	}

	@Test
	void linkTtlWithoutAUnitIsAUsageError() {
		environment.put(ActivationSettings.LINK_TTL, "12");
		assertUsageError(new String[] { "--email", "jan@example.com" }, "ACTIVATION_LINK_TTL needs a unit");
	}

	// --- one call in the context ------------------------------------------------------------------

	@Test
	void printsADescriptionAndThenTheLinkOnItsOwnLine() {
		ActivationLinks links = linksInContext();
		environment.put(ActivationSettings.LINK_TTL, "2h");
		when(links.createUser("jan@example.com", Role.ADMIN, Duration.ofHours(2)))
			.thenReturn(new IssuedLink("jan@example.com", Role.ADMIN, TOKEN, EXPIRES_AT));

		ExitCode code = command.run(new String[] { "--email= Jan@Example.com", "--role=admin" });

		assertThat(code).isEqualTo(ExitCode.OK);
		assertThat(stdout().lines()).containsExactly(
				"Created account jan@example.com (role admin) without a password. One-time activation link,"
						+ " valid until 2026-10-13T08:15:30Z:",
				WEB + "/activate#" + TOKEN);
		assertThat(stderr()).isEmpty();
		verify(context).close();
	}

	@Test
	void theRoleIsUserAndTheLinkValidFor12HoursByDefault() {
		ActivationLinks links = linksInContext();
		when(links.createUser("jan@example.com", Role.USER, ActivationSettings.DEFAULT_LINK_TTL))
			.thenReturn(new IssuedLink("jan@example.com", Role.USER, TOKEN, EXPIRES_AT));

		assertThat(command.run(new String[] { "--email", "jan@example.com" })).isEqualTo(ExitCode.OK);
		assertThat(stdout()).contains("(role user)");
	}

	@Test
	void aTakenEmailIsRefusedWithAHintToReset() {
		ActivationLinks links = linksInContext();
		when(links.createUser("jan@example.com", Role.USER, ActivationSettings.DEFAULT_LINK_TTL))
			.thenReturn(new IssueOutcome.Refused(IssueOutcome.Reason.EMAIL_TAKEN));

		ExitCode code = command.run(new String[] { "--email", "jan@example.com" });

		assertThat(code).isEqualTo(ExitCode.REFUSED);
		assertThat(stderr()).contains("An account with e-mail jan@example.com already exists")
			.contains("reset-password --email jan@example.com");
		assertThat(stdout()).isEmpty();
		verify(context).close();
	}

	@Test
	void aFailedContextStartIsReported() {
		ExitCode code = command.run(new String[] { "--email", "jan@example.com" });

		assertThat(code).isEqualTo(ExitCode.FAILED);
		assertThat(contextStarts).hasValue(1);
		assertThat(stderr()).contains("create-user failed: no database in a unit test");
		assertThat(stdout()).isEmpty();
	}

	private ActivationLinks linksInContext() {
		ActivationLinks links = mock(ActivationLinks.class);
		context = mock(ConfigurableApplicationContext.class);
		when(context.getBean(ActivationLinks.class)).thenReturn(links);
		return links;
	}

	private void assertUsageError(String[] args, String message) {
		assertThat(command.run(args)).isEqualTo(ExitCode.USAGE);
		assertThat(stderr()).contains(message);
		assertThat(contextStarts).as("no database work on usage errors").hasValue(0);
		assertThat(stdout()).isEmpty();
	}

	private String stdout() {
		return outBytes.toString(StandardCharsets.UTF_8);
	}

	private String stderr() {
		return errBytes.toString(StandardCharsets.UTF_8);
	}

}
