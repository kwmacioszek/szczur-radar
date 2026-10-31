package pl.szczurradar.api.measurement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import pl.szczurradar.api.agents.assessor.contract.ContractJson;
import pl.szczurradar.api.measurement.ModelsConfig.Model;
import pl.szczurradar.api.measurement.ModelsConfig.Price;
import pl.szczurradar.api.measurement.ModelsConfig.Role;
import pl.szczurradar.api.measurement.RedditMeasures.Interval;
import pl.szczurradar.api.measurement.RedditMeasures.LengthBucket;
import pl.szczurradar.api.measurement.RedditMeasures.ThesisLanguage;
import pl.szczurradar.api.measurement.RedditMeasures.ThesisMix;
import pl.szczurradar.api.measurement.RedditReport.WykopUsage;
import pl.szczurradar.api.measurement.RedditSampler.Volume;

/** The logic behind the S-28 report's numbers ({@link RedditMeasures}) and the shape of its input files. */
class RedditMeasuresTest {

	private static final Model LUNA = new Model("luna", Role.PREFERRED, "openrouter", "openai/gpt-6-luna", "default", null,
			Map.of("openrouter", new Price(0.1, 0.01, 0.5), "openai/flex", new Price(0.05, 0.005, 0.25)));

	@Test
	void wilsonIntervalOfThePilotsDirectionScore() {
		Interval interval = RedditMeasures.wilson(54, 74);

		assertThat(interval.low()).isCloseTo(0.619, within(0.0005));
		assertThat(interval.high()).isCloseTo(0.818, within(0.0005));
	}

	@Test
	void wilsonIntervalStaysWithinBoundsAtTheEnds() {
		assertThat(RedditMeasures.wilson(0, 10).low()).isCloseTo(0, within(1e-9));
		assertThat(RedditMeasures.wilson(10, 10).high()).isCloseTo(1, within(1e-9));
	}

	@Test
	void lengthBucketsBreakAtOneAndThreeThousand() {
		assertThat(LengthBucket.of(0)).isEqualTo(LengthBucket.SHORT);
		assertThat(LengthBucket.of(999)).isEqualTo(LengthBucket.SHORT);
		assertThat(LengthBucket.of(1000)).isEqualTo(LengthBucket.MEDIUM);
		assertThat(LengthBucket.of(2999)).isEqualTo(LengthBucket.MEDIUM);
		assertThat(LengthBucket.of(3000)).isEqualTo(LengthBucket.LONG);
	}

	@Test
	void megathreadsMatchThePilotsPatternInAnyCase() {
		assertThat(List.of("Daily Discussion Thread for October 28, 2026", "r/Stocks Daily Discussion & Technicals Tuesday",
				"Weekly Earnings Thread 10/26 - 10/30", "daily general discussion and spitballin thread",
				"Thread on Meme Stocks"))
			.allMatch(RedditMeasures::isMegathread);
		assertThat(List.of("Is NVDA still a buy?", "My daily routine as a value investor", "Weekly chart of AAPL looks weak"))
			.noneMatch(RedditMeasures::isMegathread);
		assertThat(RedditMeasures.isMegathread(null)).isFalse();
	}

	@Test
	void aThesisIsPolishByADiacriticOrTwoFunctionWords() {
		assertThat(ThesisLanguage.of("Autor uważa, że spółka jest niedowartościowana")).isEqualTo(ThesisLanguage.POLISH);
		assertThat(ThesisLanguage.of("Apple nie jest tanie")).isEqualTo(ThesisLanguage.POLISH);
	}

	@Test
	void anEnglishThesisStaysEnglishDespiteWordsItSharesWithPolish() {
		assertThat(ThesisLanguage.of("The company is undervalued at this price")).isEqualTo(ThesisLanguage.ENGLISH);
		assertThat(ThesisLanguage.of("AAPL is going to rally on earnings, no doubt")).isEqualTo(ThesisLanguage.ENGLISH);
		assertThat(ThesisLanguage.of("The word nie is the only Polish one")).isEqualTo(ThesisLanguage.ENGLISH);
	}

	@Test
	void aPostsThesesGiveItsMix() {
		assertThat(ThesisMix.of(List.of())).isEqualTo(ThesisMix.NO_THESES);
		assertThat(ThesisMix.of(List.of("Prices will fall"))).isEqualTo(ThesisMix.ENGLISH_ONLY);
		assertThat(ThesisMix.of(List.of("Prices will fall", "Ceny spadną"))).isEqualTo(ThesisMix.MIXED);
		assertThat(ThesisMix.of(List.of("Ceny spadną"))).isEqualTo(ThesisMix.POLISH_ONLY);
	}

