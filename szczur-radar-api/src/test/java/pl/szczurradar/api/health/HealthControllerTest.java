package pl.szczurradar.api.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;

/** {@code /api/ready} names the cause of its 503 in the log: one warning line, no stack trace. */
@ExtendWith(OutputCaptureExtension.class)
class HealthControllerTest {

	@Test
	void aFailedQueryIsA503WithItsCauseInOneWarningLine(CapturedOutput output) {
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		when(jdbc.queryForObject("SELECT 1", Integer.class))
			.thenThrow(new CannotGetJdbcConnectionException("Failed to obtain JDBC Connection"));

		ResponseEntity<Map<String, String>> response = new HealthController(jdbc).ready();

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
		assertThat(response.getBody()).isEqualTo(Map.of("status", "unavailable"));
		assertThat(output.getOut().lines().filter(line -> line.contains("WARN"))).singleElement()
			.asString()
			.contains(CannotGetJdbcConnectionException.class.getName() + ": Failed to obtain JDBC Connection");
		assertThat(output).doesNotContain("\tat ");
	}

}
