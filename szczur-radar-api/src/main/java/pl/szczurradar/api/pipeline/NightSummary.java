package pl.szczurradar.api.pipeline;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import pl.szczurradar.api.agents.assessor.validation.FailureClass;
import pl.szczurradar.api.pipeline.run.PipelineRun;
import pl.szczurradar.api.pipeline.run.PipelineRunRepository;
import pl.szczurradar.api.pipeline.run.PipelineRunSource;
import pl.szczurradar.api.pipeline.run.PipelineRunSourceRepository;
import pl.szczurradar.api.pipeline.run.RunKind;
import pl.szczurradar.api.pipeline.run.RunStatus;
import pl.szczurradar.api.pipeline.run.SourceFetchStatus;
import pl.szczurradar.api.posts.AssessmentQuality;
import pl.szczurradar.api.posts.AssessmentRepository;
import pl.szczurradar.api.posts.PostRepository;
import pl.szczurradar.api.posts.QualityCrossing;
import pl.szczurradar.api.posts.QualityStatus;
import pl.szczurradar.api.posts.QualityWindow;
import pl.szczurradar.api.posts.SourceAssessmentTotals;
import pl.szczurradar.api.posts.SourceCount;
import pl.szczurradar.api.posts.SourceQuality;
import pl.szczurradar.api.sources.Source;

/**
 * The night's summary for the operator (S-34): what the runs of one night did, per source, in a few lines
 * of text the daily cron asks for after its runs ({@code POST /internal/pipeline-runs/nightly-summary},
 * raised as {@code AlertEvent.NIGHT_SUMMARY}, also with nothing dead). It only reads, in one read-only
 * transaction on one snapshot ({@code REPEATABLE READ}), so the counters of a run still running agree.
 *
 * <pre>
 * Night 2026-11-11 (range 2026-11-09…2026-11-10): retry partial, range partial; errors 0, skipped 0
 * wykop: fetched 849, skipped 0, new 394, leftovers 10; assessed 391, failed 13, dead 0; tokens in 1200013 (cached 1148332), out 340550
 * reddit: fetched 105, skipped 0, new 49, leftovers 18; assessed 55, failed 12, dead 2; tokens in 245889 (cached 213594), out 197555
 * wykop quality 7d (2026-11-05…2026-11-11): failed 2.6% of 3134 attempts (schema_violation 0.3%, invalid_reference 0.1%, fabricated_evidence 2.0%, domain_invariant_violation 0.2%); threshold 5%
 * reddit quality 7d (2026-11-05…2026-11-11): failed 16.9% of 598 attempts (schema_violation 7.7%, fabricated_evidence 7.7%, domain_invariant_violation 1.5%); threshold 25%
 * Dead in admin runs since the previous night: none
 * /admin
 * </pre>
 *
 * <ul>
 *   <li><b>The night's line:</b> today in Warsaw ({@link PipelineRunService#ZONE}, from the {@link Clock}),
 *       the range of the night's {@link RunKind#RANGE} run (its first and last day; none without one),
 *       each run's kind and status in the order they were created ({@code retry} before {@code range}, as
 *       {@code /daily} queues them; a run still running or queued shows that status), and the sums of the
 *       runs' {@code processingErrors} and {@code skipped} counters — technical errors cannot be told per
 *       source.
 *   <li><b>A source's line,</b> in {@link Source} order, for every source with a result in the range run
 *       ({@link PipelineRunSource}) or with an assessment in the night's runs: {@code fetched} and
 *       {@code skipped} from its result — a source that {@link SourceFetchStatus#FAILED failed} shows
 *       {@code failed: <its error, cut to} {@value #MAX_SOURCE_ERROR} {@code characters>} and one that gave
 *       no post {@code empty} in their place, one without a result (no range run, or the run has not
 *       fetched it yet) {@code not fetched}; {@code new}: the posts the range run stored first;
 *       {@code leftovers}: the different posts the night's {@link RunKind#RETRY} run assessed (0 without
 *       one); {@code assessed} / {@code failed}: the {@code ok} / {@code failed} assessments of the night's
 *       runs; {@code dead}: the dead posts whose latest assessment is one of the night's runs (the run
 *       that killed them); {@code tokens}: the sums over the night's assessments, a missing count as 0.
 *   <li><b>A source's quality line</b> (S-35), in {@link Source} order, for every source with an attempt in
 *       the quality window of the night's date ({@link AssessmentQuality#window}: the last
 *       {@value AssessmentQuality#WINDOW_DAYS} Warsaw days, every run's attempts with a model call): the
 *       share of failed attempts of its attempts, each failure class with a failed one as its share of all
 *       the attempts (no brackets without a failed one), and the source's threshold, with
 *       {@code ; ABOVE THRESHOLD} when the share is above it; with fewer than
 *       {@value AssessmentQuality#MIN_ATTEMPTS} attempts only their number and {@code threshold not checked}.
 *       The sources that crossed their threshold tonight ({@link AssessmentQuality#crossed}) are
 *       {@link Night#crossed()}, raised apart as {@code AlertEvent.ASSESSMENT_QUALITY}
 *       ({@link Night#qualityAlert()}).
 *   <li><b>Dead in admin runs since the previous night:</b> always, {@code none} included — the dead posts
 *       whose latest assessment belongs to a run that is not the cron's ({@link PipelineRun#scheduled()}),
 *       created after the last cron run before this night's earliest run (from the beginning of the data
 *       when there is none) and before that earliest run; bounded by the runs' creation, never by now, so
 *       every admin's run falls between two nights and into exactly one summary, also across a skipped
 *       night. A run whose only reason is a dead post raises no alert of its own
 *       ({@code notifications/RunNotifications}): this line and {@code dead N} are where they show.
 *   <li>The last line is {@code /admin}.
 * </ul>
 *
 * <p>The text stays within ntfy's {@value #MAX_BYTES} bytes of a message by construction: one line per
 * source and one of its quality, a handful of sources, and a source's error cut; {@code NtfyAlerts} cuts the
 * body anyway.
 */
