package pl.szczurradar.api.pipeline.api;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.EnumMap;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pl.szczurradar.api.config.ApiError;
import pl.szczurradar.api.config.ErrorCode;
import pl.szczurradar.api.pipeline.PipelineRunService;
import pl.szczurradar.api.pipeline.run.AttentionReason;
import pl.szczurradar.api.pipeline.run.InterruptionCause;
import pl.szczurradar.api.pipeline.run.MissingDays;
import pl.szczurradar.api.pipeline.run.PipelineRun;
import pl.szczurradar.api.pipeline.run.PipelineRunRepository;
import pl.szczurradar.api.pipeline.run.PipelineRunSource;
import pl.szczurradar.api.pipeline.run.PipelineRunSourceRepository;
import pl.szczurradar.api.pipeline.run.PipelineRunStep;
import pl.szczurradar.api.pipeline.run.PipelineRunStepRepository;
import pl.szczurradar.api.pipeline.run.RunKind;
import pl.szczurradar.api.pipeline.run.RunStatus;
import pl.szczurradar.api.pipeline.run.RunStep;
import pl.szczurradar.api.pipeline.run.SourceFetchStatus;
import pl.szczurradar.api.pipeline.run.StepStatus;
import pl.szczurradar.api.pipeline.state.RunRecorder;
import pl.szczurradar.api.pipeline.step.SourceIngest;
import pl.szczurradar.api.sources.Source;

/**
 * The admin's pipeline runs: start them, see their history and one run's page (admin only:
 * {@code /api/admin/**}). The service's refusals become API errors in {@link PipelineRunExceptionHandler}.
 */
@RestController
@RequestMapping("/api/admin/pipeline-runs")
public class PipelineRunController {

	/** The longest range a request may name, in days (inclusive). */
	public static final int MAX_RANGE_DAYS = 7;

	/** The largest page of runs a request may ask for. */
	public static final int MAX_PAGE_SIZE = 100;

	/** The most posts a posts run may name. */
	static final int MAX_POSTS = 100;

	private final PipelineRunService service;

	private final PipelineRunRepository runs;

	private final PipelineRunStepRepository steps;

	private final PipelineRunSourceRepository sourceResults;

	private final RunRecorder recorder;

	private final SourceIngest sources;

	private final Clock clock;

	PipelineRunController(PipelineRunService service, PipelineRunRepository runs, PipelineRunStepRepository steps,
			PipelineRunSourceRepository sourceResults, RunRecorder recorder, SourceIngest sources, Clock clock) {
		this.service = service;
		this.runs = runs;
		this.steps = steps;
		this.sourceResults = sourceResults;
		this.recorder = recorder;
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
		return ResponseEntity.status(HttpStatus.ACCEPTED)
			.body(created.stream().map(run -> view(run, List.of(), List.of())).toList());
	}

	/** The latest runs with their steps and source results, newest first (equal creation times by id). */
	@GetMapping
	RunPage list(@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
		Page<PipelineRun> found = runs.findPage(page, size);
		List<UUID> ids = found.map(PipelineRun::getId).getContent();
		Map<UUID, List<PipelineRunStep>> stepsByRun = steps.findByRunIdInOrderByStartedAtAscIdAsc(ids)
			.stream()
			.collect(Collectors.groupingBy(PipelineRunStep::getRunId));
		Map<UUID, List<PipelineRunSource>> sourcesByRun = sourceResults.findByRunIdIn(ids)
			.stream()
			.collect(Collectors.groupingBy(PipelineRunSource::getRunId));
		return new RunPage(found.map(run -> view(run, stepsByRun.getOrDefault(run.getId(), List.of()),
				sourcesByRun.getOrDefault(run.getId(), List.of())))
			.getContent(), page, size, found.getTotalElements());
	}

