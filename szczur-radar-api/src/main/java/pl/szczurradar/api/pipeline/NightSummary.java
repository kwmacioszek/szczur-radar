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
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

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
import pl.szczurradar.api.posts.LanguageCrossing;
import pl.szczurradar.api.posts.LanguageWindow;
import pl.szczurradar.api.posts.PostRepository;
import pl.szczurradar.api.posts.QualityCrossing;
import pl.szczurradar.api.posts.QualityStatus;
import pl.szczurradar.api.posts.QualityWindow;
import pl.szczurradar.api.posts.SourceAssessmentTotals;
import pl.szczurradar.api.posts.SourceCount;
import pl.szczurradar.api.posts.SourceLanguage;
import pl.szczurradar.api.posts.SourceQuality;
import pl.szczurradar.api.sources.Source;

/**
 * The night's summary for the operator (S-34): what the runs of one night did, per source, in a few lines
 * of text the daily cron asks for after its runs ({@code POST /internal/pipeline-runs/nightly-summary},
 * raised as {@code AlertEvent.NIGHT_SUMMARY}, also with nothing dead). It only reads, in one read-only
 * transaction on one snapshot ({@code REPEATABLE READ}), so the counters of a run still running agree; the
 * quality window (S-35) and then the language window (S-36) are read before it, each in a read-only
 * transaction of its own, so a failure of one costs only its own lines, never the other's or the summary
 * (below).
 *
 * <pre>
 * Night 2026-11-11 (range 2026-11-09…2026-11-10): retry partial, range partial; errors 0, skipped 0
 * wykop: fetched 849, skipped 0, new 394, leftovers 10; assessed 391, failed 13, dead 0; tokens in 1200013 (cached 1148332), out 340550
 * reddit: fetched 105, skipped 0, new 49, leftovers 18; assessed 55, failed 12, dead 2; tokens in 245889 (cached 213594), out 197555
 * wykop quality 7d (2026-11-05…2026-11-11): failed 2.6% of 3134 attempts (schema_violation 0.3%, invalid_reference 0.1%, fabricated_evidence 2.0%, domain_invariant_violation 0.2%); threshold 5%
 * reddit quality 7d (2026-11-05…2026-11-11): failed 16.9% of 598 attempts (schema_violation 7.7%, fabricated_evidence 7.7%, domain_invariant_violation 1.5%); threshold 25%
 * wykop language 7d (2026-11-05…2026-11-11): other language 0.3% of 3476 theses; threshold 2%
 * reddit language 7d (2026-11-05…2026-11-11): other language 16.1% of 2857 theses; threshold 30%
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
 *       ({@link Night#qualityAlert()}). When the window cannot be read (any exception: an ERROR line with
 *       it in the API log), one line {@code quality 7d (<from>…<to>): unavailable (<the exception's
 *       class>)} stands for them, nothing crossed, and the summary goes out as without S-35.
 *   <li><b>A source's language line</b> (S-36), after every quality line, in {@link Source} order, for every
 *       source with a thesis in the language window of the night's date ({@link AssessmentQuality#languageWindow}:
 *       the same days, the theses of successful attempts with a model call): the share of theses with evidence
 *       of another language than their post's, their number and the source's threshold, with
 *       {@code ; ABOVE THRESHOLD} when the share is above it; with fewer than {@value AssessmentQuality#MIN_THESES}
 *       theses only their number and {@code threshold not checked}. The sources that crossed tonight
 *       ({@link AssessmentQuality#languageCrossed}) are {@link Night#languageCrossed()}, in the same
 *       {@code ASSESSMENT_QUALITY} as the failed attempts' (one event a night). When the theses cannot be read,
 *       one line {@code language 7d (<from>…<to>): unavailable (<the exception's class>)} stands for them,
 *       nothing crossed; the quality lines stay as they are.
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
 * source, one of its quality and one of its language, a handful of sources, and a source's error cut;
 * {@code NtfyAlerts} cuts the body anyway.
 */