@Component
public class NightSummary {

	/** ntfy's bytes of a message ({@code NtfyAlerts.MAX_BODY_BYTES}); {@link Night#text()} stays within it. */
	public static final int MAX_BYTES = 4096;

	/** The most characters of a failed source's error in its line. */
	static final int MAX_SOURCE_ERROR = 200;

	private final PipelineRunRepository runs;

	private final PipelineRunSourceRepository sourceResults;

	private final PostRepository posts;

	private final AssessmentRepository assessments;

	private final AssessmentQuality quality;

	private final Clock clock;

	NightSummary(PipelineRunRepository runs, PipelineRunSourceRepository sourceResults, PostRepository posts,
			AssessmentRepository assessments, AssessmentQuality quality, Clock clock) {
		this.runs = runs;
		this.sourceResults = sourceResults;
		this.posts = posts;
		this.assessments = assessments;
		this.quality = quality;
		this.clock = clock;
	}

	/**
	 * The night of {@code runIds} (a repeated id counts once); empty when one of them is no run.
	 *
	 * @throws IllegalArgumentException when {@code runIds} is empty: a night has at least one run
	 */
	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public Optional<Night> of(Collection<UUID> runIds) {
		if (runIds.isEmpty()) {
			throw new IllegalArgumentException("A night's summary needs at least one run id");
		}
		List<UUID> ids = List.copyOf(new LinkedHashSet<>(runIds));
		List<PipelineRun> found = new ArrayList<>(runs.findAllById(ids));
		if (found.size() != ids.size()) {
			return Optional.empty();
		}
		found.sort(Comparator.comparing(PipelineRun::getCreatedAt).thenComparing(PipelineRun::getId));
		PipelineRun range = first(found, RunKind.RANGE);
		PipelineRun retry = first(found, RunKind.RETRY);
		Instant earliest = found.getFirst().getCreatedAt();

		Map<Source, PipelineRunSource> results = new EnumMap<>(Source.class);
		Map<Source, Long> newPosts = Map.of();
		if (range != null) {
			sourceResults.findByRunId(range.getId()).forEach(result -> results.put(result.getSource(), result));
			newPosts = SourceCount.bySource(posts.countFirstStoredBySource(range.getId()));
		}
		Map<Source, Long> leftovers = retry == null ? Map.of()
				: SourceCount.bySource(assessments.countDistinctPostsBySource(retry.getId()));
		Map<Source, SourceAssessmentTotals> totals = new EnumMap<>(Source.class);
		assessments.sumBySource(ids).forEach(total -> totals.put(total.source(), total));
		Map<Source, Long> dead = SourceCount.bySource(posts.countDeadKilledBySource(ids));

		EnumSet<Source> shown = EnumSet.noneOf(Source.class);
		shown.addAll(results.keySet());
		shown.addAll(totals.keySet());
		List<SourceLine> lines = new ArrayList<>(shown.size());
		for (Source source : shown) {
			PipelineRunSource result = results.get(source);
			SourceAssessmentTotals total = totals.get(source);
			lines.add(new SourceLine(source, result == null ? null : result.getStatus(),
					result == null ? 0 : result.getFetched(), result == null ? 0 : result.getSkipped(),
					result == null ? null : result.getError(), newPosts.getOrDefault(source, 0L),
					leftovers.getOrDefault(source, 0L), total == null ? 0 : total.ok(), total == null ? 0 : total.failed(),
					dead.getOrDefault(source, 0L), total == null ? 0 : total.inputTokens(),
					total == null ? 0 : total.cachedTokens(), total == null ? 0 : total.outputTokens()));
		}

		// Between the previous night's last cron run and this night's first: the admin's runs, by creation.
		Instant previousNight = runs.findLastScheduledCreationBefore(earliest).orElse(Instant.EPOCH);
		Map<Source, Long> deadInAdminRuns = SourceCount
			.bySource(posts.countDeadKilledBySource(runs.findAdminRunIdsCreatedBetween(previousNight, earliest)));

		// The quality window of the night's date and the sources that crossed their threshold tonight (S-35).
		Instant now = clock.instant();
		LocalDate date = LocalDate.ofInstant(now, PipelineRunService.ZONE);
		QualityWindow window = quality.window(date, PipelineRunService.ZONE);
		List<QualityCrossing> crossed = quality.crossed(window, now, PipelineRunService.ZONE);

		return Optional.of(new Night(date,
				range == null ? null : day(range.getRangeFrom()), range == null ? null : day(range.getRangeTo()),
				found.stream().map(run -> new Run(run.getId(), run.getKind(), run.getStatus())).toList(),
				found.stream().mapToInt(PipelineRun::getProcessingErrors).sum(),
				found.stream().mapToInt(PipelineRun::getSkipped).sum(), lines, deadInAdminRuns, window, crossed));
	}

