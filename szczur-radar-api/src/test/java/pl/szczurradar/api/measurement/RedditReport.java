package pl.szczurradar.api.measurement;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;

import pl.szczurradar.api.agents.assessor.AssessmentResult;
import pl.szczurradar.api.agents.assessor.contract.AssessmentOutputV2;
import pl.szczurradar.api.agents.assessor.contract.Claim;
import pl.szczurradar.api.agents.assessor.contract.ContractJson;
import pl.szczurradar.api.agents.assessor.validation.FailureClass;
import pl.szczurradar.api.agents.assessor.validation.Violation;
import pl.szczurradar.api.measurement.GoldLabels.Label;
import pl.szczurradar.api.measurement.GoldLabels.Post;
import pl.szczurradar.api.measurement.GoldMetrics.Metric;
import pl.szczurradar.api.measurement.ModelsConfig.Model;
import pl.szczurradar.api.measurement.ModelsConfig.Price;
import pl.szczurradar.api.measurement.RedditMeasures.Interval;
import pl.szczurradar.api.measurement.RedditMeasures.LengthBucket;
import pl.szczurradar.api.measurement.RedditMeasures.Review;
import pl.szczurradar.api.measurement.RedditMeasures.Reviewed;
import pl.szczurradar.api.measurement.RedditMeasures.ThesisLanguage;
import pl.szczurradar.api.measurement.RedditMeasures.ThesisMix;
import pl.szczurradar.api.measurement.RedditSampler.Volume;

/**
 * Writes the S-28 report from the files of {@code reddit.dir}, no model calls: every line of
 * {@code recordings.jsonl} is judged again with today's code ({@link Replay#judge}). Fails when a post of
 * {@code posts.json} has neither an {@code ok} line nor three lines, rather than count from a hole.
 *
 * <pre>
 * ./mvnw -Pmeasurement test -Dtest=RedditReport -Dreddit.dir=&lt;path&gt; -Dreport.out=&lt;path&gt;
 * </pre>
 *
 * Optional: {@code -Dreddit.price=luna}, the configuration of {@code models.json} whose prices count. Two
 * files of {@code reddit.dir} are optional too: {@code labels.json}, the owner's review on the sheet of
 * {@link RedditSheet}, adds its section, and {@code report-notes.md}, hand-written notes a regenerated report
 * must keep, goes in verbatim before the definitions.
 */
@Tag("measurement")
class RedditReport {

	static final String WYKOP_USAGE = "wykop-usage.json";

	static final String LABELS = "labels.json";

	static final String NOTES = "report-notes.md";

	/** The monthly limit on the LLM key, shared with Wykop. */
	private static final double KEY_LIMIT_USD = 10;

	/** A point of reference for the whole run; the recorder's fuse is higher. */
	private static final double RUN_REFERENCE_USD = 2;

	private static final String FLEX_CHANNEL = "openai/flex";

	private static final String DEFAULT_CHANNEL = "openrouter";

	private static final Map<ThesisMix, String> MIX_LABELS = new EnumMap<>(Map.of(ThesisMix.ENGLISH_ONLY,
			"tylko po angielsku", ThesisMix.MIXED, "mieszane", ThesisMix.POLISH_ONLY, "tylko po polsku",
			ThesisMix.NO_THESES, "bez tez"));

	/**
	 * One row of {@code wykop-usage.json}, the read-only production aggregate: one row per day and per
	 * ({@code channel}, {@code service_tier}, {@code flex_fallback}), the channel because it is the price key.
	 *
	 * @param channel {@code null} for calls without an answer, which have no tokens
	 * @param ok assessments {@code ok} in this row
	 * @param failed assessments {@code failed} in this row
	 * @param inputTokens sum, 0 when none was reported
	 * @param cachedTokens sum, 0 when none was reported
	 * @param outputTokens sum, 0 when none was reported
	 * @param wykopPosts Wykop posts of the day: the same in every row of a day
	 */
	@JsonPropertyOrder({ "day", "channel", "service_tier", "flex_fallback", "ok", "failed", "input_tokens",
			"cached_tokens", "output_tokens", "wykop_posts" })
	record WykopUsage(LocalDate day, @Nullable String channel, @JsonProperty("service_tier") @Nullable String serviceTier,
			@JsonProperty("flex_fallback") boolean flexFallback, int ok, int failed,
			@JsonProperty("input_tokens") int inputTokens, @JsonProperty("cached_tokens") int cachedTokens,
			@JsonProperty("output_tokens") int outputTokens, @JsonProperty("wykop_posts") int wykopPosts) {

		double cost(Price price) {
			return price.cost(inputTokens, cachedTokens, outputTokens);
		}

		/** The cost at the row's own channel. */
		double cost(Model prices) {
			return channel == null ? 0
					: cost(Objects.requireNonNull(prices.prices().get(channel), "no price for channel " + channel));
		}

	}

