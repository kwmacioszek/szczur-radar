package pl.szczurradar.api.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/** Usage and configuration errors of {@code create-admin}: exit 2 before any database work. */
class CreateAdminCommandTest {

	private final Map<String, String> environment = new HashMap<>(Map.of("PGHOST", "db", "PGPORT", "5432",
			"PGDATABASE", "railway", "PGUSER", "postgres", "PGPASSWORD", "secret"));

	private PasswordPrompt prompt = text -> "unused";

	private final AtomicInteger contextStarts = new AtomicInteger();

	private final ByteArrayOutputStream errBytes = new ByteArrayOutputStream();

	private final CreateAdminCommand command = new CreateAdminCommand(environment::get, () -> prompt, args -> {
		contextStarts.incrementAndGet();
		throw new IllegalStateException("no database in a unit test");
	}, new PrintStream(new ByteArrayOutputStream()), new PrintStream(errBytes, true, StandardCharsets.UTF_8));

	@Test
	void missingEmailIsAUsageError() {
		assertUsageError(new String[0], "usage: create-admin --email");
		assertUsageError(new String[] { "--email" }, "usage: create-admin --email");
		assertUsageError(new String[] { "--name", "x@example.com" }, "usage: create-admin --email");
	}

	@Test
	void emailWithoutAtIsAUsageError() {
		assertUsageError(new String[] { "--email", "not-an-email" }, "Invalid e-mail address");
	}

	@Test
	void emailLongerThanLoginAcceptsIsAUsageError() {
		String email = "a".repeat(309) + "@example.com"; // 321 characters
		assertUsageError(new String[] { "--email=" + email }, "Invalid e-mail address");
	}

	@Test
	void missingDatabaseConfigurationIsAUsageError() {
		environment.remove("PGHOST");
		environment.put("PGPASSWORD", " ");
		assertUsageError(new String[] { "--email", "admin@example.com" }, "set PGHOST, PGPASSWORD");
	}

	@Test
	void missingConsoleIsAUsageError() {
		prompt = null;
		assertUsageError(new String[] { "--email", "admin@example.com" }, "No interactive console");
	}

	@Test
	void validInputStartsTheContextAndReportsItsFailure() {
		int code = command.run(new String[] { "--email", "admin@example.com" });

		assertThat(code).isEqualTo(AdminBootstrap.REFUSED);
		assertThat(contextStarts).hasValue(1);
		assertThat(stderr()).contains("create-admin failed: no database in a unit test");
	}

	private void assertUsageError(String[] args, String message) {
		assertThat(command.run(args)).isEqualTo(2);
		assertThat(stderr()).contains(message);
		assertThat(contextStarts).as("no database work on usage errors").hasValue(0);
	}

	private String stderr() {
		return errBytes.toString(StandardCharsets.UTF_8);
	}

}
