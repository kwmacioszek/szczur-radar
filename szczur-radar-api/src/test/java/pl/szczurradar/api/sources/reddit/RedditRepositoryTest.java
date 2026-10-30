package pl.szczurradar.api.sources.reddit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import pl.szczurradar.api.pipeline.PipelineStallSettings;
import pl.szczurradar.api.sources.LocalApiServer.Reply;
import pl.szczurradar.api.sources.LocalApiServer.Request;

/**
 * {@link RedditRepository} on real, anonymized Arctic Shift answers for 2026-10-27, Warsaw time
 * ({@code fixtures/reddit/}): r/inwestowanie's whole day (14 posts, none removed) and r/stocks' first 25
 * posts (19 removed, 6 visible). Ranges of more than one page are the real r/inwestowanie posts with their
 * {@code id} and {@code created_utc} renumbered ({@link #posts}).
 */
@ExtendWith(OutputCaptureExtension.class)
class RedditRepositoryTest {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private static final String INWESTOWANIE = LocalRedditServer.fixture("search-posts-inwestowanie.json");

	private static final String STOCKS = LocalRedditServer.fixture("search-posts-stocks.json");

	/** 2026-10-27 in Warsaw (CET), as a range run passes it: 00:00:00 … 23:59:59. */
	private static final Instant FROM = Instant.parse("2026-10-26T23:00:00Z");

	private static final Instant TO = Instant.parse("2026-10-27T22:59:59Z");

	private final List<Duration> pauses = new ArrayList<>();

	@Test
	void readsEachSubredditInOrderWithTheRangeOneSecondWider(CapturedOutput output) {
		try (LocalRedditServer server = new LocalRedditServer(request -> Reply.ok(
				"stocks".equals(param(request, "subreddit")) ? STOCKS : INWESTOWANIE))) {
			RedditSearchResult result = repository(server, "inwestowanie", "stocks").postsCreatedBetween(FROM, TO);

			// 14 of r/inwestowanie, then the 6 visible of r/stocks.
			assertThat(result.elements()).hasSize(14 + 6);
			assertThat(result.elements().subList(0, 14))
				.allSatisfy(element -> assertThat(element.item().subreddit()).isEqualTo("inwestowanie"));
			assertThat(result.elements().subList(14, 20))
				.allSatisfy(element -> assertThat(element.item().subreddit()).isEqualTo("stocks"));
			assertThat(result.removed()).isEqualTo(19);
			assertThat(result.skippedElements()).isZero();
			// Arctic Shift's after and before are exclusive: one second before 00:00:00 and after 23:59:59.
			assertThat(server.requests()).extracting(Request::path).containsOnly("/api/posts/search");
			assertThat(server.requests()).extracting(Request::query).containsExactly(
					"subreddit=inwestowanie&after=1793055599&before=1793142000&sort=asc&limit=100",
					"subreddit=stocks&after=1793055599&before=1793142000&sort=asc&limit=100");
			// Short pages end each subreddit; one pause between the subreddits.
			assertThat(pauses).containsExactly(Duration.ofMillis(1));
			assertThat(output).contains(
					"Reddit r/inwestowanie: 14 post(s) in range, 0 removed or deleted left out, 0 of unexpected shape",
					"Reddit r/stocks: 6 post(s) in range, 19 removed or deleted left out, 0 of unexpected shape");
		}
	}

	@Test
	void eachPostKeepsItsRawFormWithEveryFieldOfTheAnswer() {
		try (LocalRedditServer server = new LocalRedditServer(request -> Reply.ok(INWESTOWANIE))) {
			List<RedditSearchResult.Element> elements = repository(server, "inwestowanie")
				.postsCreatedBetween(FROM, TO)
				.elements();

			JsonNode answer = MAPPER.readTree(INWESTOWANIE).get("data");
			for (int i = 0; i < elements.size(); i++) {
				assertThat(elements.get(i).raw()).isEqualTo(answer.get(i));
				assertThat(elements.get(i).item().id()).isEqualTo(answer.get(i).get("id").stringValue());
			}
			// Fields mapping never reads are kept in the raw post.
			assertThat(elements.getFirst().raw().has("score")).isTrue();
			assertThat(elements.getFirst().raw().has("link_flair_text")).isTrue();
			RedditItem first = elements.getFirst().item();
			assertThat(first.createdUtc()).isEqualTo(1793086669L);
			assertThat(first.isSelf()).isTrue();
			assertThat(first.webUrl()).startsWith("https://www.reddit.com/r/inwestowanie/comments/1xgm63i/");
		}
	}

