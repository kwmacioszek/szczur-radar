package pl.szczurradar.api.pipeline.api;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pl.szczurradar.api.config.ApiError;
import pl.szczurradar.api.config.ErrorCode;
import pl.szczurradar.api.pipeline.PipelineRunService;
import pl.szczurradar.api.pipeline.run.PipelineRun;
import pl.szczurradar.api.pipeline.run.PipelineRunRepository;
import pl.szczurradar.api.pipeline.run.PipelineRunStep;
import pl.szczurradar.api.pipeline.run.PipelineRunStepRepository;
import pl.szczurradar.api.pipeline.run.RunKind;
import pl.szczurradar.api.pipeline.run.RunStatus;
import pl.szczurradar.api.pipeline.run.RunStep;
import pl.szczurradar.api.pipeline.run.StepStatus;
import pl.szczurradar.api.pipeline.step.SourceIngest;
import pl.szczurradar.api.sources.Source;

/**
 * The admin's pipeline runs: start them and see their history (admin only: {@code /api/admin/**}).
 * The service's refusals become API errors in {@link PipelineRunExceptionHandler}.
 */
@RestController
@RequestMapping("/api/admin/pipeline-runs")
class PipelineRunController {

	/** The longest range a request may name, in days (inclusive). */
	static final int MAX_RANGE_DAYS = 7;

	/** The most posts a posts run may name. */
	static final int MAX_POSTS = 100;

	private final PipelineRunService service;

	private final PipelineRunRepository runs;

	private final PipelineRunStepRepository steps;

	private final SourceIngest sources;

	private final Clock clock;

	PipelineRunController(PipelineRunService service, PipelineRunRepository runs, PipelineRunStepRepository steps,
			SourceIngest sources, Clock clock) {
		this.service = service;
		this.runs = runs;
		this.steps = steps;
		this.sources = sources;
		this.clock = clock;
	}

	/**
	 * 202 with the queued runs: a range (a retry run first when there are leftovers) or given posts.
	 * 400 {@code invalid_request} for a malformed request, {@code post_dead} for a dead post; 409
	 * {@code pipeline_busy} while a run is queued or running; 503 {@code source_not_configured}.
	 */
	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<?> start(@RequestBody StartRequest request, Authentication authentication) {
		if (!request.valid(LocalDate.ofInstant(clock.instant(), PipelineRunService.ZONE))) {
			return ApiError.response(ErrorCode.INVALID_REQUEST);
		}
		UUID admin = UUID.fromString(authentication.getName());
		List<PipelineRun> created = request.postIds() != null ? service.startPosts(request.postIds(), admin)
				: service.startRange(request.from(), request.to(),
						request.sources() == null ? List.of() : request.sources(), admin);
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(created.stream().map(run -> RunView.of(run, List.of())).toList());
	}

	/** The latest runs with their steps, newest first (equal creation times by id). */
	@GetMapping
	RunPage list(@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		Page<PipelineRun> found = runs.findPage(page, size);
		Map<UUID, List<PipelineRunStep>> stepsByRun = steps
			.findByRunIdInOrderByStartedAtAscIdAsc(found.map(PipelineRun::getId).getContent())
			.stream()
			.collect(Collectors.groupingBy(PipelineRunStep::getRunId));
		return new RunPage(found.map(run -> RunView.of(run, stepsByRun.getOrDefault(run.getId(), List.of()))).getContent(),
				page, size, found.getTotalElements());
	}

	/**
	 * The sources a range request may name, and which are configured here (keys set) — the admin form
	 * offers them and checks the configured ones; the SPA knows no source by name.
	 */
	@GetMapping("/sources")
	List<SourceIngest.SourceState> sources() {
		return sources.states();
	}

	/**
	 * Either a range — {@code from}/{@code to} calendar days in Warsaw time, optional {@code sources}
	 * (absent or empty = every configured source) — or {@code postIds}, never both.
	 */
	record StartRequest(@Nullable LocalDate from, @Nullable LocalDate to, @Nullable List<Source> sources,
			@Nullable List<UUID> postIds) {

		/**
		 * A range: both days, {@code from <= to <= today}, at most {@value #MAX_RANGE_DAYS} days; posts:
		 * 1–{@value #MAX_POSTS} ids and nothing of a range.
		 */
		boolean valid(LocalDate today) {
			if (postIds != null) {
				return from == null && to == null && sources == null && !postIds.isEmpty() && postIds.size() <= MAX_POSTS
						&& !postIds.contains(null);
			}
			return from != null && to != null && !from.isAfter(to) && !to.isAfter(today)
					&& ChronoUnit.DAYS.between(from, to) < MAX_RANGE_DAYS && (sources == null || !sources.contains(null));
		}

	}

	/** A page of runs. */
	record RunPage(List<RunView> items, int page, int size, long total) {
	}

	/**
	 * A run with its counters and steps.
	 *
	 * @param processingErrors posts with a technical error and no stored assessment (not failed attempts)
	 * @param error the reason of a failed or interrupted run, or the summary of technical errors
	 */
	record RunView(UUID id, RunKind kind, RunStatus status, @Nullable Instant rangeFrom, @Nullable Instant rangeTo,
			List<Source> sources, @Nullable List<UUID> requestedPostIds, Instant createdAt, @Nullable Instant startedAt,
			@Nullable Instant finishedAt, int fetched, int newPosts, int assessedOk, int assessedFailed,
			int processingErrors, int dead, int skipped, @Nullable String error, List<StepView> steps) {

		static RunView of(PipelineRun run, List<PipelineRunStep> steps) {
			return new RunView(run.getId(), run.getKind(), run.getStatus(), run.getRangeFrom(), run.getRangeTo(),
					run.getSources(), run.getRequestedPostIds(), run.getCreatedAt(), run.getStartedAt(), run.getFinishedAt(),
					run.getFetched(), run.getNewPosts(), run.getAssessedOk(), run.getAssessedFailed(),
					run.getProcessingErrors(), run.getDead(), run.getSkipped(), run.getError(),
					steps.stream().map(StepView::of).toList());
		}

	}

	/** One step of a run. */
	record StepView(RunStep step, StepStatus status, Instant startedAt, @Nullable Instant finishedAt,
			@Nullable Integer itemsIn, @Nullable Integer itemsOut, @Nullable String error) {

		static StepView of(PipelineRunStep step) {
			return new StepView(step.getStep(), step.getStatus(), step.getStartedAt(), step.getFinishedAt(),
					step.getItemsIn(), step.getItemsOut(), step.getError());
		}

	}

}
