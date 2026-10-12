package pl.szczurradar.api.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** The checks every operator command runs before its Spring context starts. */
class CommandChecksTest {

	private static final Set<String> NAMES = Set.of("email", "role");

	private final ByteArrayOutputStream errBytes = new ByteArrayOutputStream();

	private final PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

	@Test
	void readsBothOptionForms() {
		assertThat(CommandChecks.options(new String[] { "--email", "a@example.com", "--role=admin" }, NAMES))
			.containsExactlyInAnyOrderEntriesOf(Map.of("email", "a@example.com", "role", "admin"));
		assertThat(CommandChecks.options(new String[] { "--email=" }, NAMES)).containsExactly(Map.entry("email", ""));
		assertThat(CommandChecks.options(new String[0], NAMES)).isEmpty();
	}

	@Test
	void anythingElseIsAUsageError() {
		assertThat(CommandChecks.options(new String[] { "--name", "x" }, NAMES)).as("unknown option").isNull();
		assertThat(CommandChecks.options(new String[] { "--email" }, NAMES)).as("no value").isNull();
		assertThat(CommandChecks.options(new String[] { "--email", "a@example.com", "--email=b@example.com" }, NAMES))
			.as("repeated")
			.isNull();
		assertThat(CommandChecks.options(new String[] { "--email", "a@example.com", "extra" }, NAMES))
			.as("not an option")
			.isNull();
		assertThat(CommandChecks.options(new String[] { "-email", "a@example.com" }, NAMES)).as("single dash").isNull();
	}

	@Test
	void emailIsNormalizedOrRefused() {
		assertThat(CommandChecks.email("  Jan@Example.COM ", err)).isEqualTo("jan@example.com");
		assertThat(stderr()).isEmpty();

		assertThat(CommandChecks.email("not-an-email", err)).isNull();
		assertThat(CommandChecks.email("a".repeat(309) + "@example.com", err)).isNull();
		assertThat(stderr()).contains("Invalid e-mail address: 'not-an-email'");
	}

	@Test
	void databaseNeedsEveryVariable() {
		Map<String, String> environment = new HashMap<>(Map.of("PGHOST", "db", "PGPORT", "5432", "PGDATABASE",
				"railway", "PGUSER", "postgres", "PGPASSWORD", "secret"));
		assertThat(CommandChecks.databaseConfigured(environment::get, err)).isTrue();
		assertThat(stderr()).isEmpty();

		environment.remove("PGPORT");
		environment.put("PGUSER", " ");
		assertThat(CommandChecks.databaseConfigured(environment::get, err)).isFalse();
		assertThat(stderr()).contains("Database is not configured: set PGPORT, PGUSER.").doesNotContain("secret");
	}

	private String stderr() {
		return errBytes.toString(StandardCharsets.UTF_8);
	}

}
