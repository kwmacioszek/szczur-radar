package pl.szczurradar.api.pipeline.api;

import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import pl.szczurradar.api.pipeline.run.PipelineRunRepository;

/**
 * Whether a deploy is safe now, for the CI deploy gate (S-14): a deploy interrupts a queued or running
 * run, so the gate waits while one exists. Public (listed in {@code SecurityConfig} and
 * {@code PUBLIC_ROUTES}), so it reveals only that one bit and its reason: no ids, dates, counts or who
 * started a run — the run history is the admin's ({@code /api/admin/pipeline-runs}).
 *
 * <p>A running run outranks a queued one ({@link DeploySafetyReason}). Without the database the answer
 * is 503 with the body of {@code /api/ready}: the gate cannot decide then.
 */
@RestController
class DeploySafetyController {

	private final PipelineRunRepository runs;

	DeploySafetyController(PipelineRunRepository runs) {
		this.runs = runs;
	}

	@GetMapping("/api/deploy-safety")
	ResponseEntity<?> deploySafety() {
		try {
			return ResponseEntity.ok(safety());
		}
		catch (DataAccessException | TransactionException ex) {
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("status", "unavailable"));
		}
	}

	private DeploySafety safety() {
		if (runs.existsRunning()) {
			return DeploySafety.unsafe(DeploySafetyReason.PIPELINE_RUNNING);
		}
		if (runs.existsQueued()) {
			return DeploySafety.unsafe(DeploySafetyReason.PIPELINE_QUEUED);
		}
		return DeploySafety.SAFE;
	}

	/**
	 * Whether a deploy is safe now.
	 *
	 * @param safe {@code true} when no run is queued or running
	 * @param reason why not; {@code null} when it is safe
	 */
	record DeploySafety(boolean safe, @Nullable DeploySafetyReason reason) {

		static final DeploySafety SAFE = new DeploySafety(true, null);

		static DeploySafety unsafe(DeploySafetyReason reason) {
			return new DeploySafety(false, reason);
		}

	}

}
