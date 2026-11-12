package pl.szczurradar.api.posts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import pl.szczurradar.api.agents.assessor.validation.FailureClass;
import pl.szczurradar.api.sources.Source;
import pl.szczurradar.api.text.Language;

/** The quality windows' rule, their crossings and their percentages, without Spring or a database. */
class AssessmentQualityTest {

	private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

	private static final String POLISH = "Autor uważa, że kurs wzrośnie";

	private static final String ENGLISH = "The author thinks that the price will rise";

	/** No letter or function word of either language. */
	private static final String UNDECIDED = "CDR 120 zl target";

	@Test
	void fewerThanTheMinimumIsNotChecked() {
		assertThat(AssessmentQuality.status(99, 99, 5)).isEqualTo(QualityStatus.NOT_ENOUGH_DATA);
		assertThat(AssessmentQuality.status(100, 6, 5)).isEqualTo(QualityStatus.ABOVE_THRESHOLD);
		assertThat(AssessmentQuality.status(100, 0, 5)).isEqualTo(QualityStatus.OK);
	}

	@Test
	void aShareEqualToTheThresholdIsStillOkAndOneAttemptMoreIsAbove() {
		assertThat(AssessmentQuality.status(200, 10, 5)).isEqualTo(QualityStatus.OK);
		assertThat(AssessmentQuality.status(200, 11, 5)).isEqualTo(QualityStatus.ABOVE_THRESHOLD);
		assertThat(AssessmentQuality.status(100, 25, 25)).isEqualTo(QualityStatus.OK);
		assertThat(AssessmentQuality.status(100, 26, 25)).isEqualTo(QualityStatus.ABOVE_THRESHOLD);
	}

	@Test
	void theRowsAreSummedPerSourceWithTheNonZeroClassesInTheirOrderAndTheSourcesThreshold() {
		List<SourceQuality> sources = AssessmentQuality.sources(List.of(
				count(Source.REDDIT, null, 70), count(Source.REDDIT, FailureClass.FABRICATED_EVIDENCE, 10),
				count(Source.REDDIT, FailureClass.SCHEMA_VIOLATION, 20), count(Source.WYKOP, null, 97),
				count(Source.WYKOP, FailureClass.DOMAIN_INVARIANT_VIOLATION, 1),
				count(Source.WYKOP, FailureClass.TIMEOUT, 0), count(Source.WYKOP, FailureClass.PROVIDER_ERROR, 2)));

		assertThat(sources).containsExactly(
				new SourceQuality(Source.WYKOP, 100, 3,
						Map.of(FailureClass.PROVIDER_ERROR, 2L, FailureClass.DOMAIN_INVARIANT_VIOLATION, 1L), 5,
						QualityStatus.OK),
				new SourceQuality(Source.REDDIT, 100, 30,
						Map.of(FailureClass.SCHEMA_VIOLATION, 20L, FailureClass.FABRICATED_EVIDENCE, 10L), 25,
						QualityStatus.ABOVE_THRESHOLD));
		assertThat(sources.get(0).classes().keySet()).containsExactly(FailureClass.PROVIDER_ERROR,
				FailureClass.DOMAIN_INVARIANT_VIOLATION);
		assertThat(sources.get(1).classes().keySet()).containsExactly(FailureClass.SCHEMA_VIOLATION,
				FailureClass.FABRICATED_EVIDENCE);
	}

	@Test
	void aSourceWithoutAFailedAttemptHasNoClassAndOneWithoutRowsIsMissing() {
		assertThat(AssessmentQuality.sources(List.of(count(Source.REDDIT, null, 40)))).containsExactly(
				new SourceQuality(Source.REDDIT, 40, 0, Map.of(), 25, QualityStatus.NOT_ENOUGH_DATA));
		assertThat(AssessmentQuality.sources(List.of())).isEmpty();
	}

