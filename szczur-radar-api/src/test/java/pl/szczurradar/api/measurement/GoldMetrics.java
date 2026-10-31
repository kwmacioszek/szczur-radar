package pl.szczurradar.api.measurement;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

import pl.szczurradar.api.agents.assessor.contract.AssessmentOutputV2;
import pl.szczurradar.api.agents.assessor.contract.Direction;
import pl.szczurradar.api.agents.assessor.contract.Relevance;
import pl.szczurradar.api.agents.assessor.contract.Target;
import pl.szczurradar.api.measurement.GoldLabels.Label;
import pl.szczurradar.api.measurement.GoldLabels.LabeledTarget;

/**
 * The gold-set metrics of one model, one definition for the CI replay test and the report.
 *
 * <ul>
 *   <li>Every labeled post counts. A post without a valid output (a {@code failed} result or no
 *       recording) has no relevance and no targets: it is wrong wherever it counts and never leaves
 *       a denominator.
 *   <li>Posts labeled "hard" count only for {@link Metric#CONTRACT_COMPLIANCE}.
 *   <li>Targets are paired by {@link AnchorMatcher}.
 *   <li>A metric whose gold-side denominator is empty is a measurement error
 *       ({@link InsufficientGoldException}), never a passed threshold.
 * </ul>
 */
public final class GoldMetrics {

	/**
	 * The metrics and their thresholds (plan, phase 6, criterion 6.3). Direction accuracy (was 75 %) and
	 * the contradiction rate (was 3 %) were relaxed by the user's decision of 2026-10-06 (report
	 * {@code gold-2026-10-06.md}): no measured model met them, the default keeps a margin, and closing
	 * the gap is post-MVP "LLM hardening".
	 */
	public enum Metric {

		/** Posts with a valid output / all labeled posts. */
		CONTRACT_COMPLIANCE(0.95, true),

		/** Posts whose relevance equals the gold one / posts with gold relevance other than {@code unclear}. */
		RELEVANCE_BINARY_ACCURACY(0.80, true),

		/** Matched gold targets / gold targets. */
		TARGET_RECALL(0.80, true),

		/**
		 * Matched model targets / model targets; 0 when the model returned no targets at all while the
		 * gold set has some.
		 */
		TARGET_PRECISION(0.70, true),

		/**
		 * Gold targets with a direction other than {@code not_expressed} whose matched model target has
		 * the same direction / such gold targets (unmatched = wrong). {@code unclear} and
		 * {@code not_expressed} count as the same answer — "no usable direction" (user's decision
		 * 2026-10-05, N14).
		 */
		TARGET_DIRECTION_ACCURACY(0.70, true),

		/** Posts whose model dominant direction equals the gold one / posts with a gold dominant direction. */
		DOMINANT_DIRECTION_AGREEMENT(0.75, true),

		/**
		 * Gold {@code bullish}/{@code bearish} targets whose matched model target has the opposite
		 * direction / gold {@code bullish}/{@code bearish} targets.
		 */
		CONTRADICTION_RATE(0.10, false);

		private final double threshold;

		private final boolean atLeast;

		Metric(double threshold, boolean atLeast) {
			this.threshold = threshold;
			this.atLeast = atLeast;
		}

		public double threshold() {
			return threshold;
		}

		/** {@code true}: the value must be at least the threshold; {@code false}: at most. */
		public boolean atLeast() {
			return atLeast;
		}

		public boolean meets(Ratio ratio) {
			return atLeast ? ratio.value() >= threshold : ratio.value() <= threshold;
		}

	}

	/** A metric's value with its counts; {@code 0} when the model side gave nothing to divide by. */
	public record Ratio(int numerator, int denominator) {

		public double value() {
			return denominator == 0 ? 0 : (double) numerator / denominator;
		}

	}

	private final Map<Metric, Ratio> ratios;

	private GoldMetrics(Map<Metric, Ratio> ratios) {
		this.ratios = ratios;
	}

	public Ratio ratio(Metric metric) {
		return ratios.get(metric);
	}

	public boolean meets(Metric metric) {
		return metric.meets(ratio(metric));
	}

	/** The metrics below their threshold, in {@link Metric} order. */
	public List<Metric> unmet() {
		return ratios.keySet().stream().filter(metric -> !meets(metric)).toList();
	}

	/**
	 * @param gold the user's labels, one per post
	 * @param outputs the model's valid outputs by {@code post_id}; a post missing here failed or was
	 *     not recorded
	 * @throws InsufficientGoldException if the gold set leaves a metric without a denominator
	 */
	public static GoldMetrics compute(List<Label> gold, Map<String, AssessmentOutputV2> outputs) {
		return compute(gold, outputs, Map.of());
	}