	private static @Nullable PipelineRun first(List<PipelineRun> runs, RunKind kind) {
		return runs.stream().filter(run -> run.getKind() == kind).findFirst().orElse(null);
	}

	private static @Nullable LocalDate day(@Nullable Instant instant) {
		return instant == null ? null : LocalDate.ofInstant(instant, PipelineRunService.ZONE);
	}

	/**
	 * One night, ready to be written ({@link #text()}).
	 *
	 * @param date today in Warsaw, the night's date
	 * @param rangeFrom the first day of the night's range run; {@code null} without one
	 * @param rangeTo the last day of the night's range run; {@code null} without one
	 * @param runs the night's runs in the order they were created
	 * @param processingErrors the runs' technical errors, summed
	 * @param skipped the runs' skipped elements, summed
	 * @param sources a line per source; kept in {@link Source} order whatever order they come in
	 * @param deadInAdminRuns the dead posts of the admin's runs since the previous night, per source
	 * @param quality the quality window of {@code date} (S-35), a line per source in it
	 * @param crossed the sources that crossed their threshold tonight, in {@link Source} order; empty when none
	 */
	public record Night(LocalDate date, @Nullable LocalDate rangeFrom, @Nullable LocalDate rangeTo, List<Run> runs,
			int processingErrors, int skipped, List<SourceLine> sources, Map<Source, Long> deadInAdminRuns,
			QualityWindow quality, List<QualityCrossing> crossed) {

		public Night {
			runs = List.copyOf(runs);
			crossed = List.copyOf(crossed);
			sources = sources.stream().sorted(Comparator.comparing(SourceLine::source)).toList();
			Map<Source, Long> ordered = new EnumMap<>(Source.class);
			ordered.putAll(deadInAdminRuns);
			deadInAdminRuns = Map.copyOf(ordered);
		}

		/**
		 * The run the summary is raised for: the range run, else the first — so a repeated request within
		 * the queue's repeat window is one message.
		 */
		public UUID alertRunId() {
			return runs.stream().filter(run -> run.kind() == RunKind.RANGE).findFirst().orElse(runs.getFirst()).id();
		}

		/** The summary's text, the lines joined with {@code \n}; see the class. */
		public String text() {
			StringBuilder text = new StringBuilder("Night ").append(date);
			if (rangeFrom != null && rangeTo != null) {
				text.append(" (range ").append(rangeFrom).append('…').append(rangeTo).append(')');
			}
			text.append(": ")
				.append(runs.stream()
					.map(run -> run.kind().value() + " " + run.status().value())
					.collect(Collectors.joining(", ")))
				.append("; errors ")
				.append(processingErrors)
				.append(", skipped ")
				.append(skipped);
			for (SourceLine source : sources) {
				text.append('\n').append(source.text());
			}
			for (SourceQuality source : quality.sources()) {
				text.append('\n').append(qualityLine(source, quality));
			}
			text.append("\nDead in admin runs since the previous night: ");
			Map<Source, Long> dead = new EnumMap<>(Source.class);
			dead.putAll(deadInAdminRuns);
			if (dead.isEmpty()) {
				text.append("none");
			}
			else {
				text.append(dead.entrySet()
					.stream()
					.map(entry -> entry.getKey().value() + " " + entry.getValue())
					.collect(Collectors.joining(", ")));
			}
			return text.append("\n/admin").toString();
		}

		/**
		 * The detail of {@code AlertEvent.ASSESSMENT_QUALITY}; empty when no source crossed its threshold
		 * tonight. Per source that crossed, its share tonight with the window, its threshold, what the night
		 * before saw and its classes; the sources joined with {@code ; }, the last part {@code /admin}:
		 *
		 * <pre>
		 * wykop: failed 9.1% of 110 attempts in 7 days (2026-11-02…2026-11-08), above its threshold 5% (the day before: 0.0% of 100 attempts) (fabricated_evidence 9.1%); /admin
		 * </pre>
		 */
		public Optional<String> qualityAlert() {
			if (crossed.isEmpty()) {
				return Optional.empty();
			}
			return Optional.of(crossed.stream()
				.map(crossing -> crossingText(crossing, quality))
				.collect(Collectors.joining("; ")) + "; /admin");
		}

	}