	@Test
	void okYesterdayAndAboveTodayCrosses() {
		assertThat(AssessmentQuality.crossed(List.of(wykop(QualityStatus.ABOVE_THRESHOLD)),
				List.of(wykop(QualityStatus.OK))))
			.containsExactly(new QualityCrossing(wykop(QualityStatus.ABOVE_THRESHOLD), wykop(QualityStatus.OK)));
	}

	@Test
	void tooFewAttemptsOrNoneYesterdayAndAboveTodayCrosses() {
		assertThat(AssessmentQuality.crossed(List.of(wykop(QualityStatus.ABOVE_THRESHOLD)),
				List.of(wykop(QualityStatus.NOT_ENOUGH_DATA))))
			.containsExactly(new QualityCrossing(wykop(QualityStatus.ABOVE_THRESHOLD),
					wykop(QualityStatus.NOT_ENOUGH_DATA)));
		assertThat(AssessmentQuality.crossed(List.of(wykop(QualityStatus.ABOVE_THRESHOLD)), List.of()))
			.containsExactly(new QualityCrossing(wykop(QualityStatus.ABOVE_THRESHOLD), null));
	}

	@Test
	void aboveTwoDaysInARowDoesNotCrossAgain() {
		assertThat(AssessmentQuality.crossed(List.of(wykop(QualityStatus.ABOVE_THRESHOLD)),
				List.of(wykop(QualityStatus.ABOVE_THRESHOLD)))).isEmpty();
	}

	@Test
	void tooFewAttemptsOrOkTodayDoesNotCross() {
		assertThat(AssessmentQuality.crossed(List.of(wykop(QualityStatus.NOT_ENOUGH_DATA)),
				List.of(wykop(QualityStatus.OK)))).isEmpty();
		assertThat(AssessmentQuality.crossed(List.of(wykop(QualityStatus.OK)), List.of())).isEmpty();
	}

	@Test
	void eachSourceCrossesOnItsOwn() {
		SourceQuality redditAbove = new SourceQuality(Source.REDDIT, 100, 30, Map.of(), 25,
				QualityStatus.ABOVE_THRESHOLD);
		assertThat(AssessmentQuality.crossed(List.of(wykop(QualityStatus.ABOVE_THRESHOLD), redditAbove),
				List.of(wykop(QualityStatus.OK), redditAbove)))
			.containsExactly(new QualityCrossing(wykop(QualityStatus.ABOVE_THRESHOLD), wykop(QualityStatus.OK)));
	}

	@Test
	void theWindowIsSevenCalendarDaysOfTheZoneAndTheNightBeforeEndsADayBeforeNow() {
		AssessmentRepository repository = mock(AssessmentRepository.class);
		when(repository.countModelAttemptsBySourceAndClass(any(), any())).thenReturn(List.of());
		AssessmentQuality quality = new AssessmentQuality(repository);

		// 25 October 2026 has 25 hours in Warsaw (CEST → CET at 03:00).
		QualityWindow window = quality.window(LocalDate.of(2026, 10, 25), WARSAW);
		assertThat(window).isEqualTo(new QualityWindow(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 25),
				List.of()));
		verify(repository).countModelAttemptsBySourceAndClass(Instant.parse("2026-10-18T22:00:00Z"),
				Instant.parse("2026-10-25T23:00:00Z"));