	@Test
	void aPostInTheSecondThatEndsAFullPageIsReadAgainAndKeptOnce() {
		// Page 1 is full: posts 1–100, posts 100 and 101 in the same second T. Post 101 does not fit page 1,
		// so page 2 starts from T again (after = T − 1): post 100 comes again and counts once, post 101 is
		// not lost. Page 2 (posts 100–130, 31 of 100) is short and ends the subreddit.
		long t = FROM.getEpochSecond() + 100;
		List<ObjectNode> day = posts(1, 130, number -> number <= 100 ? FROM.getEpochSecond() + number
				: number == 101 ? t : t + number);
		try (LocalRedditServer server = new LocalRedditServer(pages(after -> after == FROM.getEpochSecond() - 1
				? day.subList(0, 100) : postedAfter(day, after)))) {
			RedditSearchResult result = repository(server, "inwestowanie").postsCreatedBetween(FROM, TO);

			assertThat(ids(result)).isEqualTo(ids(1, 130));
			assertThat(server.requests()).extracting(request -> param(request, "after"))
				.containsExactly(String.valueOf(FROM.getEpochSecond() - 1), String.valueOf(t - 1));
			assertThat(pauses).containsExactly(Duration.ofMillis(1));
		}
	}

	@Test
	void aQuietNightAsksForEachPageOnce() {
		// Exactly 100 posts: page 1 is full, page 2 has only its last second's post again — short, the end.
		List<ObjectNode> day = posts(1, 100, number -> FROM.getEpochSecond() + number);
		try (LocalRedditServer server = new LocalRedditServer(pages(after -> postedAfter(day, after)))) {
			RedditSearchResult result = repository(server, "inwestowanie").postsCreatedBetween(FROM, TO);

			assertThat(ids(result)).isEqualTo(ids(1, 100));
			assertThat(server.requests()).hasSize(2);
		}
	}

	@Test
	void aFullPageWithoutANewPostIsIncompletePaging() {
		// A full page of posts all in one second: the next page, from that second again, is the same page,
		// and a post of that second beyond it could never be reached.
		long second = FROM.getEpochSecond() + 60;
		List<ObjectNode> page = posts(1, 100, number -> second);
		try (LocalRedditServer server = new LocalRedditServer(pages(after -> page))) {
			assertThatExceptionOfType(RedditApiException.class)
				.isThrownBy(() -> repository(server, "inwestowanie").postsCreatedBetween(FROM, TO))
				.satisfies(ex -> assertThat(ex.failure()).isEqualTo(RedditFailure.INCOMPLETE_PAGING))
				.withMessageStartingWith("Reddit r/inwestowanie page 2 is full without a post not seen before");
			assertThat(server.requests()).hasSize(2);
		}
	}

	@Test
	void postsGoingBackInTimeAreIncompletePaging() {
		// Post 3 is a second older than post 2 (minimally changed recording): the order paging relies on is gone.
		ObjectNode answer = (ObjectNode) MAPPER.readTree(INWESTOWANIE);
		long second = answer.get("data").get(1).get("created_utc").asLong();
		((ObjectNode) answer.get("data").get(2)).put("created_utc", second - 1);
		try (LocalRedditServer server = new LocalRedditServer(request -> Reply.ok(answer.toString()))) {
			assertThatExceptionOfType(RedditApiException.class)
				.isThrownBy(() -> repository(server, "inwestowanie").postsCreatedBetween(FROM, TO))
				.satisfies(ex -> assertThat(ex.failure()).isEqualTo(RedditFailure.INCOMPLETE_PAGING))
				.withMessage("Reddit r/inwestowanie page 1 goes back in time: created_utc 1793089473 after 1793089474");
		}
	}

	@Test
	void aSearchThatNeverEndsStopsAtThePageLimit() {
		// Every answer a full page of posts not seen before, a second later than the page asked for.
		AtomicInteger served = new AtomicInteger();
		try (LocalRedditServer server = new LocalRedditServer(pages(after -> {
			int first = served.getAndAdd(100) + 1;
			return posts(first, first + 99, number -> after + 1 + number);
		}))) {
			assertThatExceptionOfType(RedditApiException.class)
				.isThrownBy(() -> repository(server, "inwestowanie").postsCreatedBetween(FROM, TO))
				.satisfies(ex -> assertThat(ex.failure()).isEqualTo(RedditFailure.PAGE_LIMIT))
				.withMessage("Reddit r/inwestowanie did not end within 50 pages");
			assertThat(server.requests()).hasSize(RedditRepository.MAX_PAGES);
		}
	}