	/** A post with its lines, in order, and today's verdict on each. */
	record Assessed(Post post, List<Recording> lines, List<AssessmentResult> results) {

		/**
		 * Every post of {@code posts} with its {@code lines}, each judged again. Fails on lines of other posts
		 * and on a post with neither an {@code ok} line nor three lines: the sample is incomplete.
		 */
		static List<Assessed> all(List<Post> posts, List<Recording> lines) {
			Map<String, List<Recording>> byPost = RedditMeasures.byPost(lines);
			Set<String> ids = posts.stream().map(Post::postId).collect(Collectors.toSet());
			assertThat(byPost.keySet()).as("lines of posts outside posts.json").isSubsetOf(ids);
			assertThat(posts.stream().filter(post -> !RedditMeasures.finished(byPost.getOrDefault(post.postId(), List.of())))
				.map(Post::postId)
				.toList()).as("posts without an ok line or three lines: the sample is incomplete").isEmpty();
			List<Assessed> assessed = new ArrayList<>();
			for (Post post : posts) {
				List<Recording> ofPost = byPost.get(post.postId());
				assessed.add(new Assessed(post, ofPost, ofPost.stream().map(line -> Replay.judge(post, line)).toList()));
			}
			return assessed;
		}

		boolean firstOk() {
			return results.getFirst().ok();
		}

		Optional<AssessmentOutputV2> output() {
			return results.stream().filter(AssessmentResult::ok).findFirst().map(AssessmentResult::output);
		}

		boolean ok() {
			return output().isPresent();
		}

		long changedVerdicts() {
			return IntStream.range(0, lines.size()).filter(i -> !lines.get(i).sameVerdict(results.get(i))).count();
		}

		boolean megathread() {
			return RedditMeasures.isMegathread(post.input().item().title());
		}

		LengthBucket bucket() {
			String body = post.input().item().body();
			return LengthBucket.of(body == null ? 0 : body.length());
		}

	}

