package pl.szczurradar.api.measurement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import pl.szczurradar.api.agents.assessor.ContractFixtures;
import pl.szczurradar.api.agents.assessor.contract.Assertiveness;
import pl.szczurradar.api.agents.assessor.contract.AssessmentOutputV2;
import pl.szczurradar.api.agents.assessor.contract.Direction;
import pl.szczurradar.api.agents.assessor.contract.Relevance;
import pl.szczurradar.api.agents.assessor.contract.Target;
import pl.szczurradar.api.measurement.GoldLabels.Label;
import pl.szczurradar.api.measurement.GoldLabels.LabeledTarget;
import pl.szczurradar.api.measurement.GoldMetrics.Metric;
import pl.szczurradar.api.measurement.GoldMetrics.Ratio;

/**
 * {@link GoldMetrics} on a synthetic gold set whose every value is counted by hand:
 *
 * <pre>
 * post  gold (hard?)                                    model output
 * p1    investment: XTB bullish, CDR bearish            investment: #XTB bullish, CDR bullish, PKO bearish
 * p2    not_investment: —                               investment: —
 * p3    unclear: ropa not_expressed                     unclear: ropa not_expressed
 * p4    HARD investment: KGHM bullish                   investment: KGHM bearish
 * p5    investment: CD Projekt bullish                  (failed — no output)
 * p6    investment: złoto bullish, srebro bullish       investment: złoto bullish
 * </pre>
 */
class GoldMetricsTest {

	private static final List<Label> GOLD = List.of(
			label("p1", Relevance.INVESTMENT_RELATED, false, gold("XTB", Direction.BULLISH), gold("CDR", Direction.BEARISH)),
			label("p2", Relevance.NOT_INVESTMENT_RELATED, false),
			label("p3", Relevance.UNCLEAR, false, gold("ropa", Direction.NOT_EXPRESSED)),
			label("p4", Relevance.INVESTMENT_RELATED, true, gold("KGHM", Direction.BULLISH)),
			label("p5", Relevance.INVESTMENT_RELATED, false, gold("CD Projekt", Direction.BULLISH)),
			label("p6", Relevance.INVESTMENT_RELATED, false, gold("złoto", Direction.BULLISH),
					gold("srebro", Direction.BULLISH)));

	private static final Map<String, AssessmentOutputV2> OUTPUTS = Map.of(
			"p1", output(Relevance.INVESTMENT_RELATED, model("#XTB", Direction.BULLISH), model("CDR", Direction.BULLISH),
					model("PKO", Direction.BEARISH)),
			"p2", output(Relevance.INVESTMENT_RELATED),
			"p3", output(Relevance.UNCLEAR, model("ropa", Direction.NOT_EXPRESSED)),
			"p4", output(Relevance.INVESTMENT_RELATED, model("KGHM", Direction.BEARISH)),
			"p6", output(Relevance.INVESTMENT_RELATED, model("złoto", Direction.BULLISH)));

	private final GoldMetrics metrics = GoldMetrics.compute(GOLD, OUTPUTS);

	@Test
	void contractComplianceCountsEveryPostIncludingHardOnes() {
		// valid outputs p1, p2, p3, p4, p6 of 6 posts
		assertThat(metrics.ratio(Metric.CONTRACT_COMPLIANCE)).isEqualTo(new Ratio(5, 6));
	}

	@Test
	void relevanceSkipsGoldUnclearAndHardPostsAndCountsAFailureAsWrong() {
		// p1 right, p2 wrong, p5 failed, p6 right; p3 (gold unclear) and p4 (hard) out
		assertThat(metrics.ratio(Metric.RELEVANCE_BINARY_ACCURACY)).isEqualTo(new Ratio(2, 4));
	}

	@Test
	void recallIsMatchedGoldTargetsOverGoldTargets() {
		// gold: p1 2 + p3 1 + p5 1 + p6 2 = 6; matched: XTB↔#XTB, CDR, ropa, złoto = 4
		assertThat(metrics.ratio(Metric.TARGET_RECALL)).isEqualTo(new Ratio(4, 6));
	}

	@Test
	void precisionIsMatchedModelTargetsOverModelTargets() {
		// model: p1 3 + p2 0 + p3 1 + p6 1 = 5 (p4 hard); matched 4
		assertThat(metrics.ratio(Metric.TARGET_PRECISION)).isEqualTo(new Ratio(4, 5));
	}

	@Test
	void directionAccuracyCountsUnmatchedGoldTargetsAsWrong() {
		// gold with a direction: XTB, CDR, CD Projekt, złoto, srebro = 5; right: XTB, złoto
		assertThat(metrics.ratio(Metric.TARGET_DIRECTION_ACCURACY)).isEqualTo(new Ratio(2, 5));
	}

	@Test
	void dominantDirectionSkipsPostsWithoutOne() {
		// gold dominant: p1 none (1:1), p5 bullish, p6 bullish; model: p5 failed, p6 bullish
		assertThat(metrics.ratio(Metric.DOMINANT_DIRECTION_AGREEMENT)).isEqualTo(new Ratio(1, 2));
	}

	@Test
	void contradictionsAreOppositeDirectionsOverGoldBullishOrBearishTargets() {
		// gold bullish/bearish: XTB, CDR, CD Projekt, złoto, srebro = 5; opposite: CDR (bearish → bullish)
		assertThat(metrics.ratio(Metric.CONTRADICTION_RATE)).isEqualTo(new Ratio(1, 5));
	}

