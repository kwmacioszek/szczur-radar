package pl.szczurradar.api.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import pl.szczurradar.api.agents.assessor.validation.FailureClass;
import pl.szczurradar.api.pipeline.NightSummary.Night;
import pl.szczurradar.api.pipeline.NightSummary.Run;
import pl.szczurradar.api.pipeline.NightSummary.SourceLine;
import pl.szczurradar.api.pipeline.run.RunKind;
import pl.szczurradar.api.pipeline.run.RunStatus;
import pl.szczurradar.api.pipeline.run.SourceFetchStatus;
import pl.szczurradar.api.posts.QualityCrossing;
import pl.szczurradar.api.posts.QualityStatus;
import pl.szczurradar.api.posts.QualityWindow;
import pl.szczurradar.api.posts.SourceQuality;
import pl.szczurradar.api.sources.Source;

/**
 * The text of the night's summary ({@link Night#text()}) from prepared inputs: production's night of
 * 2026-11-11 (the docs' example), the sources in {@link Source} order whatever order they come in, a run
 * still running, a source that failed (its error cut) or gave no post, a source without a result, no range
 * run, {@code none} outside the night, which run the alert names, the quality lines in each state with the
 * {@code ASSESSMENT_QUALITY} detail, and the whole within ntfy's bytes. What the inputs are built from is
 * {@code NightSummaryIT}.
 */
class NightSummaryTest {

	private static final UUID RETRY = UUID.fromString("00000000-0000-4000-8000-00000000000a");

	private static final UUID RANGE = UUID.fromString("00000000-0000-4000-8000-00000000000b");

	/** A quality window without an attempt: no quality line. */
	private static final QualityWindow NO_QUALITY = new QualityWindow(LocalDate.of(2026, 11, 2),
			LocalDate.of(2026, 11, 8), List.of());

