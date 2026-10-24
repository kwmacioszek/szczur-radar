package pl.szczurradar.api.pipeline.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

import pl.szczurradar.api.TestcontainersConfiguration;
import pl.szczurradar.api.cli.MigrateCommand;
import pl.szczurradar.api.pipeline.PipelineStallSettings;
import pl.szczurradar.api.pipeline.run.PipelineRun;
import pl.szczurradar.api.pipeline.run.PipelineRunRepository;
import pl.szczurradar.api.pipeline.run.RunStatus;
import pl.szczurradar.api.user.SystemAccount;

/**
 * {@code GET /api/deploy-safety}, the CI deploy gate's question: every answer, dead runs (no activity for
 * {@code PIPELINE_STALL_TIMEOUT}) safe and left as they were, without a session, and 503 without the
 * database.
 */
class DeploySafetyIT {

	private static final String DEPLOY_SAFETY = "/api/deploy-safety";

	/**
	 * Runs are stored through the entity and then given their status as the pipeline would; a dead one
	 * gets its times an hour back with SQL. Each test rolls back. The timeout is set, so the dead runs do
	 * not depend on the LLM settings' default, and long, so a run just stored (its creation time from
	 * this JVM's clock) is never aged by a clock skew to the database's.
	 */
	@Nested
	@SpringBootTest(properties = { MigrateCommand.ENABLE_FLYWAY, PipelineStallSettings.NAME + "=10m" })
	@AutoConfigureMockMvc
	@Import(TestcontainersConfiguration.class)
	@Transactional
	class WithDatabase {

		/** An hour without activity: far older than the timeout. */
		private static final String DEAD = "now() - interval '1 hour'";

		@Autowired
		MockMvcTester mvc;

		@Autowired
		JdbcTemplate jdbc;

		@Autowired
		PipelineRunRepository runs;

		@BeforeEach
		void setUp() {
			// The other tests of this context share the database: within this test's transaction no earlier
			// run is queued or running, so only the runs of this test count (and at most one runs, as the
			// index uq_pipeline_runs_one_running allows).
			jdbc.update("UPDATE pipeline_runs SET status = 'interrupted' WHERE status IN ('queued', 'running')");
		}

		@Test
		void withoutRunsItIsSafe() {
			assertDeploySafety("{\"safe\":true,\"reason\":null}");
		}

		@Test
		void aQueuedRunIsNotSafe() {
			run(RunStatus.QUEUED);

			assertDeploySafety("{\"safe\":false,\"reason\":\"pipeline_queued\"}");
		}

		@Test
		void aRunningRunIsNotSafe() {
			run(RunStatus.RUNNING);

			assertDeploySafety("{\"safe\":false,\"reason\":\"pipeline_running\"}");
		}

		@Test
		void aRunningRunOutranksAQueuedOne() {
			// A retry runs, the range waits behind it.
			run(RunStatus.RUNNING);
			run(RunStatus.QUEUED);

			assertDeploySafety("{\"safe\":false,\"reason\":\"pipeline_running\"}");
		}

		@Test
		void aDeadRunningRunIsSafeAndLeftAsItWas() {
			// Its instance was lost without closing it: no one runs it, and only a request to start runs
			// interrupts it — never this read.
			UUID id = deadRun(RunStatus.RUNNING);
			Map<String, Object> before = row(id);

			assertDeploySafety("{\"safe\":true,\"reason\":null}");
			assertThat(row(id)).isEqualTo(before).containsEntry("status", RunStatus.RUNNING.value());
		}

		@Test
		void aDeadQueuedRunIsSafe() {
			// Queued on an instance that was lost before it ran (or whose submit never reached its queue).
			deadRun(RunStatus.QUEUED);

			assertDeploySafety("{\"safe\":true,\"reason\":null}");
		}

		@Test
		void aQueuedRunBehindARunThatJustEndedIsNotSafe() {
			// The retry ran longer than the timeout and just ended; the range queued with it has not started.
			UUID ended = run(RunStatus.SUCCEEDED);
			jdbc.update("UPDATE pipeline_runs SET created_at = " + DEAD + ", started_at = " + DEAD
					+ ", last_activity_at = " + DEAD + ", finished_at = now() WHERE id = ?", ended);
			deadRun(RunStatus.QUEUED);

			assertDeploySafety("{\"safe\":false,\"reason\":\"pipeline_queued\"}");
		}

