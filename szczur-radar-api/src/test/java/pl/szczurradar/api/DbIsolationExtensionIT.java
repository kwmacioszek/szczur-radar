package pl.szczurradar.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import pl.szczurradar.api.cli.MigrateCommand;
import pl.szczurradar.api.user.SystemAccount;

/**
 * {@link DbIsolationExtension}'s clean and check, called directly on committed data. The extension also
 * guards this class like every other one, so each test empties the database itself before it ends.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = MigrateCommand.ENABLE_FLYWAY)
@Import(TestcontainersConfiguration.class)
class DbIsolationExtensionIT {

	@Autowired
	JdbcTemplate jdbc;

	@AfterEach
	void emptyTheDatabase() {
		DbIsolationExtension.clean(jdbc);
	}

	@Test
	void cleanRemovesCommittedRowsAndKeepsTheSchedulerAndTheMigrationHistory() {
		long history = count("SELECT count(*) FROM flyway_schema_history");
		UUID admin = insertUser("isolation-clean@example.com");
		insertRun(admin);
		insertRawPayload();

		DbIsolationExtension.clean(jdbc);

		assertThat(count("SELECT count(*) FROM users WHERE id <> ?", SystemAccount.SCHEDULER.id())).isZero();
		assertThat(count("SELECT count(*) FROM users WHERE id = ?", SystemAccount.SCHEDULER.id())).isEqualTo(1);
		assertThat(count("SELECT count(*) FROM pipeline_runs")).isZero();
		assertThat(count("SELECT count(*) FROM raw_payloads")).isZero();
		assertThat(count("SELECT count(*) FROM flyway_schema_history")).isEqualTo(history);
		assertThatCode(() -> DbIsolationExtension.requireClean(jdbc, "AfterClean")).doesNotThrowAnyException();
	}

	@Test
	void theCheckPassesOnACleanDatabaseWhichHoldsOnlyTheScheduler() {
		DbIsolationExtension.clean(jdbc);

		assertThatCode(() -> DbIsolationExtension.requireClean(jdbc, "CleanClass")).doesNotThrowAnyException();
	}

	@Test
	void theCheckFailsNamingTheTablesTheirRowCountsAndTheClass() {
		UUID admin = insertUser("isolation-dirty@example.com");
		insertRun(admin);
		insertRawPayload();

		assertThatThrownBy(() -> DbIsolationExtension.requireClean(jdbc, "SomeNextIT"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("SomeNextIT")
			.hasMessageContaining("users (1)")
			.hasMessageContaining("pipeline_runs (1)")
			.hasMessageContaining("raw_payloads (1)")
			.hasMessageNotContaining("flyway_schema_history");
	}

	private long count(String sql, Object... args) {
		Long rows = jdbc.queryForObject(sql, Long.class, args);
		return rows == null ? 0 : rows;
	}

	private UUID insertUser(String email) {
		return jdbc.queryForObject("INSERT INTO users (email, role) VALUES (?, 'admin') RETURNING id", UUID.class,
				email);
	}

	private void insertRun(UUID admin) {
		jdbc.update("INSERT INTO pipeline_runs (kind, status, requested_by) VALUES ('retry', 'succeeded', ?)", admin);
	}

	private void insertRawPayload() {
		jdbc.update("INSERT INTO raw_payloads (source, content_encoding, media_type, payload, sha256)"
				+ " VALUES ('wykop', 'gzip', 'application/json', '\\x00', ?)", "0".repeat(64));
	}

}
