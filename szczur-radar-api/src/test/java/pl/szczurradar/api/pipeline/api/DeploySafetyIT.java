package pl.szczurradar.api.pipeline.api;

import static org.assertj.core.api.Assertions.assertThat;

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
import pl.szczurradar.api.pipeline.run.PipelineRun;
import pl.szczurradar.api.pipeline.run.PipelineRunRepository;
import pl.szczurradar.api.pipeline.run.RunStatus;
import pl.szczurradar.api.user.SystemAccount;

/**
 * {@code GET /api/deploy-safety}, the CI deploy gate's question: every answer, without a session, and
 * 503 without the database.
 */
class DeploySafetyIT {

	private static final String DEPLOY_SAFETY = "/api/deploy-safety";

	/**
	 * Runs are stored through the entity and then given their status as the pipeline would. Each test
	 * rolls back.
	 */
	@Nested
	@SpringBootTest(properties = MigrateCommand.ENABLE_FLYWAY)
	@AutoConfigureMockMvc
	@Import(TestcontainersConfiguration.class)
	@Transactional
	class WithDatabase {

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
		private void run(RunStatus status) {
			UUID id = runs.saveAndFlush(PipelineRun.retry(SystemAccount.SCHEDULER.id(), null)).getId();
			jdbc.update("UPDATE pipeline_runs SET status = ? WHERE id = ?", status.value(), id);
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