	@Test
	void aPostIsFinishedWithAnOkLineOrThreeLines() {
		Recording failed = line("failed", null, null, null, null, null);
		Recording ok = line("ok", "openrouter", "{}", 1, 0, 1);

		assertThat(RedditMeasures.finished(List.of())).isFalse();
		assertThat(RedditMeasures.finished(List.of(failed, failed))).isFalse();
		assertThat(RedditMeasures.finished(List.of(failed, ok))).isTrue();
		assertThat(RedditMeasures.finished(List.of(failed, failed, failed))).isTrue();
	}

	@Test
	void linesAreGroupedByPostInOrder() {
		Recording first = line("failed", null, null, null, null, null);
		Recording second = line("ok", "openrouter", "{}", 1, 0, 1);

		assertThat(RedditMeasures.byPost(List.of(first, second))).containsOnlyKeys("reddit-1");
		assertThat(RedditMeasures.byPost(List.of(first, second)).get("reddit-1")).containsExactly(first, second);
	}

	@Test
	void aLineIsPricedByItsRecordedChannelAndACallWithoutAnAnswerByNothing() {
		Recording flex = line("ok", "openai/flex", "{}", 1_000_000, 0, 1_000_000);
		Recording standard = line("ok", "openrouter", "{}", 1_000_000, 500_000, 1_000_000);
		Recording noAnswer = line("failed", null, null, null, null, null);

		assertThat(RedditMeasures.cost(LUNA, flex)).isCloseTo(0.30, within(1e-9));
		assertThat(RedditMeasures.cost(LUNA, standard)).isCloseTo(0.555, within(1e-9));
		assertThat(RedditMeasures.cost(LUNA, noAnswer)).isZero();
	}

	@Test
	void monthlyCostIsThirtyTimesTheDailyPostsTimesTheCostOfAPost() {
		assertThat(RedditMeasures.monthlyCost(700, 7, 0.001)).isCloseTo(3.0, within(1e-9));
	}

	@Test
	void medianOfOddAndEvenCounts() {
		assertThat(RedditMeasures.median(List.of())).isZero();
		assertThat(RedditMeasures.median(List.of(3, 1, 2))).isEqualTo(2);
		assertThat(RedditMeasures.median(List.of(4, 1, 2, 3))).isEqualTo(2.5);
	}

	@Test
	void volumeRowsSurviveTheirFile() {
		Volume row = new Volume(LocalDate.parse("2026-10-20"), "stocks", 40, 3, 1, 35);

		assertThat(ContractJson.MAPPER.readValue(ContractJson.MAPPER.writeValueAsString(List.of(row)), Volume[].class))
			.containsExactly(row);
	}

	@Test
	void wykopUsageHasTheShapePhase2Selects() {
		WykopUsage[] rows = ContractJson.MAPPER.readValue("""
				[{"day": "2026-10-20", "channel": "openai/flex", "service_tier": "flex", "flex_fallback": false, "ok": 90,
				  "failed": 2, "input_tokens": 300000, "cached_tokens": 100000, "output_tokens": 40000, "wykop_posts": 95},
				 {"day": "2026-10-20", "channel": null, "service_tier": "flex", "flex_fallback": true, "ok": 0,
				  "failed": 1, "input_tokens": 0, "cached_tokens": 0, "output_tokens": 0, "wykop_posts": 95}]
				""", WykopUsage[].class);

		assertThat(rows).hasSize(2);
		assertThat(rows[0].cost(LUNA)).isCloseTo((200_000 * 0.05 + 100_000 * 0.005 + 40_000 * 0.25) / 1_000_000,
				within(1e-9));
		assertThat(rows[1].cost(LUNA)).isZero();
	}

	private static Recording line(String status, @Nullable String channel, @Nullable String rawText,
			@Nullable Integer input, @Nullable Integer cached, @Nullable Integer output) {
		return new Recording("reddit-1", "luna-flex", "openrouter", "openai/gpt-6-luna", channel, "flex", false, null, null,
				"v2.5", "V2", Instant.parse("2026-10-30T10:00:00Z"), rawText, input, output, cached, 10, 1, status,
				"failed".equals(status) ? "provider_error" : null, List.of());
	}

}
