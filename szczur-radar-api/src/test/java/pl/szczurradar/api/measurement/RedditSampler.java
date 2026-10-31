package pl.szczurradar.api.measurement;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.SerializationFeature;

import pl.szczurradar.api.agents.assessor.Prefilter;
import pl.szczurradar.api.agents.assessor.contract.AssessmentInputV1;
import pl.szczurradar.api.agents.assessor.contract.ContractJson;
import pl.szczurradar.api.measurement.GoldLabels.Post;
import pl.szczurradar.api.measurement.WykopGoldSampler.Anonymizer;
import pl.szczurradar.api.sources.UnmappableItemException;
import pl.szczurradar.api.sources.reddit.RedditApiClient;
import pl.szczurradar.api.sources.reddit.RedditItem;
import pl.szczurradar.api.sources.reddit.RedditMapper;
import pl.szczurradar.api.sources.reddit.RedditRepository;
import pl.szczurradar.api.sources.reddit.RedditSearchResult;

/**
 * Samples Reddit for the S-28 measurement: every post of the closed days and subreddits asked for, read
 * through {@link RedditRepository} (Arctic Shift, no key), anonymized, mapped with {@link RedditMapper}
 * and kept when {@link Prefilter} passes it. Nothing is drawn: the volume and the megathreads are part of
 * the measurement. Raw answers are never written.
 *
 * <pre>
 * ./mvnw -Pmeasurement test -Dtest=RedditSampler -Dreddit.days=2026-10-20,2026-10-21 -Dreddit.dir=&lt;path&gt;
 * </pre>
 *
 * Optional: {@code -Dreddit.subreddits=stocks,ValueInvesting,investing} (the default). Writes
 * {@code posts.json} ({@link GoldLabels#writePosts}, sorted by day, subreddit and id, so that
 * {@code -Dreddit.limit} of {@link RedditRecorder} takes the same posts every time) and
 * {@code volume.json} ({@link Volume}).
 *
 * <p>Anonymization, because the sample goes into a public repository: {@code u/nick} and {@code /u/nick}
 * become {@code @nick} mentions, then {@link WykopGoldSampler.Anonymizer} replaces mentions, the authors'
 * nicks, URLs (with a scheme or a bare {@code www.}) and e-mail addresses. It is the one difference from
 * production's path, and the input is otherwise field for field what production sends. The replaced bare
 * nicks are printed for review.
 */
@Tag("measurement")
class RedditSampler {

	static final String POSTS = "posts.json";

	static final String VOLUME = "volume.json";

	private static final String DEFAULT_SUBREDDITS = "stocks,ValueInvesting,investing";

	/** Not people: never replaced as nicks. */
	private static final Set<String> NOT_AUTHORS = Set.of("[deleted]", "AutoModerator");

	/** Between fetches, like {@link RedditRepository}'s own pause between subreddits. */
	private static final long PAUSE_MILLIS = 300;

	/**
	 * {@code u/nick} or {@code /u/nick} not inside a word. As wide as the check on {@code posts.json}
	 * ({@code README.md}): only ASCII letters and digits stop a match. Reddit's editor writes a nick's
	 * {@code _} as {@code \_}.
	 */
	private static final Pattern USER_LINK = Pattern.compile("(?i)(?<![A-Za-z0-9])/?u/((?:[A-Za-z0-9_-]|\\\\[_-]){3,})");

	/**
	 * One row of {@code volume.json}: one day of one subreddit.
	 *
	 * @param fetched every post of the day: those returned, {@code removed} and {@code skipped}
	 * @param removed {@link RedditSearchResult#removed()}
	 * @param skipped {@link RedditSearchResult#skippedElements()}
	 * @param assessable those {@link Prefilter} passes, the posts of {@code posts.json}
	 */
	@JsonPropertyOrder({ "day", "subreddit", "fetched", "removed", "skipped", "assessable" })
	record Volume(LocalDate day, String subreddit, int fetched, int removed, int skipped, int assessable) {
	}

	/** A subreddit's day as the repository returned it. */
	private record Fetched(LocalDate day, String subreddit, RedditSearchResult result) {
	}