	/** One run with its steps and source results, for its page; 404 {@code not_found} for an unknown id. */
	@GetMapping("/{id}")
	ResponseEntity<?> run(@PathVariable UUID id) {
		return runs.findById(id)
			.<ResponseEntity<?>>map(run -> ResponseEntity
				.ok(view(run, steps.findByRunIdOrderByStartedAtAscIdAsc(id), sourceResults.findByRunId(id))))
			.orElseGet(() -> ApiError.response(ErrorCode.NOT_FOUND));
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
	 * The days without data ({@link MissingDays}) for the admin's list: the window of the
	 * {@value MissingDays#WINDOW_DAYS} days up to the day before yesterday (today in Warsaw from the
	 * injected clock) and its days that no {@code range} run {@code succeeded} or {@code partial} (the
	 * cron's or an admin's) covers, per configured source: a run covers a source only where the source's
	 * result is {@code succeeded} (it gave a post). A source starts at its first covered day ever, so one
	 * never covered has no day. The admin starts such a range in the form; nothing here starts a run.
	 */
	@GetMapping("/missing-days")
	MissingDaysView missingDays() {
		LocalDate today = LocalDate.ofInstant(clock.instant(), PipelineRunService.ZONE);
		List<Source> configured = sources.states()
			.stream()
			.filter(SourceIngest.SourceState::configured)
			.map(SourceIngest.SourceState::source)
			.toList();
		List<MissingDays.Coverage> coverages = runs
			.findCoveringSince(MissingDays.windowStart(today).atStartOfDay(PipelineRunService.ZONE).toInstant())
			.stream()
			.map(range -> MissingDays.Coverage.of(range, PipelineRunService.ZONE))
			.toList();
		Map<Source, LocalDate> firstCovered = new EnumMap<>(Source.class);
		runs.findFirstCoveredFrom(configured)
			.forEach((source, from) -> firstCovered.put(source, LocalDate.ofInstant(from, PipelineRunService.ZONE)));
		return MissingDaysView.of(MissingDays.of(today, configured, coverages, firstCovered));
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

	/**
	 * {@code run} with {@code runSteps} and {@code runSources}; the posts it holds in assessment are counted
	 * only while it is running (at most one run is).
	 */
	private RunView view(PipelineRun run, List<PipelineRunStep> runSteps, List<PipelineRunSource> runSources) {
		return RunView.of(run, runSteps, runSources,
				run.getStatus() == RunStatus.RUNNING ? recorder.inFlight(run.getId()) : 0);
	}

	/** A page of runs. */
	record RunPage(List<RunView> items, int page, int size, long total) {
	}

	/** The checked window {@code from}–{@code to} (inclusive) and its days without data, ascending. */
	record MissingDaysView(LocalDate from, LocalDate to, List<MissingDayView> days) {

		static MissingDaysView of(MissingDays missing) {
			return new MissingDaysView(missing.from(), missing.to(),
					missing.days().stream().map(day -> new MissingDayView(day.day(), day.sources())).toList());
		}

	}

	/** A day without data and the configured sources that miss it, in {@link Source} order. */
	record MissingDayView(LocalDate day, List<Source> sources) {
	}

	/**
	 * A run with its counters, progress and steps.
	 *
	 * @param interruptionCause why an interrupted run stopped; {@code null} for any other run, and for one
	 *     interrupted before the cause was recorded
	 * @param needsAttention whether it needs the admin's attention ({@link PipelineRun#needsAttention()})
	 * @param attentionReasons why it needs attention, in the rule's order
	 *     ({@link PipelineRun#attentionReasons()}); empty when it does not
	 * @param scheduled whether the daily cron requested it, not an admin ({@link PipelineRun#scheduled()})
	 * @param lastActivityAt the last change while it was running
	 * @param processingErrors posts with a technical error and no stored assessment (not failed attempts)
	 * @param sourcesFailed the sources of a range run that failed (the {@code failed} ones in
	 *     {@code sourceResults})
	 * @param sourcesEmpty the sources of a range run that gave no post (the {@code empty} ones in
	 *     {@code sourceResults})
	 * @param llmRetries HTTP attempts beyond the first of the assessments' LLM calls
	 * @param flexFallbacks assessments whose call fell back from the flex tier to the default one
	 * @param inFlight the posts it is assessing now; 0 unless it is running
	 * @param error the reason of a failed or interrupted run, or the summary of technical errors
	 * @param sourceResults the result of each source a range run has fetched so far, in {@link Source}
	 *     order; empty for any other run and before its first source ends
	 */
	record RunView(UUID id, RunKind kind, RunStatus status, @Nullable InterruptionCause interruptionCause,
			boolean needsAttention, List<AttentionReason> attentionReasons, boolean scheduled,
			@Nullable Instant rangeFrom, @Nullable Instant rangeTo,
			List<Source> sources, @Nullable List<UUID> requestedPostIds, Instant createdAt, @Nullable Instant startedAt,
			@Nullable Instant finishedAt, @Nullable Instant lastActivityAt, int fetched, int newPosts, int assessedOk,
			int assessedFailed, int processingErrors, int dead, int skipped, int sourcesFailed, int sourcesEmpty,
			int llmRetries, int flexFallbacks, int inFlight, @Nullable String error, List<StepView> steps,
			List<SourceResultView> sourceResults) {

		static RunView of(PipelineRun run, List<PipelineRunStep> steps, List<PipelineRunSource> sources, int inFlight) {
			return new RunView(run.getId(), run.getKind(), run.getStatus(), run.getInterruptionCause(),
					run.needsAttention(), run.attentionReasons(), run.scheduled(),
					run.getRangeFrom(), run.getRangeTo(), run.getSources(), run.getRequestedPostIds(),
					run.getCreatedAt(), run.getStartedAt(), run.getFinishedAt(), run.getLastActivityAt(),
					run.getFetched(), run.getNewPosts(), run.getAssessedOk(), run.getAssessedFailed(),
					run.getProcessingErrors(), run.getDead(), run.getSkipped(), run.getSourcesFailed(),
					run.getSourcesEmpty(), run.getLlmRetries(), run.getFlexFallbacks(), inFlight, run.getError(),
					steps.stream().map(StepView::of).toList(),
					sources.stream()
						.sorted(Comparator.comparing(PipelineRunSource::getSource))
						.map(SourceResultView::of)
						.toList());
		}

	}

	/**
	 * The result of one source of a range run ({@link PipelineRunSource}).
	 *
	 * @param fetched the elements it fetched, skipped ones included (its posts are {@code fetched − skipped})
	 * @param skipped the fetched elements that could not become a post
	 * @param error why it failed ({@code <SimpleName>: <message>}); {@code null} unless it failed
	 * @param finishedAt when its result was written
	 */
	record SourceResultView(Source source, SourceFetchStatus status, int fetched, int skipped,
			@Nullable String error, Instant finishedAt) {

		static SourceResultView of(PipelineRunSource result) {
			return new SourceResultView(result.getSource(), result.getStatus(), result.getFetched(),
					result.getSkipped(), result.getError(), result.getFinishedAt());
		}

	}

	/** One step of a run; {@code interrupted} when its run was interrupted while it was running. */
	record StepView(RunStep step, StepStatus status, Instant startedAt, @Nullable Instant finishedAt,
			@Nullable Integer itemsIn, @Nullable Integer itemsOut, @Nullable String error) {

		static StepView of(PipelineRunStep step) {
			return new StepView(step.getStep(), step.getStatus(), step.getStartedAt(), step.getFinishedAt(),
					step.getItemsIn(), step.getItemsOut(), step.getError());
		}

	}

}
