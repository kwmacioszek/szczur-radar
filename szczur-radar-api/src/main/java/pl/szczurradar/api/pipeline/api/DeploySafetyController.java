package pl.szczurradar.api.pipeline.api;

import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import pl.szczurradar.api.pipeline.PipelineStallSettings;
import pl.szczurradar.api.pipeline.run.PipelineRunRepository;

/**
 * Whether a deploy is safe now, for the CI deploy gate (S-14): a deploy interrupts a queued or running
 * run, so the gate waits while one is alive. Public (listed in {@code SecurityConfig} and
 * {@code PUBLIC_ROUTES}), so it reveals only that one bit and its reason: no ids, dates, counts or who
 * started a run — the run history is the admin's ({@code /api/admin/pipeline-runs}).
 *
 * <p>A queued or running run without activity for {@code PIPELINE_STALL_TIMEOUT}
 * ({@link PipelineStallSettings}: its last activity, else its start, else its creation is older, by the
 * database's clock; the creation is written by the application's clock, a negligible difference) is dead: no instance executes it, e.g. its instance was lost without closing it.
 * While every active run is dead the deploy is safe — it interrupts nothing alive, and the new
 * instance's start-up recovery interrupts those runs anyway. The answer only reads: the next request to
 * start runs interrupts the dead ones ({@code lost}), never this route.
 *
 * <p>A running run outranks a queued one ({@link DeploySafetyReason}); once a live run makes the deploy
 * unsafe, the reason looks at every active run, dead or not. Without the database the answer is 503 with
 * the body of {@code /api/ready}: the gate cannot decide then.
 */
@RestController
class DeploySafetyController {

	private final PipelineRunRepository runs;

	/**
	 * {@code PIPELINE_STALL_TIMEOUT}, the server's bean; resolved only when asked, so a context without a
	 * web server (an operator command, e.g. the pre-deploy {@code migrate}) never needs or reads it.
	 */
	private final ObjectProvider<PipelineStallSettings> stall;

	DeploySafetyController(PipelineRunRepository runs, ObjectProvider<PipelineStallSettings> stall) {
		this.runs = runs;
		this.stall = stall;
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

	// One statement decides the bit: two (running, then queued) would both miss a run that leaves the
	// queue between them and answer safe while it runs. It counts only live runs, measured by the
	// database's clock: a dead one (no activity for PIPELINE_STALL_TIMEOUT) does not hold the deploy.
	// Only the reason is asked afterwards.
	private DeploySafety safety() {
		if (!runs.existsLiveActive(stall.getObject().timeout())) {
			return DeploySafety.SAFE;
		}
		return DeploySafety.unsafe(runs.existsRunning() ? DeploySafetyReason.PIPELINE_RUNNING
				: DeploySafetyReason.PIPELINE_QUEUED);
	}

	/**
	 * Whether a deploy is safe now.
	 *
	 * @param safe {@code true} when no run is queued or running, or every one of them is dead
	 * @param reason why not; {@code null} when it is safe
	 */
	record DeploySafety(boolean safe, @Nullable DeploySafetyReason reason) {

		static final DeploySafety SAFE = new DeploySafety(true, null);

		static DeploySafety unsafe(DeploySafetyReason reason) {
			return new DeploySafety(false, reason);
		}

	}

}