@Component
public class NightSummary {

	private static final Logger log = LoggerFactory.getLogger(NightSummary.class);

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

	/** The summary's one snapshot: read-only, {@code REPEATABLE READ}. */
	private final TransactionTemplate snapshot;

	/**
	 * The read-only transaction of a measure, apart from the snapshot: the quality window and the language window
	 * each run in one of their own, so a failed query of one does not abort the other's.
	 */
	private final TransactionTemplate measureRead;

	NightSummary(PipelineRunRepository runs, PipelineRunSourceRepository sourceResults, PostRepository posts,
			AssessmentRepository assessments, AssessmentQuality quality, Clock clock,
			PlatformTransactionManager transactions) {
		this.runs = runs;
		this.sourceResults = sourceResults;
		this.posts = posts;
		this.assessments = assessments;
		this.quality = quality;
		this.clock = clock;
		this.snapshot = new TransactionTemplate(transactions);
		this.snapshot.setReadOnly(true);
		this.snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
		this.measureRead = new TransactionTemplate(transactions);
		this.measureRead.setReadOnly(true);
	}

	/**
	 * The night of {@code runIds} (a repeated id counts once); empty when one of them is no run. The quality
	 * window is read first, then the language window, each in its own transaction: when one fails, the night has
	 * the line {@code unavailable} in its place ({@link Night#qualityUnavailable()},
	 * {@link Night#languageUnavailable()}) and is still returned.
	 *
	 * @throws IllegalArgumentException when {@code runIds} is empty: a night has at least one run
	 */
	public Optional<Night> of(Collection<UUID> runIds) {
		if (runIds.isEmpty()) {
			throw new IllegalArgumentException("A night's summary needs at least one run id");
		}
		Instant now = clock.instant();
		LocalDate date = LocalDate.ofInstant(now, PipelineRunService.ZONE);
		NightQuality nightQuality = quality(date, now);
		NightLanguage nightLanguage = language(date, now);
		return Objects.requireNonNull(snapshot.execute(status -> night(runIds, date, nightQuality, nightLanguage)));
	}

	/**
	 * The quality window of the night's date and the sources that crossed their threshold tonight (S-35); on
	 * any exception an empty window of the same days, nothing crossed, and the exception's class — the summary
	 * goes out without its quality lines rather than not at all (S-34: every night).
	 */
	private NightQuality quality(LocalDate date, Instant now) {
		try {
			return Objects.requireNonNull(measureRead.execute(status -> {
				QualityWindow window = quality.window(date, PipelineRunService.ZONE);
				return new NightQuality(window, quality.crossed(window, now, PipelineRunService.ZONE), null);
			}));
		}
		catch (RuntimeException ex) {
			log.error("The quality window of the night {} could not be read; the night's summary goes without it",
					date, ex);
			return new NightQuality(new QualityWindow(date.minusDays(AssessmentQuality.WINDOW_DAYS - 1), date,
					List.of()), List.of(), ex.getClass().getSimpleName());
		}
	}

	/** The quality part of a night: its window, the sources that crossed, or why it is unavailable. */
	private record NightQuality(QualityWindow window, List<QualityCrossing> crossed, @Nullable String unavailable) {
	}

	/**
	 * The language window of the night's date and the sources that crossed their threshold tonight (S-36), as
	 * {@link #quality}: on any exception an empty window of the same days, nothing crossed, and the exception's
	 * class — only the language lines are lost.
	 */
	private NightLanguage language(LocalDate date, Instant now) {
		try {
			return Objects.requireNonNull(measureRead.execute(status -> {
				LanguageWindow window = quality.languageWindow(date, PipelineRunService.ZONE);
				return new NightLanguage(window, quality.languageCrossed(window, now, PipelineRunService.ZONE), null);
			}));
		}
		catch (RuntimeException ex) {
			log.error("The language window of the night {} could not be read; the night's summary goes without it",
					date, ex);
			return new NightLanguage(new LanguageWindow(date.minusDays(AssessmentQuality.WINDOW_DAYS - 1), date,
					List.of()), List.of(), ex.getClass().getSimpleName());
		}
	}