	/** A source's line of the quality window; see the class. */
	static String qualityLine(SourceQuality source, QualityWindow window) {
		StringBuilder line = new StringBuilder(source.source().value()).append(" quality ")
			.append(AssessmentQuality.WINDOW_DAYS)
			.append("d (")
			.append(window.from())
			.append('…')
			.append(window.to())
			.append("): ");
		if (source.status() == QualityStatus.NOT_ENOUGH_ATTEMPTS) {
			return line.append(source.attempts())
				.append(" attempts, fewer than ")
				.append(AssessmentQuality.MIN_ATTEMPTS)
				.append(", threshold not checked")
				.toString();
		}
		line.append("failed ")
			.append(AssessmentQuality.percent(source.failed(), source.attempts()))
			.append(" of ")
			.append(source.attempts())
			.append(" attempts");
		if (source.failed() > 0) {
			line.append(" (").append(classes(source)).append(')');
		}
		line.append("; threshold ").append(source.thresholdPercent()).append('%');
		if (source.status() == QualityStatus.ABOVE_THRESHOLD) {
			line.append("; ABOVE THRESHOLD");
		}
		return line.toString();
	}

	/** One source of {@link Night#qualityAlert()}. */
	private static String crossingText(QualityCrossing crossing, QualityWindow window) {
		SourceQuality tonight = crossing.tonight();
		SourceQuality before = crossing.before();
		StringBuilder text = new StringBuilder(tonight.source().value()).append(": failed ")
			.append(AssessmentQuality.percent(tonight.failed(), tonight.attempts()))
			.append(" of ")
			.append(tonight.attempts())
			.append(" attempts in ")
			.append(AssessmentQuality.WINDOW_DAYS)
			.append(" days (")
			.append(window.from())
			.append('…')
			.append(window.to())
			.append("), above its threshold ")
			.append(tonight.thresholdPercent())
			.append("% (the day before: ");
		long attemptsBefore = before == null ? 0 : before.attempts();
		if (before == null || before.status() == QualityStatus.NOT_ENOUGH_ATTEMPTS) {
			text.append(attemptsBefore).append(" attempts, fewer than ").append(AssessmentQuality.MIN_ATTEMPTS);
		}
		else {
			text.append(AssessmentQuality.percent(before.failed(), before.attempts()))
				.append(" of ")
				.append(before.attempts())
				.append(" attempts");
		}
		return text.append(") (").append(classes(tonight)).append(')').toString();
	}