	@Test
	void writesTheReport() throws IOException {
		Path dir = Path.of(Objects.requireNonNull(System.getProperty("reddit.dir"), "-Dreddit.dir=<path> is required"));
		Path out = Path.of(Objects.requireNonNull(System.getProperty("report.out"), "-Dreport.out=<path> is required"));
		String priceId = System.getProperty("reddit.price", "luna");
		Model prices = ModelsConfig.read(ModelsConfig.FILE).model(priceId).orElseThrow();
		List<Post> posts = GoldLabels.readPosts(dir.resolve(RedditSampler.POSTS));
		List<Volume> volume = ContractJson.MAPPER.readValue(Files.readString(dir.resolve(RedditSampler.VOLUME)),
				new TypeReference<List<Volume>>() {
				});
		List<WykopUsage> usage = ContractJson.MAPPER.readValue(Files.readString(dir.resolve(WYKOP_USAGE)),
				new TypeReference<List<WykopUsage>>() {
				});
		List<Recording> lines = RedditRecorder.readRecordings(dir.resolve(RedditRecorder.RECORDINGS));
		List<Assessed> assessed = Assessed.all(posts, lines);

		StringBuilder md = new StringBuilder();
		md.append("# Raport Reddita — ").append(LocalDate.now(RedditMeasures.ZONE)).append("\n\n");
		md.append("- Prompt: ").append(distinct(lines, Recording::promptVersion)).append(", kontrakt: ")
			.append(distinct(lines, Recording::contractVersion)).append('\n');
		md.append("- Konfiguracja: ").append(distinct(lines, Recording::configId)).append(", model ")
			.append(distinct(lines, Recording::model)).append(", tryb ").append(distinct(lines, Recording::serviceTier))
			.append(", reasoning ").append(distinct(lines, Recording::reasoningEffort)).append('\n');
		md.append("- Ceny: `").append(prices.id()).append("` z `models.json`\n");
		md.append("- Próby, których werdykt dzisiejszego kodu różni się od zapisanego: ")
			.append(assessed.stream().mapToLong(Assessed::changedVerdicts).sum()).append("\n\n");

		appendSample(md, posts, volume);
		appendAssessments(md, assessed);
		appendFailures(md, assessed);
		appendLengths(md, assessed, prices);
		appendMegathreads(md, assessed, prices);
		appendLanguages(md, assessed);
		appendCost(md, posts, lines, volume, usage, prices);
		appendReference(md, assessed);
		if (Files.exists(dir.resolve(LABELS))) {
			appendReview(md, assessed, GoldLabels.readLabels(dir.resolve(LABELS)));
		}
		if (Files.exists(dir.resolve(NOTES))) {
			md.append(Files.readString(dir.resolve(NOTES))).append('\n');
		}
		md.append(LEGEND);

		Files.createDirectories(out.toAbsolutePath().getParent());
		Files.writeString(out, md.toString());
		System.out.println("wrote " + out.toAbsolutePath());
	}

	private static void appendSample(StringBuilder md, List<Post> posts, List<Volume> volume) {
		List<LocalDate> days = volume.stream().map(Volume::day).distinct().sorted().toList();
		md.append("## Próbka i wolumen\n\n");
		md.append("- Dni: ").append(days.size()).append(" (").append(days.getFirst()).append(" – ").append(days.getLast())
			.append("), postów do oceny w próbce: ").append(posts.size()).append("\n\n");
		md.append("| Subreddit | Pobrane | Zdjęte | Pominięte | Do oceny | Do oceny dziennie |\n|---|---|---|---|---|---|\n");
		Map<String, List<Volume>> bySubreddit = volume.stream()
			.collect(Collectors.groupingBy(Volume::subreddit, TreeMap::new, Collectors.toList()));
		bySubreddit.forEach((subreddit, rows) -> appendVolume(md, "r/" + subreddit, rows, days.size()));
		appendVolume(md, "razem", volume, days.size());
		md.append('\n');
	}

	private static void appendVolume(StringBuilder md, String label, List<Volume> rows, int days) {
		int assessable = rows.stream().mapToInt(Volume::assessable).sum();
		md.append("| ").append(label).append(" | ").append(rows.stream().mapToInt(Volume::fetched).sum()).append(" | ")
			.append(rows.stream().mapToInt(Volume::removed).sum()).append(" | ")
			.append(rows.stream().mapToInt(Volume::skipped).sum()).append(" | ").append(assessable).append(" | ")
			.append(String.format(Locale.ROOT, "%.1f", (double) assessable / days)).append(" |\n");
	}

	private static void appendAssessments(StringBuilder md, List<Assessed> assessed) {
		int total = assessed.size();
		int first = count(assessed, Assessed::firstOk);
		int ok = count(assessed, Assessed::ok);
		md.append("## Oceny\n\n");
		md.append("- Ocena po pierwszej próbie: ").append(share(first, total)).append('\n');
		md.append("- Ocena po trzech próbach: ").append(share(ok, total)).append('\n');
		md.append("- `dead`: ").append(share(total - ok, total)).append("\n\n");
		md.append("`dead` jest tu przybliżeniem: na produkcji trzy próby rozkładają się na kolejne noce (RETRY przed ")
			.append("RANGE), w pomiarze idą od razu po sobie. Dostawca niedostępny po ponowieniach to tu nieudana próba, ")
			.append("a na produkcji post wraca bez zużycia próby.\n\n");
	}

