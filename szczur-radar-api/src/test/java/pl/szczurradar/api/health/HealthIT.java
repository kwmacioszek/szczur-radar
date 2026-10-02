package pl.szczurradar.api.health;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import pl.szczurradar.api.TestcontainersConfiguration;

/** Probe routes, ported from the Python tests/test_health.py. */
class HealthIT {

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
		void readyWhenDatabaseReachable() {
			assertThat(mvc.get().uri("/api/ready")).hasStatusOk()
					.bodyJson().isStrictlyEqualTo("{\"status\":\"ready\"}");
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
			"spring.datasource.hikari.connection-timeout=250" })
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
		void readyReturns503WhenDatabaseUnavailable() {
			assertThat(mvc.get().uri("/api/ready")).hasStatus(HttpStatus.SERVICE_UNAVAILABLE)
					.bodyJson().isStrictlyEqualTo("{\"status\":\"unavailable\"}");
		}

	}

}
