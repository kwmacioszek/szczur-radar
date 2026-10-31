package pl.szczurradar.api.measurement;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import pl.szczurradar.api.agents.assessor.AssessmentResult;
import pl.szczurradar.api.measurement.ModelsConfig.Model;
import pl.szczurradar.api.measurement.ModelsConfig.Price;
import pl.szczurradar.api.posts.Post;

/**
 * The pure logic behind the numbers of {@link RedditReport} (S-28), apart from any file or network, so
 * that {@code RedditMeasuresTest} can pin it: Wilson interval, length buckets, the megathread pattern,
 * the language of a thesis, when a post is finished, cost.
 */
final class RedditMeasures {

	/** A day of the sample is a Warsaw calendar day, as a run's range is. */
	static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");

	/** A post is {@code dead} after its third failed try, as in production. */
	static final int MAX_TRIES = Post.MAX_ASSESSMENT_ATTEMPTS;

	static final int DAYS_PER_MONTH = 30;

	private static final double Z = 1.96;

	/** The pilot's pattern (pilot 5): the subreddits' recurring threads. */
	private static final Pattern MEGATHREAD = Pattern.compile("(daily|weekly).*(discussion|thread)|thread on meme stocks",
			Pattern.CASE_INSENSITIVE);

	private static final Pattern POLISH_LETTER = Pattern.compile("[ąćęłńóśźż]",
			Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

	private static final Pattern WORD = Pattern.compile("\\p{L}+");

	/** Polish function words; never {@code a}, {@code i}, {@code to}, {@code on}, {@code no}, {@code by}, {@code ma}. */
	private static final Set<String> POLISH_WORDS = Set.of("że", "się", "nie", "jest", "oraz", "ale", "czy", "będzie",
			"które", "jednak", "dla", "przez", "jako", "lub");

	private static final int MIN_POLISH_WORDS = 2;

	private RedditMeasures() {
	}

	/** A proportion's bounds, 0–1. */
	record Interval(double low, double high) {
	}

	/** The Wilson interval of {@code successes} out of {@code total}, which is not 0; z = 1.96. */
	static Interval wilson(int successes, int total) {
		double p = (double) successes / total;
		double z2 = Z * Z;
		double denominator = 1 + z2 / total;
		double centre = (p + z2 / (2.0 * total)) / denominator;
		double half = Z * Math.sqrt(p * (1 - p) / total + z2 / (4.0 * total * total)) / denominator;
		return new Interval(centre - half, centre + half);
	}

	/** The length of a post's {@code body} in characters, in the report's three buckets. */
	enum LengthBucket {

		SHORT("< 1000"), MEDIUM("1000–2999"), LONG("3000+");

		private final String label;

		LengthBucket(String label) {
			this.label = label;
		}

		String label() {
			return label;
		}

		static LengthBucket of(int length) {
			if (length < 1000) {
				return SHORT;
			}
			return length < 3000 ? MEDIUM : LONG;
		}

	}

	static boolean isMegathread(@Nullable String title) {
		return title != null && MEGATHREAD.matcher(title).find();
	}

	/** The language of a thesis ({@code Claim.statement}), a rough rule checked by hand in the report. */
	enum ThesisLanguage {

		POLISH, ENGLISH;

		/** Polish with a Polish letter or at least two Polish function words, otherwise English. */
		static ThesisLanguage of(String thesis) {
			if (POLISH_LETTER.matcher(thesis).find()) {
				return POLISH;
			}
			Matcher word = WORD.matcher(thesis.toLowerCase(Locale.ROOT));
			int polishWords = 0;
			while (word.find()) {
				polishWords += POLISH_WORDS.contains(word.group()) ? 1 : 0;
			}
			return polishWords >= MIN_POLISH_WORDS ? POLISH : ENGLISH;
		}

	}

	/** The languages of one post's theses. */
	enum ThesisMix {

		ENGLISH_ONLY, MIXED, POLISH_ONLY, NO_THESES;

		static ThesisMix of(List<String> theses) {
			if (theses.isEmpty()) {
				return NO_THESES;
			}
			long polish = theses.stream().filter(thesis -> ThesisLanguage.of(thesis) == ThesisLanguage.POLISH).count();
			if (polish == 0) {
				return ENGLISH_ONLY;
			}
			return polish == theses.size() ? POLISH_ONLY : MIXED;
		}

	}

	/** The lines of {@code recordings.jsonl} by post, each post's in the order they were written. */
	static Map<String, List<Recording>> byPost(List<Recording> lines) {
		Map<String, List<Recording>> byPost = new LinkedHashMap<>();
		lines.forEach(line -> byPost.computeIfAbsent(line.postId(), id -> new ArrayList<>()).add(line));
		return byPost;
	}

	/** Whether a post with these lines needs no further try: it has an {@code ok} line or used up its tries. */
	static boolean finished(List<Recording> lines) {
		return lines.size() >= MAX_TRIES
				|| lines.stream().anyMatch(line -> AssessmentResult.Status.OK.value().equals(line.status()));
	}

	/** What a line cost at its recorded channel; a call without an answer is not billed. */
	static double cost(Model prices, Recording line) {
		String channel = GoldReport.billingChannel(prices, line);
		return channel == null ? 0 : cost(prices.prices().get(channel), line);
	}

	/** What a line would have cost at {@code price}. */
	static double cost(Price price, Recording line) {
		return price.cost(line.inputTokens(), line.cachedTokens(), line.outputTokens());
	}

	/** {@value #DAYS_PER_MONTH} × the mean posts to assess a day × the mean cost of a post with its tries. */
	static double monthlyCost(int posts, int days, double costPerPost) {
		return DAYS_PER_MONTH * ((double) posts / days) * costPerPost;
	}

	/** The median; 0 for no values. */
	static double median(List<Integer> values) {
		if (values.isEmpty()) {
			return 0;
		}
		int[] sorted = values.stream().mapToInt(Integer::intValue).toArray();
		Arrays.sort(sorted);
		int middle = sorted.length / 2;
		return sorted.length % 2 == 1 ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2.0;
	}

}