		@Test
		void aRunningRunWithRecentActivityIsNotSafe() {
			// Started long ago, but its last activity is within the timeout: the activity decides.
			UUID id = run(RunStatus.RUNNING);
			jdbc.update("UPDATE pipeline_runs SET created_at = " + DEAD + ", started_at = " + DEAD
					+ ", last_activity_at = now() WHERE id = ?", id);

			assertDeploySafety("{\"safe\":false,\"reason\":\"pipeline_running\"}");
		}

		@Test
		void aLiveQueuedRunBehindADeadRunningOneIsNotSafe() {
			deadRun(RunStatus.RUNNING);
			run(RunStatus.QUEUED);

			assertThat(mvc.get().uri(DEPLOY_SAFETY)).hasStatusOk()
				.bodyJson()
				.extractingPath("$.safe")
				.isEqualTo(false);
		}

		@ParameterizedTest
		@EnumSource(value = RunStatus.class, names = { "SUCCEEDED", "PARTIAL", "FAILED", "INTERRUPTED" })
		void onlyEndedRunsAreSafe(RunStatus status) {
			run(status);

			assertDeploySafety("{\"safe\":true,\"reason\":null}");
		}

		@Test
		void itNeedsNoSessionAndCreatesNone() {
			MvcTestResult result = mvc.get().uri(DEPLOY_SAFETY).exchange();

			assertThat(result).hasStatusOk();
			assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
		}

		/** A run of the scheduler with {@code status}. */
		private UUID run(RunStatus status) {
			UUID id = runs.saveAndFlush(PipelineRun.retry(SystemAccount.SCHEDULER.id(), null)).getId();
			jdbc.update("UPDATE pipeline_runs SET status = ? WHERE id = ?", status.value(), id);
			return id;
		}

		/**
		 * A run of the scheduler with {@code status} and no activity for an hour: created then and, when
		 * running, started and last active then too.
		 */
		private UUID deadRun(RunStatus status) {
			UUID id = run(status);
			if (status == RunStatus.RUNNING) {
				jdbc.update("UPDATE pipeline_runs SET created_at = " + DEAD + ", started_at = " + DEAD
						+ ", last_activity_at = " + DEAD + " WHERE id = ?", id);
			}
			else {
				jdbc.update("UPDATE pipeline_runs SET created_at = " + DEAD + " WHERE id = ?", id);
			}
			return id;
		}

		/** What a write to a run would change. */
		private Map<String, Object> row(UUID id) {
			return jdbc.queryForMap("SELECT status, started_at, finished_at, last_activity_at, interruption_cause,"
					+ " error FROM pipeline_runs WHERE id = ?", id);
		}

		/** The answer without a session: the bit and its reason, nothing else about the runs. */
		private void assertDeploySafety(String body) {
			assertThat(mvc.get().uri(DEPLOY_SAFETY)).hasStatusOk().bodyJson().isStrictlyEqualTo(body);
		}

	}

	/** Nothing listens on port 1 (as in {@code HealthIT}): the gate gets the body of {@code /api/ready}. */
	@Nested
	@SpringBootTest(properties = {
			"spring.datasource.url=jdbc:postgresql://127.0.0.1:1/unavailable",
			"spring.datasource.hikari.connection-timeout=250",
			// The refused connection is the point of this test; keep its stack trace out of the log.
			"logging.level.org.springframework.jdbc.support.SQLErrorCodesFactory=ERROR",
			"logging.level.com.zaxxer.hikari=ERROR" })
	@AutoConfigureMockMvc
	class WithoutDatabase {

		@Autowired
		MockMvcTester mvc;

		@Test
		void itIsUnavailableWithoutDatabase() {
			assertThat(mvc.get().uri(DEPLOY_SAFETY)).hasStatus(HttpStatus.SERVICE_UNAVAILABLE)
				.bodyJson()
				.isStrictlyEqualTo("{\"status\":\"unavailable\"}");
		}

	}

}
