package pl.szczurradar.api.health;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import pl.szczurradar.api.TestcontainersConfiguration;
import pl.szczurradar.api.contract.ContractFiles;

/** Probe routes, ported from the Python tests/test_health.py. */
class HealthIT {

	/** The body of {@code /api/ready}, which the deploy gate's stub serves ({@code tools/deploy-gate/test.sh}). */
	/** The body of {@code /api/ready} without a database, the same file the gate serves for a 503. */
	private static final Path UNAVAILABLE = Path.of("../tools/deploy-gate/fixtures/api/deploy-safety-unavailable.json");

	private static final Path READY = Path.of("../tools/deploy-gate/fixtures/api/ready.json");

	@Nested
	@SpringBootTest
	@AutoConfigureMockMvc
	@Import(TestcontainersConfiguration.class)
	class WithDatabase {

		@Autowired
		MockMvcTester mvc;

		@Test
		void healthIsOk() {
			assertThat(mvc.get().uri("/api/health")).hasStatusOk()
					.bodyJson().isStrictlyEqualTo("{\"status\":\"ok\"}");
		}

		@Test
		void readyWhenDatabaseReachable() throws Exception {
			MvcTestResult result = mvc.get().uri("/api/ready").exchange();

			assertThat(result).hasStatusOk();
			ContractFiles.assertMatches(result.getResponse().getContentAsString(), READY);
		}

	}

	/**
	 * Nothing listens on port 1, so connecting is refused immediately. The context starting at all
	 * also proves the datasource pool is lazy and that unset PGUSER/PGPASSWORD placeholders do not
	 * break startup: no database is needed to boot or to serve health.
	 */
	@Nested
	@SpringBootTest(properties = {
			"spring.datasource.url=jdbc:postgresql://127.0.0.1:1/unavailable",
			"spring.datasource.hikari.connection-timeout=250",
			// The refused connection is the point of these tests; keep its stack trace out of the log.
			"logging.level.org.springframework.jdbc.support.SQLErrorCodesFactory=ERROR",
			"logging.level.com.zaxxer.hikari=ERROR" })
	@AutoConfigureMockMvc
	class WithoutDatabase {

		@Autowired
		MockMvcTester mvc;

		@Test
		void healthIsOkWithoutDatabase() {
			assertThat(mvc.get().uri("/api/health")).hasStatusOk()
					.bodyJson().isStrictlyEqualTo("{\"status\":\"ok\"}");
		}

		@Test
		void readyReturns503WhenDatabaseUnavailable() throws Exception {
			MvcTestResult result = mvc.get().uri("/api/ready").exchange();

			assertThat(result).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
			ContractFiles.assertMatches(result.getResponse().getContentAsString(), UNAVAILABLE);
		}

	}

}
