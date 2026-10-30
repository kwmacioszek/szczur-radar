package pl.szczurradar.api.sources.reddit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import pl.szczurradar.api.agents.assessor.contract.AssessmentInputV1;
import pl.szczurradar.api.agents.assessor.contract.ItemType;
import pl.szczurradar.api.sources.LocalApiServer.Reply;
import pl.szczurradar.api.sources.Source;
import pl.szczurradar.api.sources.SourcePost;
import pl.szczurradar.api.sources.UnmappableItemException;

/**
 * {@link RedditPosts} on the elements {@link RedditRepository} reads from the real, anonymized Arctic Shift
 * answers of 2026-10-27 ({@code fixtures/reddit/}): r/inwestowanie's 14 posts and r/stocks' 6 visible ones
 * (its 19 removed ones are left out by the repository).
 */
class RedditPostsTest {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	/** 2026-10-27 in Warsaw (CET), as a range run passes it. */
	private static final Instant FROM = Instant.parse("2026-10-26T23:00:00Z");

	private static final Instant TO = Instant.parse("2026-10-27T22:59:59Z");

	/*
	 * Read per test, not in a static initializer: the local server answers on its own thread through this
	 * class's lambda, which would wait for the class's initialization to end.
	 */
	private final List<RedditSearchResult.Element> elements = elements();

	@Test
	void theStoredRawPayloadMapsToTheInputOfTheElement() {
		// What the assess step rebuilds from the raw payload is what the fetch step mapped: a retry gets the
		// input of the first attempt.
		RedditPosts mapper = new RedditPosts();

		assertThat(mapper.source()).isEqualTo(Source.REDDIT);
		assertThat(elements).hasSize(14 + 6);
		for (RedditSearchResult.Element element : elements) {
			SourcePost post = RedditPosts.post(element);

			assertThat(mapper.map(post.raw())).isEqualTo(RedditMapper.map(element.item()));
			assertThat(MAPPER.readTree(post.raw())).isEqualTo(element.raw());
		}
	}

	@Test
	void aPostCarriesItsSubredditAsChannelAndTagAndItsRedditAddress() {
		for (RedditSearchResult.Element element : elements) {
			RedditItem item = element.item();
			SourcePost post = RedditPosts.post(element);
			AssessmentInputV1 input = RedditMapper.map(item);

			assertThat(post.source()).isEqualTo(Source.REDDIT);
			assertThat(post.sourceItemId()).isEqualTo(item.id());
			assertThat(post.channel()).isEqualTo(input.item().channels().getFirst()).isEqualTo(item.subreddit());
			assertThat(post.tags()).containsExactly(item.subreddit());
			assertThat(post.webUrl()).startsWith("https://www.reddit.com/r/" + item.subreddit() + "/comments/" + item.id());
			assertThat(post.itemType()).isEqualTo(Boolean.TRUE.equals(item.isSelf()) ? ItemType.POST : ItemType.LINK);
			assertThat(post.publishedAt()).isEqualTo(Instant.ofEpochSecond(item.createdUtc()));
			assertThat(post.title()).isEqualTo(input.item().title());
			assertThat(post.body()).isEqualTo(input.item().body());
			assertThat(post.author()).isEqualTo(item.author()).startsWith("uzytkownik");
			assertThat(post.slug()).isNull();
		}
		assertThat(elements).extracting(element -> RedditPosts.post(element).channel())
			.containsOnly("inwestowanie", "stocks");
	}

	@Test
	void aDeletedOrMissingAuthorIsNoAuthor() {
		// The first real post, its author's account deleted, then without an author (minimally changed).
		assertThat(RedditPosts.post(element(raw -> raw.put("author", "[deleted]"))).author()).isNull();
		assertThat(RedditPosts.post(element(raw -> raw.remove("author"))).author()).isNull();
	}

	@Test
	void anElementThatCannotBecomeAPostIsRejected() {
		// The first real post without its id, then with its text removed (minimally changed).
		assertThatExceptionOfType(UnmappableItemException.class)
			.isThrownBy(() -> RedditPosts.post(element(raw -> raw.remove("id"))))
			.withMessageContaining("without an id");
		assertThatExceptionOfType(UnmappableItemException.class)
			.isThrownBy(() -> RedditPosts.post(element(raw -> raw.put("selftext", "[removed]"))));
	}

	/** The first real r/inwestowanie element, changed by {@code change}. */
	private RedditSearchResult.Element element(Consumer<ObjectNode> change) {
		ObjectNode raw = (ObjectNode) elements.getFirst().raw().deepCopy();
		change.accept(raw);
		return new RedditSearchResult.Element(MAPPER.treeToValue(raw, RedditItem.class), raw);
	}

	/** Both fixtures as the repository reads them: r/inwestowanie, then r/stocks without its removed posts. */
	private static List<RedditSearchResult.Element> elements() {
		String inwestowanie = LocalRedditServer.fixture("search-posts-inwestowanie.json");
		String stocks = LocalRedditServer.fixture("search-posts-stocks.json");
		try (LocalRedditServer server = new LocalRedditServer(
				request -> Reply.ok(request.query().contains("subreddit=stocks&") ? stocks : inwestowanie))) {
			RedditApiClient client = new RedditApiClient(server.baseUrl(), Duration.ofSeconds(5), wait -> {
			}, InstantSource.system());
			return new RedditRepository(client, List.of("inwestowanie", "stocks"), pause -> {
			}, Duration.ZERO).postsCreatedBetween(FROM, TO).elements();
		}
	}

}