	/** The source's failure classes with a failed attempt, each as its share of all the attempts. */
	private static String classes(SourceQuality source) {
		return source.classes()
			.entrySet()
			.stream()
			.map(entry -> classShare(entry.getKey(), entry.getValue(), source.attempts()))
			.collect(Collectors.joining(", "));
	}

	private static String classShare(FailureClass failureClass, long failed, long attempts) {
		return failureClass.value() + " " + AssessmentQuality.percent(failed, attempts);
	}

	/** One of the night's runs. */
	public record Run(UUID id, RunKind kind, RunStatus status) {
	}

	/**
	 * One source's line.
	 *
	 * @param fetchStatus the source's result in the range run; {@code null} without one ({@code not fetched})
	 * @param fetched the elements it fetched (only with a result)
	 * @param skipped the fetched elements that could not become a post (only with a result)
	 * @param error why it failed; only a {@link SourceFetchStatus#FAILED failed} one
	 * @param newPosts the posts the range run stored first
	 * @param leftovers the different posts the retry run assessed
	 * @param assessed the {@code ok} assessments of the night's runs
	 * @param failed the {@code failed} assessments of the night's runs
	 * @param dead the dead posts the night's runs killed
	 * @param inputTokens the input tokens of the night's assessments
	 * @param cachedTokens of them, the cached ones
	 * @param outputTokens the output tokens of the night's assessments
	 */
	public record SourceLine(Source source, @Nullable SourceFetchStatus fetchStatus, int fetched, int skipped,
			@Nullable String error, long newPosts, long leftovers, long assessed, long failed, long dead,
			long inputTokens, long cachedTokens, long outputTokens) {

		String text() {
			StringBuilder line = new StringBuilder(source.value()).append(": ");
			if (fetchStatus == null) {
				line.append("not fetched");
			}
			else {
				switch (fetchStatus) {
					case SUCCEEDED -> line.append("fetched ").append(fetched).append(", skipped ").append(skipped);
					case EMPTY -> line.append("empty");
					case FAILED -> line.append("failed: ").append(cutError(error));
				}
			}
			return line.append(", new ")
				.append(newPosts)
				.append(", leftovers ")
				.append(leftovers)
				.append("; assessed ")
				.append(assessed)
				.append(", failed ")
				.append(failed)
				.append(", dead ")
				.append(dead)
				.append("; tokens in ")
				.append(inputTokens)
				.append(" (cached ")
				.append(cachedTokens)
				.append("), out ")
				.append(outputTokens)
				.toString();
		}

		/** The error on one line, at most {@value #MAX_SOURCE_ERROR} characters; {@code unknown} without one. */
		private static String cutError(@Nullable String error) {
			if (error == null || error.isBlank()) {
				return "unknown";
			}
			String text = error.strip().replaceAll("\\s*[\\r\\n]+\\s*", " ");
			return text.length() <= MAX_SOURCE_ERROR ? text : text.substring(0, MAX_SOURCE_ERROR - 1) + "…";
		}

	}

}
