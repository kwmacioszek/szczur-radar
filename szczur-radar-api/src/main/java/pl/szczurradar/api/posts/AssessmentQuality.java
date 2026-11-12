package pl.szczurradar.api.posts;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import pl.szczurradar.api.agents.assessor.validation.FailureClass;
import pl.szczurradar.api.sources.Source;
import pl.szczurradar.api.text.LanguageEvidence;

/**
 * The quality of the assessments per source (FR-033), two measures over the same window of {@value #WINDOW_DAYS}
 * calendar days, each against its own threshold of the source. Read only; nothing here is stored.
 *
 * <ul>
 *   <li>Failed attempts (S-35, {@link #window}, {@link #crossed}): the share of failed attempts with a model call,
 *       per failure class, against {@link Source#failureThresholdPercent()} — every stored attempt with a model
 *       call, retries included and whoever's run (the cron's or an admin's); the prefilter's are left out
 *       ({@link AssessmentRepository#countModelAttemptsBySourceAndClass}).
 *   <li>Theses in another language (S-36, {@link #languageWindow}, {@link #languageCrossed}): the share of theses
 *       of successful attempts with a model call ({@link AssessmentRepository#findModelStatements}) with evidence
 *       of another language than their post's ({@link LanguageEvidence}; a thesis without evidence is no
 *       violation), against {@link Source#otherLanguageThresholdPercent()}. Its own read, so a failure of one
 *       measure leaves the other one.
 *   <li>The window of day {@code D}: the calendar days {@code [D−6, D]} in the caller's zone, as
 *       {@code [start of D−6, start of D+1)} — a day may have 23 or 25 hours. The zone comes from the
 *       caller ({@code pipeline/PipelineRunService.ZONE}): {@code posts/} imports nothing from
 *       {@code pipeline/}.
 *   <li>The rule ({@link #status}), one for both: below the measure's minimum ({@value #MIN_ATTEMPTS} attempts,
 *       {@value #MIN_THESES} theses) → {@link QualityStatus#NOT_ENOUGH_DATA}; else a share strictly above the
 *       threshold → {@link QualityStatus#ABOVE_THRESHOLD} (in integers, {@code part × 100 > threshold × whole}:
 *       5.0 % at a threshold of 5 % is still {@link QualityStatus#OK}).
 *   <li>The crossing, one for both: a source above its threshold tonight that was not above it in the window
 *       the previous night's summary saw, computed again from the data, with no stored state
 *       ({@link QualityCrossing}, {@link LanguageCrossing}: both windows of the source).
 * </ul>
 */
@Component
public class AssessmentQuality {

	/** How many calendar days a window has. */
	public static final int WINDOW_DAYS = 7;

	/** The fewest attempts of a source in a window for its threshold of failed attempts to be checked. */
	public static final int MIN_ATTEMPTS = 100;

	/** The fewest theses of a source in a window for its threshold of theses in another language to be checked. */
	public static final int MIN_THESES = 500;

	/** How long before this night's summary the previous night's was, give or take the nights' run times. */
	private static final Duration PREVIOUS_NIGHT = Duration.ofHours(24);

	private final AssessmentRepository assessments;

	public AssessmentQuality(AssessmentRepository assessments) {
		this.assessments = assessments;
	}

	/** The window {@code [to − 6, to]} of calendar days in {@code zone}, per source with an attempt in it. */
	public QualityWindow window(LocalDate to, ZoneId zone) {
		LocalDate from = to.minusDays(WINDOW_DAYS - 1);
		return new QualityWindow(from, to, sources(assessments
			.countModelAttemptsBySourceAndClass(startOf(from, zone), startOf(to.plusDays(1), zone))));
	}