	/** Production's night of 2026-11-11, the example of the class comment and the docs. */
	@Test
	void productionsNightOfTheEleventh() {
		Night night = new Night(LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 10),
				List.of(new Run(RETRY, RunKind.RETRY, RunStatus.PARTIAL), new Run(RANGE, RunKind.RANGE, RunStatus.PARTIAL)),
				0, 0,
				List.of(new SourceLine(Source.WYKOP, SourceFetchStatus.SUCCEEDED, 849, 0, null, 394, 10, 391, 13, 0,
						1_200_013, 1_148_332, 340_550),
						new SourceLine(Source.REDDIT, SourceFetchStatus.SUCCEEDED, 105, 0, null, 49, 18, 55, 12, 2, 245_889,
								213_594, 197_555)),
				Map.of(),
				new QualityWindow(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 11), List.of(
						new SourceQuality(Source.WYKOP, 3134, 81,
								Map.of(FailureClass.SCHEMA_VIOLATION, 9L, FailureClass.INVALID_REFERENCE, 3L,
										FailureClass.FABRICATED_EVIDENCE, 63L, FailureClass.DOMAIN_INVARIANT_VIOLATION, 6L),
								5, QualityStatus.OK),
						new SourceQuality(Source.REDDIT, 598, 101,
								Map.of(FailureClass.SCHEMA_VIOLATION, 46L, FailureClass.FABRICATED_EVIDENCE, 46L,
										FailureClass.DOMAIN_INVARIANT_VIOLATION, 9L),
								25, QualityStatus.OK))),
				List.of(), null);

		assertThat(night.text()).isEqualTo("""
				Night 2026-11-11 (range 2026-11-09…2026-11-10): retry partial, range partial; errors 0, skipped 0
				wykop: fetched 849, skipped 0, new 394, leftovers 10; assessed 391, failed 13, dead 0; tokens in 1200013 (cached 1148332), out 340550
				reddit: fetched 105, skipped 0, new 49, leftovers 18; assessed 55, failed 12, dead 2; tokens in 245889 (cached 213594), out 197555
				wykop quality 7d (2026-11-05…2026-11-11): failed 2.6% of 3134 attempts (schema_violation 0.3%, invalid_reference 0.1%, fabricated_evidence 2.0%, domain_invariant_violation 0.2%); threshold 5%
				reddit quality 7d (2026-11-05…2026-11-11): failed 16.9% of 598 attempts (schema_violation 7.7%, fabricated_evidence 7.7%, domain_invariant_violation 1.5%); threshold 25%
				Dead in admin runs since the previous night: none
				/admin""");
		assertThat(night.alertRunId()).as("the range run").isEqualTo(RANGE);
		assertThat(night.qualityAlert()).as("no source crossed").isEmpty();
	}

	/**
	 * Wykop above its threshold for the first time tonight (an alarm), Reddit with too few attempts to be
	 * checked; the quality lines come after the sources' lines and before the admin's dead posts.
	 */
	@Test
	void aSourceAboveItsThresholdAndOneWithTooFewAttempts() {
		SourceQuality wykop = new SourceQuality(Source.WYKOP, 110, 10,
				Map.of(FailureClass.FABRICATED_EVIDENCE, 8L, FailureClass.PROVIDER_ERROR, 2L), 5,
				QualityStatus.ABOVE_THRESHOLD);
		QualityWindow quality = new QualityWindow(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 8),
				List.of(wykop, new SourceQuality(Source.REDDIT, 46, 9, Map.of(FailureClass.SCHEMA_VIOLATION, 9L), 25,
						QualityStatus.NOT_ENOUGH_ATTEMPTS)));
		Night night = night(List.of(new SourceLine(Source.WYKOP, SourceFetchStatus.SUCCEEDED, 5, 0, null, 2, 0, 1, 1,
				0, 10, 0, 1)), Map.of(Source.WYKOP, 1L), quality,
				List.of(new QualityCrossing(wykop, new SourceQuality(Source.WYKOP, 100, 0, Map.of(), 5, QualityStatus.OK))));

		assertThat(night.text()).isEqualTo("""
				Night 2026-11-08 (range 2026-11-06…2026-11-07): retry partial, range partial; errors 0, skipped 0
				wykop: fetched 5, skipped 0, new 2, leftovers 0; assessed 1, failed 1, dead 0; tokens in 10 (cached 0), out 1
				wykop quality 7d (2026-11-02…2026-11-08): failed 9.1% of 110 attempts (provider_error 1.8%, fabricated_evidence 7.3%); threshold 5%; ABOVE THRESHOLD
				reddit quality 7d (2026-11-02…2026-11-08): 46 attempts, fewer than 100, threshold not checked
				Dead in admin runs since the previous night: wykop 1
				/admin""");
		assertThat(night.qualityAlert()).contains("wykop: failed 9.1% of 110 attempts in 7 days (2026-11-02…2026-11-08),"
				+ " above its threshold 5% (the day before: 0.0% of 100 attempts)"
				+ " (provider_error 1.8%, fabricated_evidence 7.3%); /admin");
	}

	@Test
	void aSourceWithoutAFailedAttemptHasNoClassesAndAShareEqualToTheThresholdIsNotAbove() {
		QualityWindow quality = new QualityWindow(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 8),
				List.of(new SourceQuality(Source.WYKOP, 200, 10, Map.of(FailureClass.TIMEOUT, 10L), 5, QualityStatus.OK),
						new SourceQuality(Source.REDDIT, 150, 0, Map.of(), 25, QualityStatus.OK)));

		List<String> lines = night(List.of(), Map.of(), quality, List.of()).text().lines().toList();

		assertThat(lines.get(1)).isEqualTo(
				"wykop quality 7d (2026-11-02…2026-11-08): failed 5.0% of 200 attempts (timeout 5.0%); threshold 5%");
		assertThat(lines.get(2))
			.isEqualTo("reddit quality 7d (2026-11-02…2026-11-08): failed 0.0% of 150 attempts; threshold 25%");
	}

	/**
	 * Both sources crossed: what the night before saw of each — too few attempts, or no attempt at all —
	 * the sources joined with {@code ; } and {@code /admin} last.
	 */
	@Test
	void theQualityAlertNamesEverySourceThatCrossedWithItsNightBefore() {
		SourceQuality wykop = new SourceQuality(Source.WYKOP, 120, 12, Map.of(FailureClass.SCHEMA_VIOLATION, 12L), 5,
				QualityStatus.ABOVE_THRESHOLD);
		SourceQuality reddit = new SourceQuality(Source.REDDIT, 100, 30,
				Map.of(FailureClass.SCHEMA_VIOLATION, 20L, FailureClass.FABRICATED_EVIDENCE, 10L), 25,
				QualityStatus.ABOVE_THRESHOLD);
		QualityWindow quality = new QualityWindow(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 8),
				List.of(wykop, reddit));
		Night night = night(List.of(), Map.of(), quality, List.of(
				new QualityCrossing(wykop,
						new SourceQuality(Source.WYKOP, 90, 9, Map.of(), 5, QualityStatus.NOT_ENOUGH_ATTEMPTS)),
				new QualityCrossing(reddit, null)));

		assertThat(night.qualityAlert()).contains("wykop: failed 10.0% of 120 attempts in 7 days"
				+ " (2026-11-02…2026-11-08), above its threshold 5% (the day before: 90 attempts, fewer than 100)"
				+ " (schema_violation 10.0%); reddit: failed 30.0% of 100 attempts in 7 days (2026-11-02…2026-11-08),"
				+ " above its threshold 25% (the day before: 0 attempts, fewer than 100)"
				+ " (schema_violation 20.0%, fabricated_evidence 10.0%); /admin");
		assertThat(night.alertRunId()).isEqualTo(RANGE);
	}

	@Test
	void theSourcesLinesAreInSourceOrderWhateverOrderTheyComeIn() {
		Night night = night(List.of(new SourceLine(Source.REDDIT, null, 0, 0, null, 0, 1, 1, 0, 0, 0, 0, 0),
				new SourceLine(Source.WYKOP, null, 0, 0, null, 0, 2, 2, 0, 0, 0, 0, 0)));

		assertThat(night.sources()).extracting(SourceLine::source).containsExactly(Source.WYKOP, Source.REDDIT);
		List<String> lines = night.text().lines().toList();
		assertThat(lines.get(1)).startsWith("wykop: not fetched, new 0, leftovers 2;");
		assertThat(lines.get(2)).startsWith("reddit: not fetched, new 0, leftovers 1;");
	}

	@Test
	void aRunStillRunningShowsItsStatusAndTheErrorsAndSkippedAreTheRunsSums() {
		Night night = new Night(LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 6), LocalDate.of(2026, 11, 7),
				List.of(new Run(RETRY, RunKind.RETRY, RunStatus.PARTIAL), new Run(RANGE, RunKind.RANGE, RunStatus.RUNNING)),
				2, 3, List.of(), Map.of(), NO_QUALITY, List.of(), null);

		assertThat(night.text()).startsWith(
				"Night 2026-11-08 (range 2026-11-06…2026-11-07): retry partial, range running; errors 2, skipped 3\n");
	}

	@Test
	void aFailedSourceShowsItsErrorCutAndAnEmptyOneItsStatusInPlaceOfTheFetchCounts() {
		String error = "RedditApiException: HTTP 503 from Arctic Shift\nafter 3 attempts " + "x".repeat(300);
		Night night = night(List.of(
				new SourceLine(Source.WYKOP, SourceFetchStatus.EMPTY, 2, 2, null, 0, 0, 0, 0, 0, 0, 0, 0),
				new SourceLine(Source.REDDIT, SourceFetchStatus.FAILED, 0, 0, error, 0, 12, 10, 2, 0, 1000, 0, 100)));

		List<String> lines = night.text().lines().toList();
		assertThat(lines.get(1)).isEqualTo("wykop: empty, new 0, leftovers 0; assessed 0, failed 0, dead 0;"
				+ " tokens in 0 (cached 0), out 0");
		assertThat(lines.get(2)).startsWith("reddit: failed: RedditApiException: HTTP 503 from Arctic Shift after 3"
				+ " attempts xxx")
			.endsWith("…, new 0, leftovers 12; assessed 10, failed 2, dead 0; tokens in 1000 (cached 0), out 100")
			.doesNotContain("x".repeat(200));
		String cut = lines.get(2).substring("reddit: failed: ".length(), lines.get(2).indexOf(", new 0"));
		assertThat(cut).hasSize(NightSummary.MAX_SOURCE_ERROR);
	}

	@Test
	void aSourceWithoutAResultInTheRangeRunIsNotFetched() {
		Night night = new Night(LocalDate.of(2026, 11, 8), null, null,
				List.of(new Run(RETRY, RunKind.RETRY, RunStatus.SUCCEEDED)), 0, 0,
				List.of(new SourceLine(Source.WYKOP, null, 0, 0, null, 0, 4, 4, 0, 0, 400, 100, 40)), Map.of(), NO_QUALITY,
				List.of(), null);

		assertThat(night.text()).isEqualTo("""
				Night 2026-11-08: retry succeeded; errors 0, skipped 0
				wykop: not fetched, new 0, leftovers 4; assessed 4, failed 0, dead 0; tokens in 400 (cached 100), out 40
				Dead in admin runs since the previous night: none
				/admin""");
		assertThat(night.alertRunId()).as("no range run: the first").isEqualTo(RETRY);
	}

	@Test
	void theDeadPostsOfTheAdminsRunsAreListedPerSourceInSourceOrder() {
		Night night = new Night(LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 6), LocalDate.of(2026, 11, 7),
				List.of(new Run(RANGE, RunKind.RANGE, RunStatus.SUCCEEDED)), 0, 0, List.of(),
				Map.of(Source.REDDIT, 2L, Source.WYKOP, 1L), NO_QUALITY, List.of(), null);

		assertThat(night.text()).contains("\nDead in admin runs since the previous night: wykop 1, reddit 2\n/admin");
	}

	/** A quality window that could not be read: one line in place of the sources' quality lines, no alarm. */
	@Test
	void anUnavailableQualityWindowIsOneLineAndNoAlarm() {
		Night night = new Night(LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 6), LocalDate.of(2026, 11, 7),
				List.of(new Run(RANGE, RunKind.RANGE, RunStatus.SUCCEEDED)), 0, 0,
				List.of(new SourceLine(Source.WYKOP, SourceFetchStatus.SUCCEEDED, 5, 0, null, 5, 0, 5, 0, 0, 50, 0, 5)),
				Map.of(), NO_QUALITY, List.of(), "QueryTimeoutException");

		assertThat(night.text()).isEqualTo("""
				Night 2026-11-08 (range 2026-11-06…2026-11-07): range succeeded; errors 0, skipped 0
				wykop: fetched 5, skipped 0, new 5, leftovers 0; assessed 5, failed 0, dead 0; tokens in 50 (cached 0), out 5
				quality 7d (2026-11-02…2026-11-08): unavailable (QueryTimeoutException)
				Dead in admin runs since the previous night: none
				/admin""");
		assertThat(night.qualityAlert()).isEmpty();
		assertThatIllegalArgumentException()
			.as("an unavailable window has nothing to show")
			.isThrownBy(() -> new Night(night.date(), null, null, night.runs(), 0, 0, List.of(), Map.of(),
					new QualityWindow(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 8),
							List.of(new SourceQuality(Source.WYKOP, 1, 0, Map.of(), 5, QualityStatus.NOT_ENOUGH_ATTEMPTS))),
					List.of(), "QueryTimeoutException"));
	}

	/**
	 * Every source failing with the longest error, the largest counters, and a quality line with every
	 * failure class above the threshold for each; its alert with every source too.
	 */
	@Test
	void everySourceFailingWithTheLongestErrorOfFourByteCharactersStaysWithinNtfysBytes() {
		String error = "🚀".repeat(NightSummary.MAX_SOURCE_ERROR);
		List<SourceLine> sources = List.of(Source.values())
			.stream()
			.map(source -> new SourceLine(source, SourceFetchStatus.FAILED, 0, 0, error, Long.MAX_VALUE, Long.MAX_VALUE,
					Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE))
			.toList();
		Map<Source, Long> dead = Map.of(Source.WYKOP, Long.MAX_VALUE, Source.REDDIT, Long.MAX_VALUE);
		Map<FailureClass, Long> everyClass = new EnumMap<>(FailureClass.class);
		for (FailureClass failureClass : FailureClass.values()) {
			everyClass.put(failureClass, Long.MAX_VALUE / 8);
		}
		List<SourceQuality> qualities = List.of(Source.values())
			.stream()
			.map(source -> new SourceQuality(source, Long.MAX_VALUE, Long.MAX_VALUE / 8 * 7, everyClass,
					source.failureThresholdPercent(), QualityStatus.ABOVE_THRESHOLD))
			.toList();
		QualityWindow quality = new QualityWindow(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 8), qualities);
		List<QualityCrossing> crossed = qualities.stream()
			.map(tonight -> new QualityCrossing(tonight, new SourceQuality(tonight.source(), Long.MAX_VALUE, 0,
					Map.of(), tonight.thresholdPercent(), QualityStatus.OK)))
			.toList();

		Night night = night(sources, dead, quality, crossed);
		String text = night.text();

		assertThat(text.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(NightSummary.MAX_BYTES);
		assertThat(text).endsWith("\n/admin");
		assertThat(text.lines().filter(line -> line.contains(" quality 7d ") && line.endsWith("; ABOVE THRESHOLD")))
			.hasSize(Source.values().length);
		String alert = night.qualityAlert().orElseThrow();
		assertThat(alert.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(NightSummary.MAX_BYTES);
		assertThat(alert).endsWith("; /admin");
	}

	private static Night night(List<SourceLine> sources) {
		return night(sources, Map.of(), NO_QUALITY, List.of());
	}

	private static Night night(List<SourceLine> sources, Map<Source, Long> deadInAdminRuns, QualityWindow quality,
			List<QualityCrossing> crossed) {
		return new Night(LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 6), LocalDate.of(2026, 11, 7),
				List.of(new Run(RETRY, RunKind.RETRY, RunStatus.PARTIAL), new Run(RANGE, RunKind.RANGE, RunStatus.PARTIAL)),
				0, 0, sources, deadInAdminRuns, quality, crossed, null);
	}

}
