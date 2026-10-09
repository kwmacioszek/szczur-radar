package pl.szczurradar.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;

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
		assertThat(history).extracting(row -> row.get("version")).containsExactly("1", "2", "3", "4", "5");
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

	@Test
	void pipelineTablesHaveNamedConstraintsAndIndexes() {
		assertThat(constraintsOf("pipeline_runs")).contains("ck_pipeline_runs_kind", "ck_pipeline_runs_status",
				"ck_pipeline_runs_range", "ck_pipeline_runs_requested_posts", "fk_pipeline_runs_requested_by");
		assertThat(constraintsOf("posts")).contains("uq_posts_source_item", "uq_posts_raw_payload", "ck_posts_stage",
				"ck_posts_stage_attempts", "ck_posts_assessing_owner", "fk_posts_raw_payload", "ck_posts_item_type",
				"ck_posts_source")
			.doesNotContain("ck_posts_resource");
		assertThat(constraintsOf("assessments")).contains("ck_assessments_outcome", "ck_assessments_failure_class",
				"ck_assessments_provider", "fk_assessments_post", "fk_assessments_run");
		assertThat(constraintsOf("raw_payloads")).contains("ck_raw_payloads_content_encoding", "ck_raw_payloads_sha256");
		assertThat(jdbc.queryForList("SELECT indexname FROM pg_indexes WHERE schemaname = 'public'", String.class))
			.contains("uq_pipeline_runs_one_running", "ix_pipeline_runs_created", "ix_posts_stage_published",
					"ix_assessments_post_latest", "ix_pipeline_run_steps_run");
	}

	@Test
	void onlyOneRunIsRunning() {
		UUID admin = user("one-running@example.com");
		run(admin, "running");
		run(admin, "queued");
		assertRejected("INSERT INTO pipeline_runs (kind, status, requested_by) VALUES ('retry', 'running', '" + admin
				+ "')", "uq_pipeline_runs_one_running");
		jdbc.update("UPDATE pipeline_runs SET status = 'interrupted' WHERE status = 'running'");
	}

	@Test
	void unknownRunStatusAndARangeRunWithoutARangeAreRejected() {
		UUID admin = user("run-checks@example.com");
		assertRejected("INSERT INTO pipeline_runs (kind, status, requested_by) VALUES ('retry', 'done', '" + admin + "')",
				"ck_pipeline_runs_status");
		assertRejected("INSERT INTO pipeline_runs (kind, requested_by) VALUES ('range', '" + admin + "')",
				"ck_pipeline_runs_range");
		assertRejected("INSERT INTO pipeline_runs (kind, requested_by, range_from, range_to) VALUES ('range', '" + admin
				+ "', '2026-10-02T00:00:00Z', '2026-10-01T00:00:00Z')", "ck_pipeline_runs_range");
		// A range run names its sources (V5), only registered ones; other runs name none.
		assertRejected("INSERT INTO pipeline_runs (kind, requested_by, range_from, range_to, sources) VALUES ('range', '"
				+ admin + "', '2026-10-01T00:00:00Z', '2026-10-01T23:59:59Z', ARRAY['reddit'])", "ck_pipeline_runs_sources");
		assertRejected("INSERT INTO pipeline_runs (kind, requested_by, range_from, range_to, sources) VALUES ('range', '"
				+ admin + "', '2026-10-01T00:00:00Z', '2026-10-01T23:59:59Z', ARRAY[]::TEXT[])", "ck_pipeline_runs_sources");
		assertRejected("INSERT INTO pipeline_runs (kind, requested_by, sources) VALUES ('retry', '" + admin
				+ "', ARRAY['wykop'])", "ck_pipeline_runs_sources");
		// A run of another kind carries no half of a range (V4).
		assertRejected("INSERT INTO pipeline_runs (kind, requested_by, range_from) VALUES ('retry', '" + admin
				+ "', '2026-10-01T00:00:00Z')", "ck_pipeline_runs_range");
		assertRejected("INSERT INTO pipeline_runs (kind, requested_by, range_to) VALUES ('retry', '" + admin
				+ "', '2026-10-01T00:00:00Z')", "ck_pipeline_runs_range");
	}

	@Test
	void aPostIsStoredOncePerSourceResourceAndId() {
		UUID run = run(user("post-key@example.com"), "queued");
		insertPost("post", "70001", "stored", 0, null, run);
		// The same id as a link is another post.
		insertPost("link", "70001", "stored", 0, null, run);
		assertRejected(postInsert("post", "70001", "stored", 0, null, run), "uq_posts_source_item");
		// The shared model's types only (V4), never a source's own names.
		assertRejected(postInsert("entry", "70002", "stored", 0, null, run), "ck_posts_item_type");
	}

	@Test
	void aPostsStageAgreesWithItsAttemptsAndItsOwner() {
		UUID run = run(user("post-stage@example.com"), "queued");
		assertRejected(postInsert("post", "70101", "dead", 2, null, run), "ck_posts_stage_attempts");
		assertRejected(postInsert("post", "70102", "failed", 0, null, run), "ck_posts_stage_attempts");
		assertRejected(postInsert("post", "70103", "stored", 1, null, run), "ck_posts_stage_attempts");
		assertRejected(postInsert("post", "70104", "assessing", 0, null, run), "ck_posts_assessing_owner");
		assertRejected(postInsert("post", "70105", "stored", 0, run, run), "ck_posts_assessing_owner");
		insertPost("post", "70106", "assessing", 1, run, run);
		insertPost("post", "70107", "dead", 3, null, run);
	}

	@Test
	void anAssessmentIsOkWithAnOutputOrFailedWithAClass() {
		UUID run = run(user("assessment@example.com"), "queued");
		UUID post = insertPost("post", "70201", "stored", 0, null, run);
		String insert = "INSERT INTO assessments (post_id, run_id, status, failure_class, input, input_hash, output,"
				+ " violations, result_source, flex_fallback, contract_version, assessed_at, duration_ms, attempts)"
				+ " VALUES ('" + post + "', '" + run + "', '%s', %s, '{}', '" + "0".repeat(64) + "', %s, '[]', 'prefilter',"
				+ " false, 'V2', now(), 0, 0)";
		assertRejected(insert.formatted("ok", "NULL", "NULL"), "ck_assessments_outcome");
		assertRejected(insert.formatted("failed", "NULL", "NULL"), "ck_assessments_outcome");
		assertRejected(insert.formatted("failed", "'refused'", "NULL"), "ck_assessments_failure_class");
		jdbc.update(insert.formatted("ok", "NULL", "'{}'"));
		jdbc.update(insert.formatted("failed", "'timeout'", "NULL"));
	}

	private List<String> constraintsOf(String table) {
		return jdbc.queryForList("SELECT conname FROM pg_constraint WHERE conrelid = ?::regclass", String.class,
				"public." + table);
	}

	private UUID user(String email) {
		return jdbc.queryForObject("INSERT INTO users (email, role) VALUES (?, 'admin') RETURNING id", UUID.class, email);
	}

	private UUID run(UUID requestedBy, String status) {
		return jdbc.queryForObject(
				"INSERT INTO pipeline_runs (kind, status, requested_by) VALUES ('retry', ?, ?) RETURNING id", UUID.class,
				status, requestedBy);
	}

	private UUID insertPost(String itemType, String sourceItemId, String stage, int attempts, UUID assessingRun,
			UUID firstRun) {
		return jdbc.queryForObject(postInsert(itemType, sourceItemId, stage, attempts, assessingRun, firstRun) + " RETURNING id",
				UUID.class);
	}

	/** A post with its own raw payload, inserted by the same statement. */
	private String postInsert(String itemType, String sourceItemId, String stage, int attempts, UUID assessingRun,
			UUID firstRun) {
		String hash = "'" + "0".repeat(64) + "'";
		return "WITH raw AS (INSERT INTO raw_payloads (source, content_encoding, media_type, payload, sha256)"
				+ " VALUES ('wykop', 'gzip', 'application/json', '\\x00', " + hash + ") RETURNING id)"
				+ " INSERT INTO posts (source, item_type, source_item_id, published_at, raw_payload_id, content_hash,"
				+ " stage, assessment_attempts, assessing_run_id, first_run_id) SELECT 'wykop', '" + itemType + "', '"
				+ sourceItemId + "', now(), raw.id, " + hash + ", '" + stage + "', " + attempts + ", "
				+ (assessingRun == null ? "NULL" : "'" + assessingRun + "'") + ", '" + firstRun + "' FROM raw";
	}

	private void assertRejected(String sql, String constraint) {
		assertThatThrownBy(() -> jdbc.update(sql))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining(constraint);
	}

}
