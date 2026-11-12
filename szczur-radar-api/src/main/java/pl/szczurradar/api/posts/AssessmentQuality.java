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

import org.springframework.stereotype.Component;

import pl.szczurradar.api.agents.assessor.validation.FailureClass;
import pl.szczurradar.api.sources.Source;

/**
 * The quality of the assessments per source (S-35, FR-033): the share of failed attempts with a model call
 * over a window of {@value #WINDOW_DAYS} calendar days, per failure class, against the source's threshold
 * ({@link Source#failureThresholdPercent()}). Read only; nothing here is stored.
 *
 * <ul>
 *   <li>The measure: every stored attempt with a model call, retries included and whoever's run (the cron's
 *       or an admin's); the prefilter's are left out ({@link AssessmentRepository#countModelAttemptsBySourceAndClass}).
 *   <li>The window of day {@code D}: the calendar days {@code [D−6, D]} in the caller's zone, as
 *       {@code [start of D−6, start of D+1)} — a day may have 23 or 25 hours. The zone comes from the
 *       caller ({@code pipeline/PipelineRunService.ZONE}): {@code posts/} imports nothing from
 *       {@code pipeline/}.
 *   <li>The rule ({@link #status}): fewer than {@value #MIN_ATTEMPTS} attempts → {@link
 *       QualityStatus#NOT_ENOUGH_ATTEMPTS}; else a share strictly above the threshold → {@link
 *       QualityStatus#ABOVE_THRESHOLD} (in integers, {@code failed × 100 > threshold × attempts}: 5.0 % at a
 *       threshold of 5 % is still {@link QualityStatus#OK}).
 *   <li>The crossing ({@link #crossed}): a source above its threshold tonight that was not above it in the
 *       window the previous night's summary saw, computed again from the data, with no stored state
 *       ({@link QualityCrossing}: both windows of the source).
 * </ul>
 */
@Component
public class AssessmentQuality {

	/** How many calendar days a window has. */
	public static final int WINDOW_DAYS = 7;

	/** The fewest attempts of a source in a window for its threshold to be checked. */
	public static final int MIN_ATTEMPTS = 100;

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
		Instant nightStart = startOf(night, zone);
		Instant summaryBefore = now.minus(PREVIOUS_NIGHT);
		Instant until = summaryBefore.isBefore(nightStart) ? summaryBefore : nightStart;
		List<SourceQuality> before = sources(
				assessments.countModelAttemptsBySourceAndClass(startOf(night.minusDays(WINDOW_DAYS), zone), until));
		return crossed(tonight.sources(), before);
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
		if (attempts < MIN_ATTEMPTS) {
			return QualityStatus.NOT_ENOUGH_ATTEMPTS;
		}
		return failed * 100 > thresholdPercent * attempts ? QualityStatus.ABOVE_THRESHOLD : QualityStatus.OK;
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
	 * The sources of {@code tonight} above their threshold that are not above it in {@code before}, each with
	 * its row of {@code before} ({@code null} without one).
	 */
	static List<QualityCrossing> crossed(List<SourceQuality> tonight, List<SourceQuality> before) {
		List<QualityCrossing> crossed = new ArrayList<>();
		for (SourceQuality quality : tonight) {
			if (quality.status() != QualityStatus.ABOVE_THRESHOLD) {
				continue;
			}
			SourceQuality earlier = before.stream()
				.filter(row -> row.source() == quality.source())
				.findFirst()
				.orElse(null);
			if (earlier == null || earlier.status() != QualityStatus.ABOVE_THRESHOLD) {
				crossed.add(new QualityCrossing(quality, earlier));
			}
		}
		return List.copyOf(crossed);
	}

	private static Instant startOf(LocalDate day, ZoneId zone) {
		return day.atStartOfDay(zone).toInstant();
	}

}