	/** The language part of a night: its window, the sources that crossed, or why it is unavailable. */
	private record NightLanguage(LanguageWindow window, List<LanguageCrossing> crossed, @Nullable String unavailable) {
	}

	/** The night inside the snapshot; {@code date} and both measures were read before it. */
	private Optional<Night> night(Collection<UUID> runIds, LocalDate date, NightQuality nightQuality,
			NightLanguage nightLanguage) {
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

		return Optional.of(new Night(date,
				range == null ? null : day(range.getRangeFrom()), range == null ? null : day(range.getRangeTo()),
				found.stream().map(run -> new Run(run.getId(), run.getKind(), run.getStatus())).toList(),
				found.stream().mapToInt(PipelineRun::getProcessingErrors).sum(),
				found.stream().mapToInt(PipelineRun::getSkipped).sum(), lines, deadInAdminRuns, nightQuality.window(),
				nightQuality.crossed(), nightQuality.unavailable(), nightLanguage.window(), nightLanguage.crossed(),
				nightLanguage.unavailable()));
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
	 * @param quality the quality window of {@code date} (S-35), a line per source in it; without sources when
	 *     {@code qualityUnavailable}
	 * @param crossed the sources that crossed their threshold tonight, in {@link Source} order; empty when none
	 * @param qualityUnavailable the class of the exception the quality window could not be read with, which
	 *     makes the quality one line {@code unavailable}; {@code null} when it was read
	 * @param language the language window of {@code date} (S-36), a line per source in it; without sources when
	 *     {@code languageUnavailable}
	 * @param languageCrossed the sources whose share of theses in another language crossed their threshold
	 *     tonight, in {@link Source} order; empty when none
	 * @param languageUnavailable the class of the exception the language window could not be read with, which
	 *     makes the language one line {@code unavailable}; {@code null} when it was read
	 */
	public record Night(LocalDate date, @Nullable LocalDate rangeFrom, @Nullable LocalDate rangeTo, List<Run> runs,
			int processingErrors, int skipped, List<SourceLine> sources, Map<Source, Long> deadInAdminRuns,
			QualityWindow quality, List<QualityCrossing> crossed, @Nullable String qualityUnavailable,
			LanguageWindow language, List<LanguageCrossing> languageCrossed, @Nullable String languageUnavailable) {

		public Night {
			if (qualityUnavailable != null && (!quality.sources().isEmpty() || !crossed.isEmpty())) {
				throw new IllegalArgumentException("An unavailable quality window has no source and nothing crossed");
			}
			if (languageUnavailable != null && (!language.sources().isEmpty() || !languageCrossed.isEmpty())) {
				throw new IllegalArgumentException("An unavailable language window has no source and nothing crossed");
			}
			runs = List.copyOf(runs);
			crossed = List.copyOf(crossed);
			languageCrossed = List.copyOf(languageCrossed);
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
			if (qualityUnavailable != null) {
				text.append("\nquality ")
					.append(AssessmentQuality.WINDOW_DAYS)
					.append("d (")
					.append(quality.from())
					.append('…')
					.append(quality.to())
					.append("): unavailable (")
					.append(qualityUnavailable)
					.append(')');
			}
			for (SourceQuality source : quality.sources()) {
				text.append('\n').append(qualityLine(source, quality));
			}
			if (languageUnavailable != null) {
				text.append("\nlanguage ")
					.append(AssessmentQuality.WINDOW_DAYS)
					.append("d (")
					.append(language.from())
					.append('…')
					.append(language.to())
					.append("): unavailable (")
					.append(languageUnavailable)
					.append(')');
			}
			for (SourceLanguage source : language.sources()) {
				text.append('\n').append(languageLine(source, language));
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
		 * The detail of {@code AlertEvent.ASSESSMENT_QUALITY}; empty when no source crossed a threshold of either
		 * measure tonight. First per source whose share of failed attempts crossed: its share tonight with the
		 * window, its threshold, what the night before saw and its classes; then per source whose share of theses
		 * in another language crossed (S-36): its share with the window, its threshold and what the night before
		 * saw; each group in {@link Source} order, the parts joined with {@code ; }, the last part {@code /admin}:
		 *
		 * <pre>
		 * wykop: failed 9.1% of 110 attempts in 7 days (2026-11-02…2026-11-08), above its threshold 5% (the day before: 0.0% of 100 attempts) (fabricated_evidence 9.1%); reddit: other language 31.0% of 600 theses in 7 days (2026-11-02…2026-11-08), above its threshold 30% (the day before: 498 theses, fewer than 500); /admin
		 * </pre>
		 */
		public Optional<String> qualityAlert() {
			if (crossed.isEmpty() && languageCrossed.isEmpty()) {
				return Optional.empty();
			}
			List<String> parts = new ArrayList<>(crossed.size() + languageCrossed.size() + 1);
			crossed.forEach(crossing -> parts.add(crossingText(crossing, quality)));
			languageCrossed.forEach(crossing -> parts.add(languageCrossingText(crossing, language)));
			parts.add("/admin");
			return Optional.of(String.join("; ", parts));
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
		if (source.status() == QualityStatus.NOT_ENOUGH_DATA) {
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

	/** A source's line of the language window; see the class. */
	static String languageLine(SourceLanguage source, LanguageWindow window) {
		StringBuilder line = new StringBuilder(source.source().value()).append(" language ")
			.append(AssessmentQuality.WINDOW_DAYS)
			.append("d (")
			.append(window.from())
			.append('…')
			.append(window.to())
			.append("): ");
		if (source.status() == QualityStatus.NOT_ENOUGH_DATA) {
			return line.append(source.theses())
				.append(" theses, fewer than ")
				.append(AssessmentQuality.MIN_THESES)
				.append(", threshold not checked")
				.toString();
		}
		line.append("other language ")
			.append(AssessmentQuality.percent(source.otherLanguage(), source.theses()))
			.append(" of ")
			.append(source.theses())
			.append(" theses; threshold ")
			.append(source.thresholdPercent())
			.append('%');
		if (source.status() == QualityStatus.ABOVE_THRESHOLD) {
			line.append("; ABOVE THRESHOLD");
		}
		return line.toString();
	}

	/** One source of {@link Night#qualityAlert()} whose share of theses in another language crossed. */
	private static String languageCrossingText(LanguageCrossing crossing, LanguageWindow window) {
		SourceLanguage tonight = crossing.tonight();
		SourceLanguage before = crossing.before();
		StringBuilder text = new StringBuilder(tonight.source().value()).append(": other language ")
			.append(AssessmentQuality.percent(tonight.otherLanguage(), tonight.theses()))
			.append(" of ")
			.append(tonight.theses())
			.append(" theses in ")
			.append(AssessmentQuality.WINDOW_DAYS)
			.append(" days (")
			.append(window.from())
			.append('…')
			.append(window.to())
			.append("), above its threshold ")
			.append(tonight.thresholdPercent())
			.append("% (the day before: ");
		long thesesBefore = before == null ? 0 : before.theses();
		if (before == null || before.status() == QualityStatus.NOT_ENOUGH_DATA) {
			text.append(thesesBefore).append(" theses, fewer than ").append(AssessmentQuality.MIN_THESES);
		}
		else {
			text.append(AssessmentQuality.percent(before.otherLanguage(), before.theses()))
				.append(" of ")
				.append(before.theses())
				.append(" theses");
		}
		return text.append(')').toString();
	}

	/** One source of {@link Night#qualityAlert()} whose share of failed attempts crossed. */
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
		if (before == null || before.status() == QualityStatus.NOT_ENOUGH_DATA) {
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