	@Test
	void aModelThatNeverReturnsTargetsScoresZeroOnEveryTargetMetric() {
		Map<String, AssessmentOutputV2> empty = new HashMap<>();
		GOLD.forEach(label -> empty.put(label.postId(), output(label.relevance())));

		GoldMetrics none = GoldMetrics.compute(GOLD, empty);

		assertThat(none.ratio(Metric.CONTRACT_COMPLIANCE)).isEqualTo(new Ratio(6, 6));
		assertThat(none.ratio(Metric.RELEVANCE_BINARY_ACCURACY)).isEqualTo(new Ratio(4, 4));
		assertThat(none.ratio(Metric.TARGET_RECALL)).isEqualTo(new Ratio(0, 6));
		assertThat(none.ratio(Metric.TARGET_PRECISION).value()).isZero();
		assertThat(none.ratio(Metric.TARGET_DIRECTION_ACCURACY)).isEqualTo(new Ratio(0, 5));
		assertThat(none.ratio(Metric.DOMINANT_DIRECTION_AGREEMENT)).isEqualTo(new Ratio(0, 2));
		assertThat(none.unmet()).contains(Metric.TARGET_RECALL, Metric.TARGET_PRECISION,
				Metric.TARGET_DIRECTION_ACCURACY, Metric.DOMINANT_DIRECTION_AGREEMENT);
	}

	@Test
	void anEmptyGoldDenominatorIsAMeasurementErrorNotAPass() {
		List<Label> onlyHard = List.of(label("p1", Relevance.INVESTMENT_RELATED, true, gold("XTB", Direction.BULLISH)));

		assertThatExceptionOfType(InsufficientGoldException.class)
			.isThrownBy(() -> GoldMetrics.compute(onlyHard, Map.of()));
		assertThatExceptionOfType(InsufficientGoldException.class)
			.isThrownBy(() -> GoldMetrics.compute(List.of(), Map.of()));
	}

	@Test
	void anAliasPairsAModelThatNamedTheSubjectDifferently() {
		List<Label> gold = List.of(label("p", Relevance.INVESTMENT_RELATED, false, gold("#ORLEN", Direction.BEARISH)));
		Map<String, AssessmentOutputV2> onlyAlias = Map.of("p",
				output(Relevance.INVESTMENT_RELATED, model("#PKN", Direction.BEARISH)));
		Map<String, AssessmentOutputV2> both = Map.of("p", output(Relevance.INVESTMENT_RELATED,
				model("#PKN", Direction.BEARISH), model("#ORLEN", Direction.BEARISH)));
		Map<String, Map<String, List<String>>> aliases = Map.of("p", Map.of("#ORLEN", List.of("#PKN")));

		assertThat(GoldMetrics.compute(gold, onlyAlias).ratio(Metric.TARGET_RECALL)).isEqualTo(new Ratio(0, 1));
		assertThat(GoldMetrics.compute(gold, onlyAlias, aliases).ratio(Metric.TARGET_RECALL)).isEqualTo(new Ratio(1, 1));
		assertThat(GoldMetrics.compute(gold, onlyAlias, aliases).ratio(Metric.TARGET_DIRECTION_ACCURACY))
			.isEqualTo(new Ratio(1, 1));
		// Two targets for one subject (§6.7): one pairs, the other stays an extra.
		assertThat(GoldMetrics.compute(gold, both, aliases).ratio(Metric.TARGET_PRECISION)).isEqualTo(new Ratio(1, 2));
	}

	@ParameterizedTest
	@CsvSource({ "CONTRACT_COMPLIANCE, 95, 100, true", "CONTRACT_COMPLIANCE, 94, 100, false",
			"TARGET_PRECISION, 70, 100, true", "TARGET_PRECISION, 69, 100, false", "CONTRADICTION_RATE, 3, 100, true",
			"CONTRADICTION_RATE, 4, 100, false", "CONTRADICTION_RATE, 0, 0, true" })
	void thresholdsAreInclusive(Metric metric, int numerator, int denominator, boolean meets) {
		assertThat(metric.meets(new Ratio(numerator, denominator))).isEqualTo(meets);
	}

	@Test
	void thresholdsAreThePlansValues() {
		assertThat(List.of(Metric.values()).stream().map(Metric::threshold).toList())
			.containsExactly(0.95, 0.80, 0.80, 0.70, 0.75, 0.75, 0.03);
		assertThat(Metric.CONTRADICTION_RATE.atLeast()).isFalse();
	}

	private static Label label(String postId, Relevance relevance, boolean hard, LabeledTarget... targets) {
		return new Label(postId, relevance, hard, "", List.of(targets));
	}

	private static LabeledTarget gold(String anchor, Direction direction) {
		return new LabeledTarget(anchor, direction, false);
	}

	private static Target model(String anchor, Direction direction) {
		Assertiveness assertiveness = direction == Direction.BULLISH || direction == Direction.BEARISH
				? Assertiveness.UNHEDGED : null;
		return ContractFixtures.target("t", anchor, direction, assertiveness);
	}

	private static AssessmentOutputV2 output(Relevance relevance, Target... targets) {
		return new AssessmentOutputV2(relevance, List.of(), List.of(targets), List.of(), List.of());
	}

}
