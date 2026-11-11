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

import pl.szczurradar.api.pipeline.run.PipelineRun;
import pl.szczurradar.api.pipeline.run.PipelineRunRepository;
import pl.szczurradar.api.pipeline.run.PipelineRunSource;
import pl.szczurradar.api.pipeline.run.PipelineRunSourceRepository;
import pl.szczurradar.api.pipeline.run.RunKind;
import pl.szczurradar.api.pipeline.run.RunStatus;
import pl.szczurradar.api.pipeline.run.SourceFetchStatus;
import pl.szczurradar.api.posts.AssessmentRepository;
import pl.szczurradar.api.posts.PostRepository;
import pl.szczurradar.api.posts.SourceAssessmentTotals;
import pl.szczurradar.api.posts.SourceCount;
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
 * source, a handful of sources, and a source's error cut; {@code NtfyAlerts} cuts the body anyway.
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

	private final Clock clock;

	NightSummary(PipelineRunRepository runs, PipelineRunSourceRepository sourceResults, PostRepository posts,
			AssessmentRepository assessments, Clock clock) {
		this.runs = runs;
		this.sourceResults = sourceResults;
		this.posts = posts;
		this.assessments = assessments;
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

		return Optional.of(new Night(LocalDate.ofInstant(clock.instant(), PipelineRunService.ZONE),
				range == null ? null : day(range.getRangeFrom()), range == null ? null : day(range.getRangeTo()),
				found.stream().map(run -> new Run(run.getId(), run.getKind(), run.getStatus())).toList(),
				found.stream().mapToInt(PipelineRun::getProcessingErrors).sum(),
				found.stream().mapToInt(PipelineRun::getSkipped).sum(), lines, deadInAdminRuns));
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
	 */
	public record Night(LocalDate date, @Nullable LocalDate rangeFrom, @Nullable LocalDate rangeTo, List<Run> runs,
			int processingErrors, int skipped, List<SourceLine> sources, Map<Source, Long> deadInAdminRuns) {

		public Night {
			runs = List.copyOf(runs);
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
