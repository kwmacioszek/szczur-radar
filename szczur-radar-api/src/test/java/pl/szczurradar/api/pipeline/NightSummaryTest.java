package pl.szczurradar.api.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import pl.szczurradar.api.pipeline.NightSummary.Night;
import pl.szczurradar.api.pipeline.NightSummary.Run;
import pl.szczurradar.api.pipeline.NightSummary.SourceLine;
import pl.szczurradar.api.pipeline.run.RunKind;
import pl.szczurradar.api.pipeline.run.RunStatus;
import pl.szczurradar.api.pipeline.run.SourceFetchStatus;
import pl.szczurradar.api.sources.Source;

/**
 * The text of the night's summary ({@link Night#text()}) from prepared inputs: production's night of
 * 2026-11-11 (the docs' example), the sources in {@link Source} order whatever order they come in, a run
 * still running, a source that failed (its error cut) or gave no post, a source without a result, no range
 * run, {@code none} outside the night, which run the alert names, and the whole within ntfy's bytes. What
 * the inputs are built from is {@code NightSummaryIT}.
 */
class NightSummaryTest {

	private static final UUID RETRY = UUID.fromString("00000000-0000-4000-8000-00000000000a");

	private static final UUID RANGE = UUID.fromString("00000000-0000-4000-8000-00000000000b");

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
				Map.of());

		assertThat(night.text()).isEqualTo("""
				Night 2026-11-11 (range 2026-11-09…2026-11-10): retry partial, range partial; errors 0, skipped 0
				wykop: fetched 849, skipped 0, new 394, leftovers 10; assessed 391, failed 13, dead 0; tokens in 1200013 (cached 1148332), out 340550
				reddit: fetched 105, skipped 0, new 49, leftovers 18; assessed 55, failed 12, dead 2; tokens in 245889 (cached 213594), out 197555
				Dead in admin runs since the previous night: none
				/admin""");
		assertThat(night.alertRunId()).as("the range run").isEqualTo(RANGE);
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
				2, 3, List.of(), Map.of());

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
				List.of(new SourceLine(Source.WYKOP, null, 0, 0, null, 0, 4, 4, 0, 0, 400, 100, 40)), Map.of());

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
				Map.of(Source.REDDIT, 2L, Source.WYKOP, 1L));

		assertThat(night.text()).contains("\nDead in admin runs since the previous night: wykop 1, reddit 2\n/admin");
	}

	@Test
	void everySourceFailingWithTheLongestErrorOfFourByteCharactersStaysWithinNtfysBytes() {
		String error = "🚀".repeat(NightSummary.MAX_SOURCE_ERROR);
		List<SourceLine> sources = List.of(Source.values())
			.stream()
			.map(source -> new SourceLine(source, SourceFetchStatus.FAILED, 0, 0, error, Long.MAX_VALUE, Long.MAX_VALUE,
					Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE))
			.toList();
		Map<Source, Long> dead = Map.of(Source.WYKOP, Long.MAX_VALUE, Source.REDDIT, Long.MAX_VALUE);

		String text = night(sources, dead).text();

		assertThat(text.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(NightSummary.MAX_BYTES);
		assertThat(text).endsWith("\n/admin");
	}

	private static Night night(List<SourceLine> sources) {
		return night(sources, Map.of());
	}

	private static Night night(List<SourceLine> sources, Map<Source, Long> deadInAdminRuns) {
		return new Night(LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 6), LocalDate.of(2026, 11, 7),
				List.of(new Run(RETRY, RunKind.RETRY, RunStatus.PARTIAL), new Run(RANGE, RunKind.RANGE, RunStatus.PARTIAL)),
				0, 0, sources, deadInAdminRuns);
	}

}