	/**
	 * The sources above their threshold in {@code tonight} that were not above it the night before — not
	 * enough attempts or no attempt then counts as not above. The night before is the window
	 * {@code [start of night − 7, min(start of night, now − 24 h))}: what the previous night's summary saw,
	 * so an admin's attempts of the day before (stored after that summary) count only tonight, and one bad
	 * episode raises one alarm. Tonight's window is the caller's ({@link #window}), so a night reads two
	 * windows, one query each.
	 *
	 * @param tonight the window of the night's calendar day ({@link QualityWindow#to()}) in {@code zone}
	 * @param now the caller's clock
	 * @param zone the calendar's zone
	 * @return the sources that crossed, each with what the night before saw of it, in {@link Source} order
	 */
	public List<QualityCrossing> crossed(QualityWindow tonight, Instant now, ZoneId zone) {
		LocalDate night = tonight.to();
		List<SourceQuality> before = sources(assessments.countModelAttemptsBySourceAndClass(
				startOf(night.minusDays(WINDOW_DAYS), zone), nightBeforeUntil(night, now, zone)));
		return crossed(tonight.sources(), before);
	}

	/**
	 * The language window {@code [to − 6, to]} of calendar days in {@code zone}, the same days as {@link #window},
	 * per source with a thesis in it.
	 */
	public LanguageWindow languageWindow(LocalDate to, ZoneId zone) {
		LocalDate from = to.minusDays(WINDOW_DAYS - 1);
		return new LanguageWindow(from, to,
				languages(assessments.findModelStatements(startOf(from, zone), startOf(to.plusDays(1), zone))));
	}

	/**
	 * The sources above their threshold of theses in another language in {@code tonight} that were not above it
	 * the night before, with the night before as in {@link #crossed}.
	 *
	 * @param tonight the language window of the night's calendar day ({@link LanguageWindow#to()}) in {@code zone}
	 * @param now the caller's clock
	 * @param zone the calendar's zone
	 * @return the sources that crossed, each with what the night before saw of it, in {@link Source} order
	 */
	public List<LanguageCrossing> languageCrossed(LanguageWindow tonight, Instant now, ZoneId zone) {
		LocalDate night = tonight.to();
		List<SourceLanguage> before = languages(assessments.findModelStatements(
				startOf(night.minusDays(WINDOW_DAYS), zone), nightBeforeUntil(night, now, zone)));
		return languageCrossed(tonight.sources(), before);
	}

	/**
	 * {@code part} of {@code whole} in percent with one decimal, half up, a dot and a percent sign, e.g.
	 * {@code 2.6%} for 258 of 10 000: the one way a share of the quality window is written.
	 */
	public static String percent(long part, long whole) {
		if (whole <= 0) {
			throw new IllegalArgumentException("A share needs a positive whole, got " + whole);
		}
		return BigDecimal.valueOf(part)
			.multiply(BigDecimal.valueOf(100))
			.divide(BigDecimal.valueOf(whole), 1, RoundingMode.HALF_UP)
			.toPlainString() + "%";
	}

	/** The state of a source's window with {@code attempts}, {@code failed} of them, at {@code thresholdPercent}. */
	static QualityStatus status(long attempts, long failed, int thresholdPercent) {
		return status(attempts, failed, thresholdPercent, MIN_ATTEMPTS);
	}

	/**
	 * The state of a measure's window with {@code part} of {@code whole} at {@code thresholdPercent}, checked from
	 * {@code minimum} on: the one rule of both measures.
	 */
	static QualityStatus status(long whole, long part, int thresholdPercent, int minimum) {
		if (whole < minimum) {
			return QualityStatus.NOT_ENOUGH_DATA;
		}
		return part * 100 > thresholdPercent * whole ? QualityStatus.ABOVE_THRESHOLD : QualityStatus.OK;
	}