	private static void appendFailures(StringBuilder md, List<Assessed> assessed) {
		List<AssessmentResult> tries = assessed.stream().flatMap(post -> post.results().stream()).toList();
		List<AssessmentResult> failed = tries.stream().filter(result -> !result.ok()).toList();
		md.append("## Klasy niepowodzeń\n\n");
		md.append("Nieudane próby: ").append(failed.size()).append(" z ").append(tries.size())
			.append(". Jedna próba może złamać kilka reguł; każda reguła liczy się w niej raz.\n\n");
		md.append("| Klasa | Próby | Reguły (próby) |\n|---|---|---|\n");
		for (FailureClass failureClass : FailureClass.values()) {
			List<AssessmentResult> ofClass = failed.stream().filter(result -> result.failureClass() == failureClass).toList();
			Map<String, Long> rules = ofClass.stream()
				.flatMap(result -> result.violations().stream().map(Violation::rule).distinct())
				.collect(Collectors.groupingBy(Function.identity(), TreeMap::new, Collectors.counting()));
			md.append("| `").append(failureClass.value()).append("` | ").append(ofClass.size()).append(" | ")
				.append(rules.entrySet().stream()
					.map(rule -> "`" + rule.getKey() + "` " + rule.getValue())
					.collect(Collectors.joining(", ")))
				.append(" |\n");
		}
		md.append('\n');
	}

	private static void appendLengths(StringBuilder md, List<Assessed> assessed, Model prices) {
		md.append("## Przedziały długości\n\n");
		appendGroupHeader(md, "Długość `body` (znaki)");
		for (LengthBucket bucket : LengthBucket.values()) {
			appendGroup(md, bucket.label(), assessed.stream().filter(post -> post.bucket() == bucket).toList(), prices);
		}
		appendGroup(md, "wszystkie", assessed, prices);
		md.append('\n');
	}

	private static void appendMegathreads(StringBuilder md, List<Assessed> assessed, Model prices) {
		md.append("## Megawątki\n\n");
		appendGroupHeader(md, "Posty");
		appendGroup(md, "z megawątkami", assessed, prices);
		appendGroup(md, "bez megawątków", assessed.stream().filter(post -> !post.megathread()).toList(), prices);
		appendGroup(md, "same megawątki", assessed.stream().filter(Assessed::megathread).toList(), prices);
		md.append('\n');
	}

	private static void appendGroupHeader(StringBuilder md, String first) {
		md.append("| ").append(first).append(" | Posty | Ocena po pierwszej próbie | Ocena po trzech próbach | `dead` ")
			.append("| Mediana celów | Tokeny wejścia / wyjścia na próbę | Koszt (kanał nagrania) |\n")
			.append("|---|---|---|---|---|---|---|---|\n");
	}

	private static void appendGroup(StringBuilder md, String label, List<Assessed> posts, Model prices) {
		int total = posts.size();
		int ok = count(posts, Assessed::ok);
		List<Recording> lines = posts.stream().flatMap(post -> post.lines().stream()).toList();
		List<Integer> targets = posts.stream()
			.flatMap(post -> post.output().stream())
			.map(output -> output.targets().size())
			.toList();
		int tries = Math.max(1, lines.size());
		md.append("| ").append(label).append(" | ").append(total).append(" | ")
			.append(share(count(posts, Assessed::firstOk), total)).append(" | ").append(share(ok, total)).append(" | ")
			.append(share(total - ok, total)).append(" | ")
			.append(String.format(Locale.ROOT, "%.1f", RedditMeasures.median(targets))).append(" | ")
			.append(tokens(lines, Recording::inputTokens) / tries).append(" / ")
			.append(tokens(lines, Recording::outputTokens) / tries).append(" | ")
			.append(usd(sum(lines, line -> RedditMeasures.cost(prices, line)))).append(" |\n");
	}

