package pl.szczurradar.api.pipeline.run;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import pl.szczurradar.api.pipeline.PipelineRunService;
import pl.szczurradar.api.pipeline.run.MissingDays.Coverage;
import pl.szczurradar.api.pipeline.run.MissingDays.Day;
import pl.szczurradar.api.sources.Source;

/** The days without data ({@link MissingDays}): the coverage rules, the window and its edges. */
class MissingDaysTest {

	/** Today in the plan's example: the window is 9–22 October 2026. */
	private static final LocalDate TODAY = LocalDate.parse("2026-10-24");

	/** Long before the window: the source was covered from then on. */
	private static final Map<Source, LocalDate> COVERED_LONG_AGO = Map.of(Source.WYKOP, LocalDate.parse("2026-09-01"));

	@Test
	void theWindowIsTheFourteenDaysUpToTheDayBeforeYesterday() {
		MissingDays missing = MissingDays.of(TODAY, List.of(Source.WYKOP), List.of(), Map.of());

		assertThat(missing.from()).isEqualTo("2026-10-09");
		assertThat(missing.to()).isEqualTo("2026-10-22");
	}

	@Test
	void theDaysNoRunCoveredAreMissing() {
		MissingDays missing = MissingDays.of(TODAY, List.of(Source.WYKOP),
				List.of(coverage("2026-10-09", "2026-10-18"), coverage("2026-10-21", "2026-10-22")), COVERED_LONG_AGO);

		assertThat(missing.days()).containsExactly(wykop("2026-10-19"), wykop("2026-10-20"));
	}

	@Test
	void yesterdayAndTodayAreNeverMissing() {
		MissingDays missing = MissingDays.of(TODAY, List.of(Source.WYKOP), List.of(), COVERED_LONG_AGO);

		assertThat(missing.days()).extracting(Day::day)
			.hasSize(MissingDays.WINDOW_DAYS)
			.startsWith(LocalDate.parse("2026-10-09"))
			.endsWith(LocalDate.parse("2026-10-22"))
			.doesNotContain(TODAY.minusDays(1), TODAY);
	}

	@Test
	void theWindowOfASourceStartsAtItsFirstCoveredDay() {
		MissingDays missing = MissingDays.of(TODAY, List.of(Source.WYKOP),
				List.of(coverage("2026-10-15", "2026-10-16")), Map.of(Source.WYKOP, LocalDate.parse("2026-10-15")));

		assertThat(missing.days()).extracting(Day::day).containsExactlyElementsOf(days("2026-10-17", "2026-10-22"));
		assertThat(missing.from()).as("the window itself stays").isEqualTo("2026-10-09");
	}

	@Test
	void aSourceFirstCoveredAfterTheWindowHasNoMissingDay() {
		MissingDays missing = MissingDays.of(TODAY, List.of(Source.WYKOP),
				List.of(coverage("2026-10-23", "2026-10-23")), Map.of(Source.WYKOP, LocalDate.parse("2026-10-23")));

		assertThat(missing.days()).isEmpty();
	}

	@Test
	void aSourceNeverCoveredHasNoMissingDay() {
		MissingDays missing = MissingDays.of(TODAY, List.of(Source.WYKOP), List.of(), Map.of());

		assertThat(missing.days()).isEmpty();
	}

	@Test
	void aSourceThatIsNotConfiguredHasNoMissingDay() {
		MissingDays missing = MissingDays.of(TODAY, List.of(), List.of(coverage("2026-10-09", "2026-10-18")),
				COVERED_LONG_AGO);

		assertThat(missing.days()).isEmpty();
	}

	@Test
	void aRunWithoutTheSourceDoesNotCoverItsDays() {
		Coverage withoutTheSource = new Coverage(LocalDate.parse("2026-10-19"), LocalDate.parse("2026-10-22"),
				Set.of());
		MissingDays missing = MissingDays.of(TODAY, List.of(Source.WYKOP),
				List.of(coverage("2026-10-09", "2026-10-18"), withoutTheSource), COVERED_LONG_AGO);

		assertThat(missing.days()).extracting(Day::day).containsExactlyElementsOf(days("2026-10-19", "2026-10-22"));
	}

