package pl.szczurradar.api.pipeline.api;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pl.szczurradar.api.alerts.AlertEvent;
import pl.szczurradar.api.alerts.OperatorAlertQueue;
import pl.szczurradar.api.config.ApiError;
import pl.szczurradar.api.config.ErrorCode;
import pl.szczurradar.api.pipeline.NightSummary;
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
 * run, polls each created run until it ends and then asks for the night's summary, which goes to the
 * operator ({@link AlertEvent#NIGHT_SUMMARY}, then {@link AlertEvent#ASSESSMENT_QUALITY} when a source
 * crossed its quality threshold) and into the cron's log. Starting goes through the same
 * {@link PipelineRunService#startRange} as the admin's button.
 */
@RestController
@RequestMapping("/internal/pipeline-runs")
class InternalPipelineRunController {

	private final PipelineRunService service;

	private final PipelineRunRepository runs;

	private final PipelineRunStepRepository steps;

	private final NightSummary summary;

	/**
	 * The server's queue ({@code AlertsConfiguration}), resolved when used: an operator command's context
	 * (the pre-deploy {@code migrate}) builds this controller too and has no queue.
	 */
	private final ObjectProvider<OperatorAlertQueue> alerts;

	private final Clock clock;

	InternalPipelineRunController(PipelineRunService service, PipelineRunRepository runs,
			PipelineRunStepRepository steps, NightSummary summary, ObjectProvider<OperatorAlertQueue> alerts,
			Clock clock) {
		this.service = service;
		this.runs = runs;
		this.steps = steps;
		this.summary = summary;
		this.alerts = alerts;
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

	/**
	 * The night's summary of the given runs ({@link NightSummary}): raised to the operator as
	 * {@link AlertEvent#NIGHT_SUMMARY} for the range run (else the first one, so a repeated call within the
	 * queue's repeat window is one message) and returned as {@code {"text": …}} for the cron's log; when a
	 * source crossed its quality threshold tonight ({@link NightSummary.Night#qualityAlert()}), also
	 * {@link AlertEvent#ASSESSMENT_QUALITY} for the same run, after the summary. 400
	 * {@code invalid_request} without 1–{@value NightlySummaryRequest#MAX_RUNS} ids; 404 {@code not_found}
	 * when one of them is no run.
	 */
	@PostMapping("/nightly-summary")
	ResponseEntity<?> nightlySummary(@RequestBody NightlySummaryRequest request) {
		if (!request.valid()) {
			return ApiError.response(ErrorCode.INVALID_REQUEST);
		}
		NightSummary.Night night = summary.of(request.runs()).orElse(null);
		if (night == null) {
			return ApiError.response(ErrorCode.NOT_FOUND);
		}
		String text = night.text();
		OperatorAlertQueue.raise(alerts, AlertEvent.NIGHT_SUMMARY, night.alertRunId(), text);
		night.qualityAlert()
			.ifPresent(detail -> OperatorAlertQueue.raise(alerts, AlertEvent.ASSESSMENT_QUALITY, night.alertRunId(),
					detail));
		return ResponseEntity.ok(new NightlySummary(text));
	}

	/** A queued run. */
	record StartedRun(UUID id, RunKind kind) {

		static StartedRun of(PipelineRun run) {
			return new StartedRun(run.getId(), run.getKind());
		}

	}

	/** The runs of one night, as {@code /daily} answered them. */
	record NightlySummaryRequest(@Nullable List<UUID> runs) {

		/** The most runs of one night: {@code /daily} creates at most two. */
		static final int MAX_RUNS = 10;

		boolean valid() {
			return runs != null && !runs.isEmpty() && runs.size() <= MAX_RUNS && !runs.contains(null);
		}

	}

	/** The night's summary, the text the operator gets. */
	record NightlySummary(String text) {
	}

	/**
	 * A run's state; {@code finishedAt} is set once it has ended. The counters are for the cron's log
	 * lines (meaning as in the admin's run list).
	 *
	 * @param lastActivityAt the last change while it was running
	 * @param interruptionCause why an interrupted run stopped; {@code null} for any other run
	 * @param step the step running now; {@code null} between steps and once the run has ended
	 * @param assessTotal the posts its assess step took ({@code items_in}); {@code null} before that step
	 * @param skipped the fetched elements that could not become a post
	 */
	record RunState(UUID id, RunKind kind, RunStatus status, @Nullable Instant startedAt, @Nullable Instant finishedAt,
			@Nullable Instant lastActivityAt, @Nullable InterruptionCause interruptionCause, @Nullable RunStep step,
			@Nullable Integer assessTotal, int fetched, int newPosts, int assessedOk, int assessedFailed,
			int processingErrors, int dead, int skipped) {

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
					run.getDead(), run.getSkipped());
		}

	}

}