	private static void appendLanguages(StringBuilder md, List<Assessed> assessed) {
		List<List<String>> theses = assessed.stream()
			.flatMap(post -> post.output().stream())
			.map(output -> output.claims().stream().map(Claim::statement).toList())
			.toList();
		List<String> all = theses.stream().flatMap(List::stream).toList();
		int polish = (int) all.stream().filter(thesis -> ThesisLanguage.of(thesis) == ThesisLanguage.POLISH).count();
		md.append("## Język tez\n\n");
		md.append("Posty z oceną: ").append(theses.size()).append("; tez po polsku: ").append(share(polish, all.size()))
			.append(". Klasyfikacja jest regułą zgrubną (definicje na końcu).\n\n");
		md.append("| Tezy posta | Posty |\n|---|---|\n");
		for (ThesisMix mix : ThesisMix.values()) {
			int posts = (int) theses.stream().filter(post -> ThesisMix.of(post) == mix).count();
			md.append("| ").append(MIX_LABELS.get(mix)).append(" | ").append(share(posts, theses.size())).append(" |\n");
		}
		md.append('\n');
	}

	private static void appendCost(StringBuilder md, List<Post> posts, List<Recording> lines, List<Volume> volume,
			List<WykopUsage> usage, Model prices) {
		Price flex = prices.prices().get(FLEX_CHANNEL);
		Price standard = prices.prices().get(DEFAULT_CHANNEL);
		int fallbacks = (int) lines.stream().filter(Recording::flexFallback).count();
		int assessable = volume.stream().mapToInt(Volume::assessable).sum();
		int days = (int) volume.stream().map(Volume::day).distinct().count();
		Map<LocalDate, Integer> wykopPosts = new TreeMap<>();
		usage.forEach(row -> wykopPosts.put(row.day(), row.wykopPosts()));
		int wykopDays = Math.max(1, wykopPosts.size());
		int wykopCalls = usage.stream().mapToInt(row -> row.ok() + row.failed()).sum();
		int wykopFallbacks = usage.stream().filter(WykopUsage::flexFallback).mapToInt(row -> row.ok() + row.failed()).sum();
		double run = sum(lines, line -> RedditMeasures.cost(prices, line));

		md.append("## Koszt\n\n");
		md.append("- Tokeny przebiegu: wejście ").append(tokens(lines, Recording::inputTokens)).append(" (z cache ")
			.append(tokens(lines, Recording::cachedTokens)).append("), wyjście ")
			.append(tokens(lines, Recording::outputTokens)).append(", prób ").append(lines.size()).append(", postów ")
			.append(posts.size()).append('\n');
		md.append("- Powrót z flex: ").append(share(fallbacks, lines.size())).append(" prób Reddita; ")
			.append(share(wykopFallbacks, wykopCalls)).append(" ocen Wykopa\n");
		md.append("- Koszt przebiegu w kanałach nagrania: ").append(usd(run)).append(" (wartość odniesienia ")
			.append(usd(RUN_REFERENCE_USD)).append(")\n");
		md.append("- Wykop: ").append(wykopPosts.size()).append(" dni z `").append(WYKOP_USAGE).append("`, średnio ")
			.append(String.format(Locale.ROOT, "%.1f", wykopPosts.values().stream().mapToInt(Integer::intValue).sum()
					/ (double) wykopDays))
			.append(" postów dziennie, ").append(wykopCalls).append(" ocen\n\n");
		md.append("| Ceny | Przebieg | Na post | Reddit / mies. | Wykop / mies. | Razem / mies. | Udział w limicie ")
			.append(usd(KEY_LIMIT_USD)).append(" |\n|---|---|---|---|---|---|---|\n");
		appendCostRow(md, "`" + FLEX_CHANNEL + "`", sum(lines, line -> RedditMeasures.cost(flex, line)),
				sum(usage, row -> row.cost(flex)), posts.size(), assessable, days, wykopDays);
		appendCostRow(md, "`" + DEFAULT_CHANNEL + "`", sum(lines, line -> RedditMeasures.cost(standard, line)),
				sum(usage, row -> row.cost(standard)), posts.size(), assessable, days, wykopDays);
		appendCostRow(md, "kanały nagrania", run, sum(usage, row -> row.cost(prices)), posts.size(), assessable, days,
				wykopDays);
		md.append('\n');
	}

