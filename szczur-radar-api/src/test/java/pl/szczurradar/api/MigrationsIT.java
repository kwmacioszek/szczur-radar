package pl.szczurradar.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import pl.szczurradar.api.cli.MigrateCommand;

/**
 * Migrations from scratch on an empty database (replaces the Python upgrade/downgrade round trip:
 * Flyway Community has no undo, the rule is fix-forward + PITR).
 *
 * <p>The context enables Flyway with the same override the {@code migrate} command uses, so Spring
 * Boot's Flyway auto-configuration migrates this context's fresh container on startup.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = MigrateCommand.ENABLE_FLYWAY)
@Import(TestcontainersConfiguration.class)
class MigrationsIT {

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void allMigrationsApplyOnEmptyDatabase() {
		List<Map<String, Object>> history = jdbc.queryForList(
				"SELECT version, success FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank");
		assertThat(history).extracting(row -> row.get("version")).containsExactly("1", "2");
		assertThat(history).allSatisfy(row -> assertThat(row.get("success")).isEqualTo(true));
	}

	@Test
	void usersTableHasNamedConstraints() {
		List<String> constraints = jdbc.queryForList(
				"SELECT conname FROM pg_constraint WHERE conrelid = 'public.users'::regclass", String.class);
		assertThat(constraints).contains("uq_users_email", "ck_users_email_lower", "ck_users_role");
	}

	@Test
	void usersDefaultsAndNoTokenVersion() {
		Map<String, Object> row = jdbc.queryForMap(
				"INSERT INTO users (email) VALUES ('defaults@example.com') RETURNING id, role, password_hash, created_at");
		assertThat(row.get("id")).isNotNull();
		assertThat(row.get("role")).isEqualTo("user");
		assertThat(row.get("password_hash")).isNull();
		assertThat(row.get("created_at")).isNotNull();

		Integer tokenVersionColumns = jdbc.queryForObject(
				"SELECT count(*) FROM information_schema.columns WHERE table_name = 'users' AND column_name = 'token_version'",
				Integer.class);
		assertThat(tokenVersionColumns).isZero();
	}

	@Test
	void unknownRoleIsRejected() {
		assertRejected("INSERT INTO users (email, role) VALUES ('root@example.com', 'root')", "ck_users_role");
	}

	@Test
	void nonLowercaseEmailIsRejected() {
		assertRejected("INSERT INTO users (email) VALUES ('Admin@Example.com')", "ck_users_email_lower");
	}

	@Test
	void duplicateEmailIsRejected() {
		jdbc.update("INSERT INTO users (email) VALUES ('dup@example.com')");
		assertRejected("INSERT INTO users (email) VALUES ('dup@example.com')", "uq_users_email");
	}

	@Test
	void springSessionTablesExist() {
		assertThat(jdbc.queryForObject("SELECT to_regclass('public.spring_session')::text", String.class))
				.isEqualTo("spring_session");
		assertThat(jdbc.queryForObject("SELECT to_regclass('public.spring_session_attributes')::text", String.class))
				.isEqualTo("spring_session_attributes");
		List<String> indexes = jdbc.queryForList(
				"SELECT indexname FROM pg_indexes WHERE tablename = 'spring_session'", String.class);
		assertThat(indexes).contains("spring_session_ix1", "spring_session_ix2", "spring_session_ix3");
	}

	private void assertRejected(String sql, String constraint) {
		assertThatThrownBy(() -> jdbc.update(sql))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining(constraint);
	}

}