	/**
	 * @param aliases other names of a gold target, by post and anchor ({@code gold/aliases.json}): the
	 *     same subject named differently in the post (§6.7), e.g. {@code #PKN} for {@code #ORLEN}
	 */
	public static GoldMetrics compute(List<Label> gold, Map<String, AssessmentOutputV2> outputs,
			Map<String, Map<String, List<String>>> aliases) {
		int valid = 0;
		int relevanceRight = 0;
		int relevanceTotal = 0;
		int goldTargets = 0;
		int modelTargets = 0;
		int matched = 0;
		int directionRight = 0;
		int directionTotal = 0;
		int dominantRight = 0;
		int dominantTotal = 0;
		int contradictions = 0;
		int polarTotal = 0;
		for (Label label : gold) {
			AssessmentOutputV2 output = outputs.get(label.postId());
			if (output != null) {
				valid++;
			}
			if (label.hard()) {
				continue;
			}
			if (label.relevance() != Relevance.UNCLEAR) {
				relevanceTotal++;
				if (output != null && output.relevance() == label.relevance()) {
					relevanceRight++;
				}
			}
			List<LabeledTarget> labeled = label.targets();
			List<Target> answered = output == null ? List.of() : output.targets();
			goldTargets += labeled.size();
			modelTargets += answered.size();
			Target[] matchOf = new Target[labeled.size()];
			Map<String, List<String>> postAliases = aliases.getOrDefault(label.postId(), Map.of());
			List<List<String>> names = labeled.stream()
				.map(target -> Stream.concat(Stream.of(target.anchor()),
						postAliases.getOrDefault(target.anchor(), List.of()).stream()).toList())
				.toList();
			for (AnchorMatcher.Pair pair : AnchorMatcher.matchNames(names,
					answered.stream().map(target -> target.anchor().quote()).toList())) {
				matchOf[pair.gold()] = answered.get(pair.model());
				matched++;
			}
			for (int i = 0; i < labeled.size(); i++) {
				Direction expected = labeled.get(i).direction();
				Direction actual = matchOf[i] == null ? null : matchOf[i].stance().direction();
				if (expected != Direction.NOT_EXPRESSED) {
					directionTotal++;
					if (sameDirection(expected, actual)) {
						directionRight++;
					}
				}
				if (expected == Direction.BULLISH || expected == Direction.BEARISH) {
					polarTotal++;
					if (actual != null && actual == opposite(expected)) {
						contradictions++;
					}
				}
			}
			Optional<Direction> expectedDominant = dominantDirection(labeled, LabeledTarget::direction);
			if (expectedDominant.isPresent()) {
				dominantTotal++;
				if (expectedDominant.equals(dominantDirection(answered, target -> target.stance().direction()))) {
					dominantRight++;
				}
			}
		}
		Map<Metric, Ratio> ratios = new EnumMap<>(Metric.class);
		ratios.put(Metric.CONTRACT_COMPLIANCE, ratio(Metric.CONTRACT_COMPLIANCE, valid, gold.size()));
		ratios.put(Metric.RELEVANCE_BINARY_ACCURACY,
				ratio(Metric.RELEVANCE_BINARY_ACCURACY, relevanceRight, relevanceTotal));
		ratios.put(Metric.TARGET_RECALL, ratio(Metric.TARGET_RECALL, matched, goldTargets));
		requireGold(Metric.TARGET_PRECISION, goldTargets);
		ratios.put(Metric.TARGET_PRECISION, new Ratio(matched, modelTargets));
		ratios.put(Metric.TARGET_DIRECTION_ACCURACY,
				ratio(Metric.TARGET_DIRECTION_ACCURACY, directionRight, directionTotal));
		ratios.put(Metric.DOMINANT_DIRECTION_AGREEMENT,
				ratio(Metric.DOMINANT_DIRECTION_AGREEMENT, dominantRight, dominantTotal));
		ratios.put(Metric.CONTRADICTION_RATE, ratio(Metric.CONTRADICTION_RATE, contradictions, polarTotal));
		return new GoldMetrics(ratios);
	}

	/**
	 * More {@code bullish} than {@code bearish} targets → {@code bullish}, fewer → {@code bearish},
	 * equal → none.
	 */
	static <T> Optional<Direction> dominantDirection(List<T> targets, Function<T, Direction> direction) {
		long bullish = targets.stream().filter(target -> direction.apply(target) == Direction.BULLISH).count();
		long bearish = targets.stream().filter(target -> direction.apply(target) == Direction.BEARISH).count();
		if (bullish > bearish) {
			return Optional.of(Direction.BULLISH);
		}
		if (bearish > bullish) {
			return Optional.of(Direction.BEARISH);
		}
		return Optional.empty();
	}

	/** Equal, or both "no usable direction" ({@code unclear} / {@code not_expressed}). */
	static boolean sameDirection(Direction expected, @Nullable Direction actual) {
		return expected == actual || (noDirection(expected) && actual != null && noDirection(actual));
	}

	static boolean noDirection(Direction direction) {
		return direction == Direction.UNCLEAR || direction == Direction.NOT_EXPRESSED;
	}

	static @Nullable Direction opposite(Direction direction) {
		return switch (direction) {
			case BULLISH -> Direction.BEARISH;
			case BEARISH -> Direction.BULLISH;
			default -> null;
		};
	}

	private static Ratio ratio(Metric metric, int numerator, int goldDenominator) {
		requireGold(metric, goldDenominator);
		return new Ratio(numerator, goldDenominator);
	}

	private static void requireGold(Metric metric, int goldCount) {
		if (goldCount == 0) {
			throw new InsufficientGoldException("the gold set has nothing to measure " + metric + " on");
		}
	}

}