	private static void appendCostRow(StringBuilder md, String label, double run, double wykop, int posts, int assessable,
			int days, int wykopDays) {
		double perPost = run / Math.max(1, posts);
		double redditMonthly = RedditMeasures.monthlyCost(assessable, days, perPost);
		double wykopMonthly = RedditMeasures.DAYS_PER_MONTH * wykop / wykopDays;
		double monthly = redditMonthly + wykopMonthly;
		md.append("| ").append(label).append(" | ").append(usd(run)).append(" | ").append(usd(perPost)).append(" | ")
			.append(usd(redditMonthly)).append(" | ").append(usd(wykopMonthly)).append(" | ").append(usd(monthly)).append(" | ")
			.append(String.format(Locale.ROOT, "%.0f %%", monthly / KEY_LIMIT_USD * 100)).append(" |\n");
	}

	private static void appendReference(StringBuilder md, List<Assessed> assessed) {
		md.append("## Odniesienie do progów Wykopu\n\n");
		md.append("Progi są tylko punktem odniesienia, ta próbka ich nie rozstrzyga. Zgodność z kontraktem: próg ≥ ")
			.append(percent(Metric.CONTRACT_COMPLIANCE.threshold())).append(" na Wykopie, tu po pierwszej próbie ")
			.append(share(count(assessed, Assessed::firstOk), assessed.size())).append(". Kierunek celów (≥ ")
			.append(percent(Metric.TARGET_DIRECTION_ACCURACY.threshold())).append(") i sprzeczne kierunki (≤ ")
			.append(percent(Metric.CONTRADICTION_RATE.threshold()))
			.append(") wymagają etykiet człowieka bez podpowiedzi modelu i nie są tu liczone wobec progów.\n\n");
	}

	private static void appendReview(StringBuilder md, List<Assessed> assessed, List<Label> labels) {
		Map<String, Assessed> byId = assessed.stream()
			.filter(Assessed::ok)
			.collect(Collectors.toMap(post -> post.post().postId(), Function.identity()));
		assertThat(labels).extracting(Label::postId).as("labels of posts without a valid assessment")
			.isSubsetOf(byId.keySet());
		List<Reviewed> reviewed = labels.stream()
			.map(label -> new Reviewed(label, byId.get(label.postId()).output().orElseThrow().targets()))
			.toList();

		md.append("## Pominięte cele (ocena właściciela)\n\n");
		md.append("Przejrzane posty: ").append(labels.size()).append(", z tego „trudne”, poza mianownikami: ")
			.append(labels.stream().filter(Label::hard).count())
			.append(". Mianownikiem są tylko posty przejrzane, nigdy cała kolejka arkusza.\n\n");
		md.append("| Długość `body` (znaki) | Posty | Posty z pominiętym celem | Braki na post | Pominięte wśród celów ")
			.append("właściciela | Cele modelu usunięte | Odwrotny kierunek | Stanowisko, którego właściciel nie widzi |\n")
			.append("|---|---|---|---|---|---|---|---|\n");
		for (LengthBucket bucket : LengthBucket.values()) {
			appendReviewRow(md, bucket.label(),
					reviewed.stream().filter(post -> byId.get(post.label().postId()).bucket() == bucket).toList());
		}
		appendReviewRow(md, "wszystkie", reviewed);
		md.append('\n').append(REVIEW_NOTES).append('\n');
	}

	private static void appendReviewRow(StringBuilder md, String label, List<Reviewed> posts) {
		Review review = RedditMeasures.review(posts);
		md.append("| ").append(label).append(" | ").append(review.posts()).append(" | ")
			.append(share(review.postsWithMiss(), review.posts())).append(" | ")
			.append(review.posts() == 0 ? "—" : String.format(Locale.ROOT, "%.2f (%d/%d)",
					(double) review.missed() / review.posts(), review.missed(), review.posts()))
			.append(" | ").append(share(review.missed(), review.ownerTargets())).append(" | ")
			.append(share(review.removed(), review.modelTargets())).append(" | ")
			.append(share(review.reversed(), review.polarTargets())).append(" | ")
			.append(share(review.unseenStances(), review.noStanceTargets())).append(" |\n");
	}

	private static int count(List<Assessed> posts, Predicate<Assessed> filter) {
		return (int) posts.stream().filter(filter).count();
	}