	/** {@code counts} summed per source, each source with a row, in {@link Source} order. */
	static List<SourceQuality> sources(Collection<SourceClassCount> counts) {
		Map<Source, Map<FailureClass, Long>> classes = new EnumMap<>(Source.class);
		Map<Source, Long> attempts = new EnumMap<>(Source.class);
		for (SourceClassCount count : counts) {
			attempts.merge(count.source(), count.count(), Long::sum);
			Map<FailureClass, Long> ofSource = classes.computeIfAbsent(count.source(),
					source -> new EnumMap<>(FailureClass.class));
			FailureClass failureClass = count.failureClass();
			if (failureClass != null && count.count() > 0) {
				ofSource.merge(failureClass, count.count(), Long::sum);
			}
		}
		List<SourceQuality> sources = new ArrayList<>();
		attempts.forEach((source, total) -> {
			if (total > 0) {
				Map<FailureClass, Long> ofSource = classes.get(source);
				long failed = ofSource.values().stream().mapToLong(Long::longValue).sum();
				int threshold = source.failureThresholdPercent();
				sources.add(new SourceQuality(source, total, failed, ofSource, threshold,
						status(total, failed, threshold)));
			}
		});
		return sources;
	}

	/**
	 * {@code statements} counted per source, in {@link Source} order: a thesis is in another language when
	 * {@link LanguageEvidence} finds a language in it other than its post's; one without evidence is not.
	 */
	static List<SourceLanguage> languages(Collection<PostStatement> statements) {
		Map<Source, Long> theses = new EnumMap<>(Source.class);
		Map<Source, Long> other = new EnumMap<>(Source.class);
		for (PostStatement statement : statements) {
			theses.merge(statement.source(), 1L, Long::sum);
			boolean otherLanguage = LanguageEvidence.of(statement.statement())
				.filter(language -> language != statement.postLanguage())
				.isPresent();
			other.merge(statement.source(), otherLanguage ? 1L : 0L, Long::sum);
		}
		List<SourceLanguage> sources = new ArrayList<>();
		theses.forEach((source, total) -> {
			long otherLanguage = other.get(source);
			int threshold = source.otherLanguageThresholdPercent();
			sources.add(new SourceLanguage(source, total, otherLanguage, threshold,
					status(total, otherLanguage, threshold, MIN_THESES)));
		});
		return sources;
	}

	/**
	 * The sources of {@code tonight} above their threshold that are not above it in {@code before}, each with
	 * its row of {@code before} ({@code null} without one).
	 */
	static List<QualityCrossing> crossed(List<SourceQuality> tonight, List<SourceQuality> before) {
		return crossed(tonight, before, SourceQuality::source, SourceQuality::status, QualityCrossing::new);
	}

	/** {@link #crossed(List, List)} of the language measure. */
	static List<LanguageCrossing> languageCrossed(List<SourceLanguage> tonight, List<SourceLanguage> before) {
		return crossed(tonight, before, SourceLanguage::source, SourceLanguage::status, LanguageCrossing::new);
	}

	/** The one crossing rule of both measures, over their rows. */
	private static <R, C> List<C> crossed(List<R> tonight, List<R> before, Function<R, Source> source,
			Function<R, QualityStatus> status, BiFunction<R, @Nullable R, C> crossing) {
		List<C> crossed = new ArrayList<>();
		for (R row : tonight) {
			if (status.apply(row) != QualityStatus.ABOVE_THRESHOLD) {
				continue;
			}
			@Nullable R earlier = before.stream()
				.filter(candidate -> source.apply(candidate) == source.apply(row))
				.findFirst()
				.orElse(null);
			if (earlier == null || status.apply(earlier) != QualityStatus.ABOVE_THRESHOLD) {
				crossed.add(crossing.apply(row, earlier));
			}
		}
		return List.copyOf(crossed);
	}

	/** The end of the night before's window: {@code min(start of night, now − 24 h)}. */
	private static Instant nightBeforeUntil(LocalDate night, Instant now, ZoneId zone) {
		Instant nightStart = startOf(night, zone);
		Instant summaryBefore = now.minus(PREVIOUS_NIGHT);
		return summaryBefore.isBefore(nightStart) ? summaryBefore : nightStart;
	}

	private static Instant startOf(LocalDate day, ZoneId zone) {
		return day.atStartOfDay(zone).toInstant();
	}

}
