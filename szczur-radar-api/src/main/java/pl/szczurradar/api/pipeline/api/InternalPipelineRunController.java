package pl.szczurradar.api.pipeline.api;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pl.szczurradar.api.config.ApiError;
import pl.szczurradar.api.config.ErrorCode;
import pl.szczurradar.api.pipeline.PipelineRunService;
import pl.szczurradar.api.pipeline.run.InterruptionCause;
import pl.szczurradar.api.pipeline.run.PipelineRun;
import pl.szczurradar.api.pipeline.run.PipelineRunRepository;
import pl.szczurradar.api.pipeline.run.PipelineRunStep;
import pl.szczurradar.api.pipeline.run.PipelineRunStepRepository;
import pl.szczurradar.api.pipeline.run.RunKind;
import pl.szczurradar.api.pipeline.run.RunStatus;
import pl.szczurradar.api.pipeline.run.RunStep;
import pl.szczurradar.api.pipeline.run.StepStatus;

/**
 * The daily cron's routes ({@code /internal/**}: the trigger token only, the system account
 * {@code SCHEDULER} as the requester; see {@code config/SecurityConfig}). The cron starts the daily
 * run and polls each created run until it ends. Starting goes through the same
 * {@link PipelineRunService#startRange} as the admin's button.
 */
@RestController
@RequestMapping("/internal/pipeline-runs")
class InternalPipelineRunController {

	private final PipelineRunService service;

	private final PipelineRunRepository runs;

	private final PipelineRunStepRepository steps;

	private final Clock clock;

	InternalPipelineRunController(PipelineRunService service, PipelineRunRepository runs,
			PipelineRunStepRepository steps, Clock clock) {
		this.service = service;
		this.runs = runs;
		this.steps = steps;
		this.clock = clock;
	}

	/**
	 * Queues the daily run, no body: the day before yesterday and yesterday (Warsaw calendar, so a
	 * day's late posts are caught the next night; a repeated day costs no LLM call), from every
	 * configured source, a retry run first when there are leftovers. 202 with the created runs in
	 * execution order; 409 {@code pipeline_busy} while a run is queued or running; 503
	 * {@code source_not_configured}.
	 */
	@PostMapping("/daily")
	ResponseEntity<List<StartedRun>> daily(Authentication authentication) {
		LocalDate today = LocalDate.ofInstant(clock.instant(), PipelineRunService.ZONE);
		List<PipelineRun> created = service.startRange(today.minusDays(2), today.minusDays(1), List.of(),
				UUID.fromString(authentication.getName()));
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(created.stream().map(StartedRun::of).toList());
	}

	/**
	 * A run's status with its step and progress, for polling until it ends (and the cron's heartbeat
	 * meanwhile); 404 {@code not_found} for an unknown id.
	 */
	@GetMapping("/{id}")
	ResponseEntity<?> status(@PathVariable UUID id) {
		return runs.findById(id)
			.<ResponseEntity<?>>map(
					run -> ResponseEntity.ok(RunState.of(run, steps.findByRunIdOrderByStartedAtAscIdAsc(id))))
			.orElseGet(() -> ApiError.response(ErrorCode.NOT_FOUND));
	}

	/** A queued run. */
	record StartedRun(UUID id, RunKind kind) {

		static StartedRun of(PipelineRun run) {
			return new StartedRun(run.getId(), run.getKind());
		}

	}

	/**
	 * A run's state; {@code finishedAt} is set once it has ended. The counters are for the cron's log
	 * lines (meaning as in the admin's run list).
	 *
	 * @param lastActivityAt the last change while it was running
	 * @param interruptionCause why an interrupted run stopped; {@code null} for any other run
	 * @param step the step running now; {@code null} between steps and once the run has ended
	 * @param assessTotal the posts its assess step took ({@code items_in}); {@code null} before that step
	 */
	record RunState(UUID id, RunKind kind, RunStatus status, @Nullable Instant startedAt, @Nullable Instant finishedAt,
			@Nullable Instant lastActivityAt, @Nullable InterruptionCause interruptionCause, @Nullable RunStep step,
			@Nullable Integer assessTotal, int fetched, int newPosts, int assessedOk, int assessedFailed,
			int processingErrors, int dead) {

		/** {@code run} with {@code steps}, its steps in the order they started. */
		static RunState of(PipelineRun run, List<PipelineRunStep> steps) {
			RunStep running = steps.stream()
				.filter(step -> step.getStatus() == StepStatus.RUNNING)
				.map(PipelineRunStep::getStep)
				.reduce((earlier, later) -> later)
				.orElse(null);
			Integer assessTotal = steps.stream()
				.filter(step -> step.getStep() == RunStep.ASSESS)
				.findFirst()
				.map(PipelineRunStep::getItemsIn)
				.orElse(null);
			return new RunState(run.getId(), run.getKind(), run.getStatus(), run.getStartedAt(), run.getFinishedAt(),
					run.getLastActivityAt(), run.getInterruptionCause(), running, assessTotal, run.getFetched(),
					run.getNewPosts(), run.getAssessedOk(), run.getAssessedFailed(), run.getProcessingErrors(),
					run.getDead());
		}

	}

}
