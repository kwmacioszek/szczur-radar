package pl.szczurradar.api.measurement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import pl.szczurradar.api.agents.assessor.ContractFixtures;
import pl.szczurradar.api.agents.assessor.contract.Assertiveness;
import pl.szczurradar.api.agents.assessor.contract.ContractJson;
import pl.szczurradar.api.agents.assessor.contract.Direction;
import pl.szczurradar.api.agents.assessor.contract.Relevance;
import pl.szczurradar.api.agents.assessor.contract.Target;
import pl.szczurradar.api.measurement.GoldLabels.Label;
import pl.szczurradar.api.measurement.GoldLabels.LabeledTarget;
import pl.szczurradar.api.measurement.ModelsConfig.Model;
import pl.szczurradar.api.measurement.ModelsConfig.Price;
import pl.szczurradar.api.measurement.ModelsConfig.Role;
import pl.szczurradar.api.measurement.RedditMeasures.Interval;
import pl.szczurradar.api.measurement.RedditMeasures.LengthBucket;
import pl.szczurradar.api.measurement.RedditMeasures.Review;
import pl.szczurradar.api.measurement.RedditMeasures.Reviewed;
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
		// 0/20 and 0/467 used to round to a negative zero, which the report printed as "-0.0".
		assertThat(RedditMeasures.wilson(0, 20).low()).isNotNegative();
		assertThat(RedditMeasures.wilson(0, 467).low()).isNotNegative();
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
	void theSheetTakesTenFifteenFifteenLongestFirst() {
		List<Integer> queue = RedditMeasures.sheetQueue(lengths(100, 100, 100), LengthBucket::of, 1);

		assertThat(queue).hasSize(40).doesNotHaveDuplicates();
		assertThat(queue.stream().map(LengthBucket::of).toList().subList(0, 7)).containsExactly(LengthBucket.LONG,
				LengthBucket.MEDIUM, LengthBucket.SHORT, LengthBucket.LONG, LengthBucket.MEDIUM, LengthBucket.SHORT,
				LengthBucket.LONG);
		assertThat(queue.stream().map(LengthBucket::of).toList().subList(30, 40)).containsExactly(LengthBucket.MEDIUM,
				LengthBucket.SHORT, LengthBucket.MEDIUM, LengthBucket.SHORT, LengthBucket.MEDIUM, LengthBucket.SHORT,
				LengthBucket.MEDIUM, LengthBucket.SHORT, LengthBucket.MEDIUM, LengthBucket.SHORT);
		assertThat(queue.stream().filter(length -> LengthBucket.of(length) == LengthBucket.LONG)).hasSize(10);
		assertThat(queue.stream().filter(length -> LengthBucket.of(length) == LengthBucket.MEDIUM)).hasSize(15);
		assertThat(queue.stream().filter(length -> LengthBucket.of(length) == LengthBucket.SHORT)).hasSize(15);
	}

	@Test
	void theSheetSkipsABucketThatIsUsedUp() {
		List<Integer> queue = RedditMeasures.sheetQueue(lengths(2, 1, 0), LengthBucket::of, 1);

		assertThat(queue.stream().map(LengthBucket::of).toList()).containsExactly(LengthBucket.MEDIUM, LengthBucket.SHORT,
				LengthBucket.SHORT);
	}

	@Test
	void theSheetIsTheSameForTheSameSeed() {
		List<Integer> candidates = lengths(100, 100, 100);

		assertThat(RedditMeasures.sheetQueue(candidates, LengthBucket::of, 20261030))
			.isEqualTo(RedditMeasures.sheetQueue(candidates, LengthBucket::of, 20261030))
			.isNotEqualTo(RedditMeasures.sheetQueue(candidates, LengthBucket::of, 20261031));
		assertThat(RedditMeasures.sheetQueue(List.<Integer>of(), LengthBucket::of, 1)).isEmpty();
	}

	@Test
	void aTargetTheOwnerAddedIsMissed() {
		Review review = RedditMeasures.review(List.of(
				reviewed(false, targets(owner("Apple", Direction.BULLISH), owner("Micron", Direction.BEARISH)),
						model("Apple", Direction.BULLISH)),
				reviewed(false, targets(owner("Tesla", Direction.BULLISH)), model("$Tesla", Direction.BULLISH))));

		assertThat(review.posts()).isEqualTo(2);
		assertThat(review.postsWithMiss()).isEqualTo(1);
		assertThat(review.ownerTargets()).isEqualTo(3);
		assertThat(review.missed()).isEqualTo(1);
		assertThat(review.removed()).isZero();
	}

	@Test
	void aModelTargetWithoutAPairIsRemoved() {
		Review review = RedditMeasures.review(List.of(
				reviewed(false, targets(owner("Apple", Direction.BULLISH)), model("Apple", Direction.BULLISH),
						model("the sector", Direction.NOT_EXPRESSED))));

		assertThat(review.modelTargets()).isEqualTo(2);
		assertThat(review.removed()).isEqualTo(1);
		assertThat(review.missed()).isZero();
	}

	@Test
	void aPostTheOwnerFoundNotInvestmentRelatedRemovesEveryModelTarget() {
		Label none = new Label("reddit-1", Relevance.NOT_INVESTMENT_RELATED, false, "", List.of());

		Review review = RedditMeasures.review(List.of(new Reviewed(none, List.of(model("Apple", Direction.BULLISH)))));

		assertThat(review.posts()).isEqualTo(1);
		assertThat(review.removed()).isEqualTo(1);
		assertThat(review.postsWithMiss()).isZero();
	}

	@Test
	void anOppositeDirectionOfAPairIsReversed() {
		Review review = RedditMeasures.review(List.of(reviewed(false,
				targets(owner("Apple", Direction.BULLISH), owner("Tesla", Direction.BEARISH),
						owner("Micron", Direction.BULLISH), owner("Intel", Direction.BULLISH),
						owner("AMD", Direction.NEUTRAL)),
				model("Apple", Direction.BEARISH), model("Tesla", Direction.BEARISH), model("Intel", Direction.NEUTRAL),
				model("AMD", Direction.BEARISH))));

		// Micron has no pair: it is missed, not reversed. AMD's owner direction is not polar.
		assertThat(review.polarTargets()).isEqualTo(4);
		assertThat(review.reversed()).isEqualTo(1);
	}

	@Test
	void aModelStanceTheOwnerDoesNotSeeCountsLikeGoldMetrics() {
		Review review = RedditMeasures.review(List.of(reviewed(false,
				targets(owner("Apple", Direction.NOT_EXPRESSED), owner("Tesla", Direction.UNCLEAR),
						owner("Micron", Direction.NOT_EXPRESSED), owner("Intel", Direction.NOT_EXPRESSED),
						owner("AMD", Direction.BULLISH)),
				model("Apple", Direction.BULLISH), model("Tesla", Direction.NEUTRAL), model("Micron", Direction.UNCLEAR),
				model("AMD", Direction.BEARISH))));

		// Micron: "unclear" and "not_expressed" are the same answer. Intel has no pair.
		assertThat(review.noStanceTargets()).isEqualTo(4);
		assertThat(review.unseenStances()).isEqualTo(2);
	}

	@Test
	void aHardPostCountsNowhere() {
		Reviewed hard = reviewed(true,
				targets(owner("Apple", Direction.BULLISH), owner("Micron", Direction.NOT_EXPRESSED),
						owner("Tesla", Direction.BEARISH)),
				model("Apple", Direction.BEARISH), model("Micron", Direction.BULLISH),
				model("the sector", Direction.NEUTRAL));

		assertThat(RedditMeasures.review(List.of(hard))).isEqualTo(new Review(0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
		assertThat(RedditMeasures.review(List.of())).isEqualTo(new Review(0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
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

	/** Candidates of the sheet: body lengths in the three buckets. */
	private static List<Integer> lengths(int shortPosts, int mediumPosts, int longPosts) {
		return Stream.of(IntStream.range(0, shortPosts), IntStream.range(1000, 1000 + mediumPosts),
				IntStream.range(3000, 3000 + longPosts)).flatMap(IntStream::boxed).toList();
	}

	private static Reviewed reviewed(boolean hard, List<LabeledTarget> owner, Target... model) {
		return new Reviewed(new Label("reddit-1", Relevance.INVESTMENT_RELATED, hard, "", owner), List.of(model));
	}

	private static List<LabeledTarget> targets(LabeledTarget... owner) {
		return List.of(owner);
	}

	private static LabeledTarget owner(String anchor, Direction direction) {
		return new LabeledTarget(anchor, direction, false);
	}

	private static Target model(String anchor, Direction direction) {
		Assertiveness assertiveness = direction == Direction.BULLISH || direction == Direction.BEARISH
				|| direction == Direction.NEUTRAL ? Assertiveness.UNHEDGED : null;
		return ContractFixtures.target("t", anchor, direction, assertiveness);
	}

	private static Recording line(String status, @Nullable String channel, @Nullable String rawText,
			@Nullable Integer input, @Nullable Integer cached, @Nullable Integer output) {
		return new Recording("reddit-1", "luna-flex", "openrouter", "openai/gpt-6-luna", channel, "flex", false, null, null,
				"v2.5", "V2", Instant.parse("2026-10-30T10:00:00Z"), rawText, input, output, cached, 10, 1, status,
				"failed".equals(status) ? "provider_error" : null, List.of());
	}

}