	@Test
	void overlappingRangesCoverTheirUnion() {
		MissingDays missing = MissingDays.of(TODAY, List.of(Source.WYKOP),
				List.of(coverage("2026-10-12", "2026-10-20"), coverage("2026-10-05", "2026-10-14"),
						coverage("2026-10-20", "2026-10-22"), coverage("2026-10-21", "2026-10-21")),
				COVERED_LONG_AGO);

		assertThat(missing.days()).isEmpty();
	}

	@Test
	void aOneDayRunOnTheDayWithTwentyFiveHoursCoversExactlyThatDay() {
		// 25 October 2026 has 25 hours in Warsaw: from 00:00 CEST (22:00 UTC) to 23:59:59 CET (22:59:59 UTC).
		Coverage clockChangeDay = Coverage.of(range("2026-10-24T22:00:00Z", "2026-10-25T22:59:59Z"),
				PipelineRunService.ZONE);
		Coverage nextDay = Coverage.of(range("2026-10-25T23:00:00Z", "2026-10-26T22:59:59Z"), PipelineRunService.ZONE);
		assertThat(clockChangeDay).isEqualTo(coverage("2026-10-25", "2026-10-25"));
		assertThat(nextDay).isEqualTo(coverage("2026-10-26", "2026-10-26"));

		MissingDays missing = MissingDays.of(LocalDate.parse("2026-10-28"), List.of(Source.WYKOP),
				List.of(coverage("2026-10-01", "2026-10-24"), clockChangeDay), COVERED_LONG_AGO);

		assertThat(missing.days()).containsExactly(wykop("2026-10-26"));
	}

	@Test
	void justAfterMidnightOneBadNightShowsTheDayBeforeYesterdayUntilTheNightsRunCoversIt() {
		// 00:05 in Warsaw on 24 October. The night X fetches X-2…X-1, so 22 October is fetched on the nights
		// of the 23rd and the 24th: the 23rd's failed, and the 24th's (00:30 UTC, 02:30 in Warsaw) has not
		// run yet. After one bad night the day is missing for a few hours; nothing hides it.
		LocalDate today = LocalDate.ofInstant(Instant.parse("2026-10-23T22:05:00Z"), PipelineRunService.ZONE);
		List<Coverage> nights = new ArrayList<>();
		LocalDate failedNight = LocalDate.parse("2026-10-23");
		for (LocalDate night = LocalDate.parse("2026-10-01"); night.isBefore(failedNight); night = night.plusDays(1)) {
			nights.add(coverage(night.minusDays(2).toString(), night.minusDays(1).toString()));
		}

		MissingDays missing = MissingDays.of(today, List.of(Source.WYKOP), nights, COVERED_LONG_AGO);

		assertThat(today).isEqualTo("2026-10-24");
		assertThat(missing.days()).containsExactly(wykop("2026-10-22"));
		// Tonight's run covers it.
		nights.add(coverage("2026-10-22", "2026-10-23"));
		assertThat(MissingDays.of(today, List.of(Source.WYKOP), nights, COVERED_LONG_AGO).days()).isEmpty();
	}

	private static Coverage coverage(String from, String to) {
		return new Coverage(LocalDate.parse(from), LocalDate.parse(to), Set.of(Source.WYKOP));
	}

	private static PipelineRun range(String from, String to) {
		return PipelineRun.range(Instant.parse(from), Instant.parse(to), List.of(Source.WYKOP), UUID.randomUUID(),
				null);
	}

	/** The days {@code first}–{@code last}, inclusive. */
	private static List<LocalDate> days(String first, String last) {
		return LocalDate.parse(first).datesUntil(LocalDate.parse(last).plusDays(1)).toList();
	}

	private static Day wykop(String day) {
		return new Day(LocalDate.parse(day), List.of(Source.WYKOP));
	}

}
