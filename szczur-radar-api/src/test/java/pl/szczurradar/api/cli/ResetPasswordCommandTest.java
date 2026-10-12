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
 * {@code reset-password} without a database: usage and configuration errors exit 2 before the Spring
 * context starts; the output and exit codes of a call are checked against a mocked context.
 */
class ResetPasswordCommandTest {

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

	private final ResetPasswordCommand command = new ResetPasswordCommand(environment::get, args -> {
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
		assertUsageError(new String[0], "usage: reset-password --email <e-mail>");
		assertUsageError(new String[] { "--email" }, "usage: reset-password");
	}

	@Test
	void unknownOptionsAreUsageErrors() {
		// --role belongs to create-user: a reset keeps the account's role.
		assertUsageError(new String[] { "--email", "jan@example.com", "--role", "admin" }, "usage: reset-password");
		assertUsageError(new String[] { "--email", "jan@example.com", "now" }, "usage: reset-password");
	}

	@Test
	void invalidEmailIsAUsageError() {
		assertUsageError(new String[] { "--email", "not-an-email" }, "Invalid e-mail address: 'not-an-email'");
	}

	@Test
	void missingDatabaseConfigurationIsAUsageError() {
		environment.remove("PGPASSWORD");
		environment.put("PGUSER", " ");
		assertUsageError(new String[] { "--email", "jan@example.com" },
				"Database is not configured: set PGUSER, PGPASSWORD.");
	}

	@Test
	void missingOrInvalidWebPublicUrlIsAUsageError() {
		environment.remove(ActivationSettings.WEB_PUBLIC_URL);
		assertUsageError(new String[] { "--email", "jan@example.com" }, "WEB_PUBLIC_URL is not set");

		environment.put(ActivationSettings.WEB_PUBLIC_URL, "ftp://web.example.test");
		assertUsageError(new String[] { "--email", "jan@example.com" },
				"WEB_PUBLIC_URL must be an absolute http(s) URL");
	}

	@Test
	void invalidLinkTtlIsAUsageError() {
		environment.put(ActivationSettings.LINK_TTL, "90");
		assertUsageError(new String[] { "--email", "jan@example.com" }, "ACTIVATION_LINK_TTL needs a unit");

		environment.put(ActivationSettings.LINK_TTL, "8d");
		assertUsageError(new String[] { "--email", "jan@example.com" }, "ACTIVATION_LINK_TTL must be from 1m to 7d");
	}

	// --- one call in the context ------------------------------------------------------------------

	@Test
	void printsADescriptionAndThenTheLinkOnItsOwnLine() {
		ActivationLinks links = linksInContext();
		environment.put(ActivationSettings.LINK_TTL, "90m");
		when(links.resetPassword("admin@example.com", Duration.ofMinutes(90)))
			.thenReturn(new IssuedLink("admin@example.com", Role.ADMIN, TOKEN, EXPIRES_AT));

		int code = command.run(new String[] { "--email", "Admin@Example.com" });

		assertThat(code).isZero();
		assertThat(stdout().lines()).containsExactly(
				"Reset account admin@example.com (role admin): password removed, signed out everywhere, earlier links"
						+ " invalid. One-time activation link, valid until 2026-10-13T08:15:30Z:",
				WEB + "/activate#" + TOKEN);
		assertThat(stderr()).isEmpty();
		verify(context).close();
	}

	@Test
	void anUnknownEmailIsRefusedWithAHintToCreate() {
		ActivationLinks links = linksInContext();
		when(links.resetPassword("jan@example.com", ActivationSettings.DEFAULT_LINK_TTL))
			.thenReturn(new IssueOutcome.Refused(IssueOutcome.Reason.NO_ACCOUNT));

		int code = command.run(new String[] { "--email=jan@example.com" });

		assertThat(code).isOne();
		assertThat(stderr()).contains("No account with e-mail jan@example.com")
			.contains("create-user --email jan@example.com");
		assertThat(stdout()).isEmpty();
	}

	@Test
	void aFailedContextStartIsReported() {
		int code = command.run(new String[] { "--email", "jan@example.com" });

		assertThat(code).isOne();
		assertThat(contextStarts).hasValue(1);
		assertThat(stderr()).contains("reset-password failed: no database in a unit test");
		assertThat(stdout()).isEmpty();
	}

	private ActivationLinks linksInContext() {
		ActivationLinks links = mock(ActivationLinks.class);
		context = mock(ConfigurableApplicationContext.class);
		when(context.getBean(ActivationLinks.class)).thenReturn(links);
		return links;
	}

	private void assertUsageError(String[] args, String message) {
		assertThat(command.run(args)).isEqualTo(2);
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