	@Test
	void removedAndDeletedPostsAreLeftOutAndCounted(CapturedOutput output) {
		// The real r/stocks page (19 removed by a moderator or Reddit), one visible post's selftext changed to
		// "[deleted]" without a removed_by_category (minimally changed recording): its author deleted it.
		ObjectNode answer = (ObjectNode) MAPPER.readTree(STOCKS);
		ObjectNode deleted = visible(answer).getFirst();
		deleted.put("selftext", "[deleted]");
		try (LocalRedditServer server = new LocalRedditServer(request -> Reply.ok(answer.toString()))) {
			RedditSearchResult result = repository(server, "stocks").postsCreatedBetween(FROM, TO);

			assertThat(result.removed()).isEqualTo(19 + 1);
			assertThat(result.skippedElements()).isZero();
			assertThat(result.elements()).hasSize(5)
				.allSatisfy(element -> {
					assertThat(element.item().removed()).isFalse();
					assertThat(element.item().removedByCategory()).isNull();
				})
				.noneSatisfy(element -> assertThat(element.item().id()).isEqualTo(deleted.get("id").stringValue()));
			assertThat(output)
				.contains("Reddit r/stocks: 5 post(s) in range, 20 removed or deleted left out, 0 of unexpected shape");
		}
	}

	@Test
	void anElementWithoutIdTimeOrSubredditIsSkippedAndCounted(CapturedOutput output) {
		// The real r/inwestowanie day, minimally changed: post 1 without an id, post 2 with a numeric id,
		// post 3 with created_utc as text, post 4 without a subreddit, post 5 with is_self as an object.
		ObjectNode answer = (ObjectNode) MAPPER.readTree(INWESTOWANIE);
		ArrayNode data = (ArrayNode) answer.get("data");
		((ObjectNode) data.get(0)).remove("id");
		((ObjectNode) data.get(1)).put("id", 123);
		((ObjectNode) data.get(2)).put("created_utc", "yesterday");
		((ObjectNode) data.get(3)).remove("subreddit");
		((ObjectNode) data.get(4)).putObject("is_self");
		try (LocalRedditServer server = new LocalRedditServer(request -> Reply.ok(answer.toString()))) {
			RedditSearchResult result = repository(server, "inwestowanie").postsCreatedBetween(FROM, TO);

			assertThat(result.skippedElements()).isEqualTo(5);
			assertThat(result.elements()).hasSize(14 - 5);
			assertThat(result.removed()).isZero();
			assertThat(output)
				.contains("Reddit r/inwestowanie: 9 post(s) in range, 0 removed or deleted left out, "
						+ "5 of unexpected shape");
		}
	}

	@Test
	void aClientErrorEndsTheFetchWithoutAResult() {
		// r/inwestowanie is read, r/stocks answers 500 in every attempt: the fetch fails, nothing is returned.
		try (LocalRedditServer server = new LocalRedditServer(request -> "stocks".equals(param(request, "subreddit"))
				? new Reply(500, "{\"data\":null,\"error\":\"Internal server error\"}") : Reply.ok(INWESTOWANIE))) {
			assertThatExceptionOfType(RedditApiException.class)
				.isThrownBy(() -> repository(server, "inwestowanie", "stocks").postsCreatedBetween(FROM, TO))
				.satisfies(ex -> {
					assertThat(ex.failure()).isEqualTo(RedditFailure.HTTP_STATUS);
					assertThat(ex.status()).isEqualTo(500);
				});
			assertThat(server.requests()).hasSize(1 + 3);
		}
	}

	@Test
	void theDeadlineCoversTheWholeFetch() {
		// The clock passes the fetch's 10 minutes while r/inwestowanie is read: r/stocks is never asked for.
		AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-29T00:30:00Z"));
		try (LocalRedditServer server = new LocalRedditServer(request -> {
			now.updateAndGet(instant -> instant.plus(RedditRepository.FETCH_DEADLINE));
			return Reply.ok(INWESTOWANIE);
		})) {
			RedditApiClient client = new RedditApiClient(server.baseUrl(), Duration.ofSeconds(5), wait -> {
			}, now::get);
			RedditRepository repository = new RedditRepository(client, List.of("inwestowanie", "stocks"), pauses::add,
					Duration.ofMillis(1));

			assertThatExceptionOfType(RedditApiException.class)
				.isThrownBy(() -> repository.postsCreatedBetween(FROM, TO))
				.satisfies(ex -> assertThat(ex.failure()).isEqualTo(RedditFailure.DEADLINE_EXCEEDED));
			assertThat(server.requests()).hasSize(1);
		}
	}

	@Test
	void anInterruptBetweenSubredditsStopsTheFetchAndKeepsTheInterruptFlag() {
		try (LocalRedditServer server = new LocalRedditServer(request -> Reply.ok(INWESTOWANIE))) {
			RedditRepository repository = new RedditRepository(client(server), List.of("inwestowanie", "stocks"),
					duration -> {
						throw new InterruptedException("stopping");
					}, Duration.ofMillis(1));

			try {
				assertThatExceptionOfType(RedditApiException.class)
					.isThrownBy(() -> repository.postsCreatedBetween(FROM, TO))
					.satisfies(ex -> assertThat(ex.failure()).isEqualTo(RedditFailure.INTERRUPTED));
				assertThat(Thread.currentThread().isInterrupted()).isTrue();
			}
			finally {
				Thread.interrupted();
			}
			assertThat(server.requests()).hasSize(1);
		}
	}

