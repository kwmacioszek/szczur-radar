package pl.szczurradar.api.health;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public probe routes under {@code /api}. The web service reverse-proxies {@code /api/*} to this
 * service preserving the path, so they are reachable at {@code /api/health} and {@code /api/ready}
 * through the single public origin.
 */
@RestController
@RequestMapping("/api")
class HealthController {

	private static final Logger log = LoggerFactory.getLogger(HealthController.class);

	private final JdbcTemplate jdbc;

	HealthController(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** Liveness: the process is up and serving. No dependencies checked. */
	@GetMapping("/health")
	Map<String, String> health() {
		return Map.of("status", "ok");
	}

	/**
	 * Readiness: succeeds only if a PostgreSQL query round-trips. Returns 503 (not 500) on failure so
	 * orchestrators treat it as "not ready yet" rather than a crash. The cause goes to the log as one
	 * warning line — its class and message, no stack trace (a probe may fail often while the database is
	 * down) and no operator alert.
	 */
	@GetMapping("/ready")
	ResponseEntity<Map<String, String>> ready() {
		try {
			jdbc.queryForObject("SELECT 1", Integer.class);
		}
		catch (RuntimeException ex) {
			log.warn("Not ready: the database query failed: {}", ex.toString());
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("status", "unavailable"));
		}
		return ResponseEntity.ok(Map.of("status", "ready"));
	}

}