	private static <T> double sum(List<T> values, ToDoubleFunction<T> field) {
		return values.stream().mapToDouble(field).sum();
	}

	private static long tokens(List<Recording> lines, Function<Recording, @Nullable Integer> field) {
		return lines.stream().map(field).filter(Objects::nonNull).mapToLong(Integer::longValue).sum();
	}

	private static String distinct(List<Recording> lines, Function<Recording, @Nullable String> field) {
		String values = lines.stream().map(field).filter(Objects::nonNull).distinct().sorted()
			.map(value -> "`" + value + "`").collect(Collectors.joining(", "));
		return values.isEmpty() ? "—" : values;
	}

	/** {@code part / total} with its counts and Wilson interval. */
	private static String share(int part, int total) {
		if (total == 0) {
			return "—";
		}
		Interval interval = RedditMeasures.wilson(part, total);
		return String.format(Locale.ROOT, "%.1f %% (%d/%d; %.1f–%.1f %%)", 100.0 * part / total, part, total,
				interval.low() * 100, interval.high() * 100);
	}

	private static String percent(double value) {
		return String.format(Locale.ROOT, "%.0f %%", value * 100);
	}

	private static String usd(double value) {
		return String.format(Locale.ROOT, "$%.4f", value);
	}

	private static final String REVIEW_NOTES = """
			Arkusz był wypełniony odpowiedzią modelu, więc ta podpowiedź może zawyżać wynik modelu: łatwiej przyjąć gotowy cel, niż zauważyć brak. Kolejka ma celowo większy udział długich postów i nie ma w niej megawątków ani postów `dead`, więc wiersz „wszystkie” nie opisuje całej próbki. Cele z jednego posta nie są od siebie niezależne, więc widełki miar liczonych na celach są raczej za wąskie.

			- **Para:** cel właściciela i cel modelu połączone jeden do jednego przez `AnchorMatcher`, bez aliasów.
			- **Pominięty cel:** cel właściciela bez pary.
			- **Braki na post:** pominięte cele / posty. Może przekroczyć 1, więc to nie odsetek i nie ma widełek Wilsona; ma je kolumna obok, pominięte cele / cele właściciela.
			- **Cel modelu usunięty:** cel modelu bez pary / cele modelu.
			- **Odwrotny kierunek:** cele właściciela `bullish` lub `bearish`, których para ma kierunek przeciwny / wszystkie takie cele; cel bez pary nie jest odwrotny (miara „sprzeczne kierunki” z `GoldMetrics`).
			- **Stanowisko, którego właściciel nie widzi:** cele właściciela `not_expressed` lub `unclear`, których para ma inny kierunek niż te dwa / wszystkie takie cele; cel bez pary się do licznika nie liczy.
			""";

	private static final String LEGEND = """
			## Definicje

			- **Dzień:** dzień kalendarzowy czasu warszawskiego.
			- **Próba:** jedno wywołanie `Assessor.assess`, czyli jedna linia `recordings.jsonl`. Ponowienia transportu wewnątrz wywołania to pole `attempts` tej linii, nie próba.
			- **`dead`:** post bez poprawnej oceny po trzech próbach.
			- **Koszt miesięczny:** 30 × średnia dzienna liczba postów do oceny × średni koszt posta wraz z ponowieniami. Wykop: 30 × średni dzienny koszt z `wykop-usage.json`.
			- **Przedziały długości:** `body` poniżej 1000, 1000–2999 i 3000+ znaków.
			- **Megawątek:** tytuł pasujący do wzorca pilota `(daily|weekly).*(discussion|thread)|thread on meme stocks`, bez rozróżniania wielkości liter.
			- **Język tezy:** polska, gdy `statement` ma polski znak diakrytyczny albo co najmniej dwa polskie słowa funkcyjne; inaczej angielska.
			- **Widełki:** przedział Wilsona dla z = 1,96.

			Koszt: tokeny wejścia nieobecne w cache × cena wejścia + tokeny z cache × cena cache + tokeny wyjścia × cena wyjścia; ceny za 1M tokenów z `models.json`. Próba bez odpowiedzi modelu nie jest liczona.
			""";

}