	@Test
	void aSubclassChoosesTheSubredditsOfAFetch() {
		// The configured list is a copy nobody changes; a test's repository may answer its own list instead.
		try (LocalRedditServer server = new LocalRedditServer(request -> Reply.ok(INWESTOWANIE))) {
			RedditRepository repository = new RedditRepository(client(server), List.of(), pauses::add,
					Duration.ofMillis(1)) {

				@Override
				public List<String> subreddits() {
					return List.of("inwestowanie");
				}

			};

			assertThat(repository.postsCreatedBetween(FROM, TO).elements()).hasSize(14);
			assertThat(server.requests()).extracting(request -> param(request, "subreddit"))
				.containsExactly("inwestowanie");
		}
		List<String> names = new ArrayList<>(List.of("stocks"));
		RedditRepository repository = new RedditRepository(new RedditApiClient(), names);
		names.add("investing");
		assertThat(repository.subreddits()).containsExactly("stocks").isUnmodifiable();
	}

	@Test
	void aFetchEndsBeforeTheWatchdogsShortestDefault() {
		// The deadline bounds the attempts; the last one's time-out and one connection retry inside the
		// JDK come on top. A fetch that ends within this is failed by its source, not stopped as stalled.
		Duration longestFetch = RedditRepository.FETCH_DEADLINE.plus(RedditApiClient.DEFAULT_TIMEOUT.multipliedBy(2));

		assertThat(longestFetch).isLessThan(PipelineStallSettings.MIN_DEFAULT);
	}

	/** The ids of the posts {@link #posts} built, {@code from} to {@code to}. */
	private static List<String> ids(int from, int to) {
		return IntStream.rangeClosed(from, to).mapToObj(RedditRepositoryTest::id).toList();
	}

	private static List<String> ids(RedditSearchResult result) {
		return result.elements().stream().map(element -> element.item().id()).toList();
	}

	private static String id(int number) {
		return "t" + number;
	}

	/** The time of a post by its number. */
	@FunctionalInterface
	private interface Times {

		long createdUtc(long number);

	}

	/**
	 * Posts {@code from} to {@code to}: the real r/inwestowanie posts in turn, changed minimally — {@code id}
	 * renumbered to {@code t<number>}, {@code created_utc} from {@code times}.
	 */
	private static List<ObjectNode> posts(int from, int to, Times times) {
		ArrayNode real = (ArrayNode) MAPPER.readTree(INWESTOWANIE).get("data");
		List<ObjectNode> posts = new ArrayList<>();
		for (int number = from; number <= to; number++) {
			ObjectNode post = (ObjectNode) real.get(number % real.size()).deepCopy();
			post.put("id", id(number));
			post.put("created_utc", times.createdUtc(number));
			posts.add(post);
		}
		return posts;
	}

	/** Answers a search by its {@code after}: at most a page of the posts {@code page} gives for it. */
	private static Function<Request, Reply> pages(Function<Long, List<ObjectNode>> page) {
		return request -> {
			ArrayNode data = MAPPER.createArrayNode();
			page.apply(Long.parseLong(param(request, "after")))
				.stream()
				.limit(RedditRepository.PAGE_SIZE)
				.forEach(data::add);
			ObjectNode answer = MAPPER.createObjectNode();
			answer.set("data", data);
			return Reply.ok(answer.toString());
		};
	}

	/** The posts of {@code day} created after {@code after} (the archive's exclusive {@code after}). */
	private static List<ObjectNode> postedAfter(List<ObjectNode> day, long after) {
		return day.stream().filter(post -> post.get("created_utc").asLong() > after).toList();
	}

	/** The visible posts of an answer (no {@code removed_by_category}), in order. */
	private static List<ObjectNode> visible(ObjectNode answer) {
		List<ObjectNode> posts = new ArrayList<>();
		answer.get("data").forEach(post -> {
			if (post.get("removed_by_category").isNull()) {
				posts.add((ObjectNode) post);
			}
		});
		return posts;
	}

	private static String param(Request request, String name) {
		for (String pair : request.query().split("&")) {
			if (pair.startsWith(name + "=")) {
				return pair.substring(name.length() + 1);
			}
		}
		throw new AssertionError("no " + name + " in " + request.query());
	}

	private RedditRepository repository(LocalRedditServer server, String... subreddits) {
		return new RedditRepository(client(server), List.of(subreddits), pauses::add, Duration.ofMillis(1));
	}

	/** No real waits between the client's attempts: a case answering 5xx stays fast. */
	private static RedditApiClient client(LocalRedditServer server) {
		return new RedditApiClient(server.baseUrl(), Duration.ofSeconds(5), wait -> {
		}, InstantSource.system());
	}

}