	@Test
	void samplesTheRedditPosts() throws IOException, InterruptedException {
		List<LocalDate> days = Arrays.stream(required("reddit.days").split(",")).map(String::strip).map(LocalDate::parse)
			.sorted().distinct().toList();
		assertThat(days).as("reddit.days must be closed days").isNotEmpty().allSatisfy(
				day -> assertThat(day).isBefore(LocalDate.now(RedditMeasures.ZONE)));
		Path dir = Path.of(required("reddit.dir"));
		List<String> subreddits = Arrays.stream(System.getProperty("reddit.subreddits", DEFAULT_SUBREDDITS).split(","))
			.map(String::strip).toList();

		RedditApiClient client = new RedditApiClient();
		List<Fetched> fetched = new ArrayList<>();
		for (LocalDate day : days) {
			Instant from = day.atStartOfDay(RedditMeasures.ZONE).toInstant();
			Instant to = day.plusDays(1).atStartOfDay(RedditMeasures.ZONE).toInstant().minusSeconds(1);
			for (String subreddit : subreddits) {
				// One repository per subreddit, so the result's counts are this subreddit's.
				RedditSearchResult result = new RedditRepository(client, List.of(subreddit)).postsCreatedBetween(from, to);
				fetched.add(new Fetched(day, subreddit, result));
				System.out.printf("%s r/%s: %d posts, %d removed, %d skipped (unexpected shape)%n", day, subreddit,
						result.elements().size(), result.removed(), result.skippedElements());
				Thread.sleep(PAUSE_MILLIS);
			}
		}

		Anonymizer anonymizer = new Anonymizer(authors(fetched.stream().flatMap(day -> items(day).stream()).toList()));
		List<Post> posts = new ArrayList<>();
		List<Volume> volume = new ArrayList<>();
		for (Fetched day : fetched) {
			int assessable = 0;
			int unmappable = 0;
			for (RedditItem item : items(day)) {
				try {
					AssessmentInputV1 input = RedditMapper.map(anonymized(item, anonymizer));
					if (Prefilter.isAssessable(input)) {
						assessable++;
						posts.add(new Post("reddit-" + item.id(), day.day(), GoldLabels.Media.NONE, input));
					}
				}
				catch (UnmappableItemException ex) {
					unmappable++;
				}
			}
			RedditSearchResult result = day.result();
			volume.add(new Volume(day.day(), day.subreddit(), result.elements().size() + result.removed()
					+ result.skippedElements(), result.removed(), result.skippedElements(), assessable));
			System.out.printf("%s r/%s: %d assessable, %d unmappable%n", day.day(), day.subreddit(), assessable, unmappable);
		}
		posts.sort(Comparator.comparing(Post::day)
			.thenComparing(post -> post.input().item().channels().getFirst())
			.thenComparing(Post::postId));
		System.out.printf("nicks replaced: %d mentions, %d bare author nicks; URLs: %d; e-mails: %d%n",
				anonymizer.mentions, anonymizer.bareNicks, anonymizer.urls, anonymizer.emails);
		System.out.println("bare author nicks replaced (check they are not ordinary words): "
				+ anonymizer.replacedBareNicks);

		GoldLabels.writePosts(dir.resolve(POSTS), posts);
		Files.writeString(dir.resolve(VOLUME), ContractJson.MAPPER.writer()
			.with(SerializationFeature.INDENT_OUTPUT)
			.writeValueAsString(volume) + "\n");
		System.out.println("wrote " + posts.size() + " posts and " + volume.size() + " volume rows to "
				+ dir.toAbsolutePath());
		assertThat(posts).as("assessable posts").isNotEmpty();
	}

	/**
	 * {@code item} with its text anonymized and nothing that leads to its author: what goes through
	 * {@link RedditMapper#map}, in the order of {@link WykopGoldSampler}.
	 */
	static RedditItem anonymized(RedditItem item, Anonymizer anonymizer) {
		return new RedditItem(item.id(), item.subreddit(), anonymizer.text(userLinksAsMentions(item.title())),
				anonymizer.text(userLinksAsMentions(item.selftext())), item.isSelf(), null, null, null, item.createdUtc(),
				item.removedByCategory());
	}

	/**
	 * {@code u/nick} and {@code /u/nick} as {@code @nick}, the form {@link Anonymizer} replaces. A mention
	 * right after a letter, digit, dot, {@code _} or {@code -} is not one for {@link Anonymizer}, so it gets
	 * a space in front.
	 */
	static @Nullable String userLinksAsMentions(@Nullable String text) {
		if (text == null) {
			return null;
		}
		return USER_LINK.matcher(text).replaceAll(match -> {
			boolean glued = match.start() > 0 && gluesMention(text.charAt(match.start() - 1));
			return Matcher.quoteReplacement((glued ? " @" : "@") + match.group(1).replace("\\", ""));
		});
	}

	private static boolean gluesMention(char before) {
		return Character.isLetterOrDigit(before) || before == '.' || before == '_' || before == '-';
	}

	/** The nicks to replace wherever they occur in the text: the posts' authors who are people. */
	static List<String> authors(List<RedditItem> items) {
		return items.stream().map(RedditItem::author).filter(Objects::nonNull)
			.filter(author -> !NOT_AUTHORS.contains(author)).toList();
	}

	private static List<RedditItem> items(Fetched day) {
		return day.result().elements().stream().map(RedditSearchResult.Element::item).toList();
	}

	private static String required(String property) {
		String value = System.getProperty(property);
		if (value == null || value.isBlank()) {
			throw new IllegalStateException("-D" + property + " is required, see the class comment");
		}
		return value;
	}

}