		// 01:45 in Warsaw on 8 November: the night before is cut at 01:45 on the 7th, not at its end.
		assertThat(quality.crossed(quality.window(LocalDate.of(2026, 11, 8), WARSAW),
				Instant.parse("2026-11-08T00:45:00Z"), WARSAW)).isEmpty();
		verify(repository).countModelAttemptsBySourceAndClass(Instant.parse("2026-11-01T23:00:00Z"),
				Instant.parse("2026-11-08T23:00:00Z"));
		verify(repository).countModelAttemptsBySourceAndClass(Instant.parse("2026-10-31T23:00:00Z"),
				Instant.parse("2026-11-07T00:45:00Z"));
	}

	@Test
	void theNightBeforeNeverReachesIntoTheNight() {
		AssessmentRepository repository = mock(AssessmentRepository.class);
		when(repository.countModelAttemptsBySourceAndClass(any(), any())).thenReturn(List.of());

		// A night's summary asked for more than a day after the night started: the night before still ends at
		// the night's start.
		AssessmentQuality quality = new AssessmentQuality(repository);
		quality.crossed(quality.window(LocalDate.of(2026, 11, 8), WARSAW), Instant.parse("2026-11-08T23:30:00Z"),
				WARSAW);
		verify(repository).countModelAttemptsBySourceAndClass(Instant.parse("2026-10-31T23:00:00Z"),
				Instant.parse("2026-11-07T23:00:00Z"));
	}

	@Test
	void aShareIsWrittenWithOneDecimalHalfUpAndADot() {
		assertThat(AssessmentQuality.percent(258, 10_000)).isEqualTo("2.6%");
		assertThat(AssessmentQuality.percent(1, 2_000)).isEqualTo("0.1%");
		assertThat(AssessmentQuality.percent(1, 3)).isEqualTo("33.3%");
		assertThat(AssessmentQuality.percent(101, 598)).isEqualTo("16.9%");
		assertThat(AssessmentQuality.percent(0, 46)).isEqualTo("0.0%");
		assertThat(AssessmentQuality.percent(5, 5)).isEqualTo("100.0%");
		assertThatIllegalArgumentException().isThrownBy(() -> AssessmentQuality.percent(0, 0));
	}

	@Test
	void fewerThanFiveHundredThesesAreNotChecked() {
		assertThat(AssessmentQuality.languages(wykopTheses(499, 499))).containsExactly(
				new SourceLanguage(Source.WYKOP, 499, 499, 2, QualityStatus.NOT_ENOUGH_DATA));
		assertThat(AssessmentQuality.languages(wykopTheses(500, 0))).containsExactly(
				new SourceLanguage(Source.WYKOP, 500, 0, 2, QualityStatus.OK));
	}

	@Test
	void aShareOfThesesEqualToTheThresholdIsStillOkAndOneThesisMoreIsAbove() {
		// Wykop's threshold is 2 %: 10 of 500 is 2.0 %, 11 of 500 is 2.2 %.
		assertThat(AssessmentQuality.languages(wykopTheses(500, 10))).containsExactly(
				new SourceLanguage(Source.WYKOP, 500, 10, 2, QualityStatus.OK));
		assertThat(AssessmentQuality.languages(wykopTheses(500, 11))).containsExactly(
				new SourceLanguage(Source.WYKOP, 500, 11, 2, QualityStatus.ABOVE_THRESHOLD));
	}

	@Test
	void onlyEvidenceOfAnotherLanguageThanThePostsIsAViolation() {
		List<SourceLanguage> sources = AssessmentQuality.languages(List.of(
				new PostStatement(Source.REDDIT, Language.EN, POLISH),
				new PostStatement(Source.REDDIT, Language.EN, ENGLISH),
				new PostStatement(Source.REDDIT, Language.EN, UNDECIDED),
				new PostStatement(Source.WYKOP, Language.PL, ENGLISH),
				new PostStatement(Source.WYKOP, Language.PL, POLISH),
				new PostStatement(Source.WYKOP, Language.PL, UNDECIDED),
				// The post's language decides, not its source's: an English post on Wykop.
				new PostStatement(Source.WYKOP, Language.EN, POLISH)));

		assertThat(sources).containsExactly(
				new SourceLanguage(Source.WYKOP, 4, 2, 2, QualityStatus.NOT_ENOUGH_DATA),
				new SourceLanguage(Source.REDDIT, 3, 1, 30, QualityStatus.NOT_ENOUGH_DATA));
		assertThat(AssessmentQuality.languages(List.of())).isEmpty();
	}

	@Test
	void okYesterdayAndAboveTodayCrossesTheLanguageThreshold() {
		assertThat(AssessmentQuality.languageCrossed(List.of(wykopLanguage(QualityStatus.ABOVE_THRESHOLD)),
				List.of(wykopLanguage(QualityStatus.OK))))
			.containsExactly(new LanguageCrossing(wykopLanguage(QualityStatus.ABOVE_THRESHOLD),
					wykopLanguage(QualityStatus.OK)));
	}

	@Test
	void tooFewThesesOrNoneYesterdayAndAboveTodayCrossesTheLanguageThreshold() {
		assertThat(AssessmentQuality.languageCrossed(List.of(wykopLanguage(QualityStatus.ABOVE_THRESHOLD)),
				List.of(wykopLanguage(QualityStatus.NOT_ENOUGH_DATA))))
			.containsExactly(new LanguageCrossing(wykopLanguage(QualityStatus.ABOVE_THRESHOLD),
					wykopLanguage(QualityStatus.NOT_ENOUGH_DATA)));
		assertThat(AssessmentQuality.languageCrossed(List.of(wykopLanguage(QualityStatus.ABOVE_THRESHOLD)),
				List.of()))
			.containsExactly(new LanguageCrossing(wykopLanguage(QualityStatus.ABOVE_THRESHOLD), null));
	}

	@Test
	void aboveTheLanguageThresholdTwoDaysInARowDoesNotCrossAgain() {
		assertThat(AssessmentQuality.languageCrossed(List.of(wykopLanguage(QualityStatus.ABOVE_THRESHOLD)),
				List.of(wykopLanguage(QualityStatus.ABOVE_THRESHOLD)))).isEmpty();
		assertThat(AssessmentQuality.languageCrossed(List.of(wykopLanguage(QualityStatus.OK)), List.of())).isEmpty();
	}

	@Test
	void theLanguageWindowHasTheDaysOfTheQualityWindowAndTheSameNightBefore() {
		AssessmentRepository repository = mock(AssessmentRepository.class);
		when(repository.findModelStatements(any(), any())).thenReturn(List.of());
		AssessmentQuality quality = new AssessmentQuality(repository);

		assertThat(quality.languageWindow(LocalDate.of(2026, 10, 25), WARSAW)).isEqualTo(
				new LanguageWindow(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 25), List.of()));
		verify(repository).findModelStatements(Instant.parse("2026-10-18T22:00:00Z"),
				Instant.parse("2026-10-25T23:00:00Z"));

		assertThat(quality.languageCrossed(quality.languageWindow(LocalDate.of(2026, 11, 8), WARSAW),
				Instant.parse("2026-11-08T00:45:00Z"), WARSAW)).isEmpty();
		verify(repository).findModelStatements(Instant.parse("2026-10-31T23:00:00Z"),
				Instant.parse("2026-11-07T00:45:00Z"));
		quality.languageCrossed(quality.languageWindow(LocalDate.of(2026, 11, 8), WARSAW),
				Instant.parse("2026-11-08T23:30:00Z"), WARSAW);
		verify(repository).findModelStatements(Instant.parse("2026-10-31T23:00:00Z"),
				Instant.parse("2026-11-07T23:00:00Z"));
	}

	/** {@code count} theses of Wykop's Polish posts, {@code english} of them in English. */
	private static List<PostStatement> wykopTheses(int count, int english) {
		return IntStream.range(0, count)
			.mapToObj(i -> new PostStatement(Source.WYKOP, Language.PL, i < english ? ENGLISH : POLISH))
			.toList();
	}

	private static SourceLanguage wykopLanguage(QualityStatus status) {
		return new SourceLanguage(Source.WYKOP, 500, status == QualityStatus.ABOVE_THRESHOLD ? 20 : 5, 2, status);
	}

	private static SourceClassCount count(Source source, @Nullable FailureClass failureClass, long count) {
		return new SourceClassCount(source, failureClass, count);
	}

	private static SourceQuality wykop(QualityStatus status) {
		return new SourceQuality(Source.WYKOP, 100, status == QualityStatus.ABOVE_THRESHOLD ? 10 : 2, Map.of(), 5,
				status);
	}

}
