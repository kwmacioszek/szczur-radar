package pl.szczurradar.api.sources.reddit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import pl.szczurradar.api.agents.assessor.contract.AssessmentInputV1;
import pl.szczurradar.api.agents.assessor.contract.Context;
import pl.szczurradar.api.agents.assessor.contract.ContractJson;
import pl.szczurradar.api.agents.assessor.contract.ItemType;
import pl.szczurradar.api.sources.UnmappableItemException;

/**
 * {@link RedditMapper} on real, anonymized Arctic Shift posts of r/inwestowanie and r/stocks of 2026-10-27
 * ({@code fixtures/reddit/}). Cases the recording does not have are its posts changed minimally
 * ({@link #post(String, Consumer)}).
 */
class RedditMapperTest {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private static final JsonNode INWESTOWANIE = MAPPER
		.readTree(LocalRedditServer.fixture("search-posts-inwestowanie.json"))
		.get("data");

	private static final JsonNode STOCKS = MAPPER.readTree(LocalRedditServer.fixture("search-posts-stocks.json"))
		.get("data");

	@Test
	void aSelfPostBecomesAPostWithItsTitleAndText() {
		RedditItem self = post("1xgm63i");

		AssessmentInputV1 input = RedditMapper.map(self);

		assertThat(self.isSelf()).isTrue();
		assertThat(input.item().itemType()).isEqualTo(ItemType.POST);
		assertThat(input.item().title()).isEqualTo(self.title());
		assertThat(input.item().body()).isEqualTo(self.selftext());
		assertThat(input.item().channels()).containsExactly("inwestowanie");
		assertThat(input.item().publishedAt()).isEqualTo(OffsetDateTime.parse("2026-10-27T07:37:49Z"));
		assertThat(input.context()).isEqualTo(Context.NONE);
	}

	@Test
	void aLinkPostBecomesALinkWithItsTextUnchanged() {
		// A real link post (an image) with a selftext holding hard spaces and markdown, which stay.
		RedditItem link = post("1xgrbst");

		AssessmentInputV1 input = RedditMapper.map(link);

		assertThat(link.isSelf()).isFalse();
		assertThat(link.selftext()).contains(" ", "**");
		assertThat(input.item().itemType()).isEqualTo(ItemType.LINK);
		assertThat(input.item().title()).isEqualTo(link.title());
		assertThat(input.item().body()).isEqualTo(link.selftext());
	}

	@Test
	void aLinkPostWithoutTextHasOnlyItsTitle() {
		RedditItem link = post("1xgto1s");

		AssessmentInputV1 input = RedditMapper.map(link);

		assertThat(link.selftext()).isEmpty();
		assertThat(input.item().itemType()).isEqualTo(ItemType.LINK);
		assertThat(input.item().title()).isEqualTo(link.title());
		assertThat(input.item().body()).isNull();
	}

	@Test
	void aPostWithoutIsSelfIsALink() {
		assertThat(RedditMapper.map(post("1xgm63i", raw -> raw.remove("is_self"))).item().itemType())
			.isEqualTo(ItemType.LINK);
	}

	@Test
	void theChannelIsTheSubredditAsThePayloadSpellsIt() {
		RedditItem item = post("1xgm63i", raw -> raw.put("subreddit", "ValueInvesting"));

		assertThat(RedditMapper.map(item).item().channels()).containsExactly("ValueInvesting");
	}

	@Test
	void neitherAuthorNorUrlNorIdReachTheInput() {
		RedditItem self = post("1xgm63i");

		String json = ContractJson.MAPPER.writeValueAsString(RedditMapper.map(self));

		assertThat(self.author()).isNotBlank();
		assertThat(json).doesNotContain(self.author(), self.id(), "reddit.com", "author", "\"url\"", "\"id\"",
				"permalink");
	}

	@Test
	void winterAndSummerTimeAreBothUtc() {
		// Reddit's clock has no daylight saving time: January and July both get the offset Z.
		long january = Instant.parse("2026-01-15T12:00:00Z").getEpochSecond();
		long july = Instant.parse("2026-07-15T12:00:00Z").getEpochSecond();

		OffsetDateTime winter = RedditMapper.map(post("1xgm63i", raw -> raw.put("created_utc", january)))
			.item()
			.publishedAt();
		OffsetDateTime summer = RedditMapper.map(post("1xgm63i", raw -> raw.put("created_utc", july)))
			.item()
			.publishedAt();

		assertThat(winter).isEqualTo(OffsetDateTime.parse("2026-01-15T12:00:00Z"));
		assertThat(winter.getOffset()).isEqualTo(ZoneOffset.UTC);
		assertThat(summer).isEqualTo(OffsetDateTime.parse("2026-07-15T12:00:00Z"));
		assertThat(summer.getOffset()).isEqualTo(ZoneOffset.UTC);
		assertThat(ContractJson.MAPPER.writeValueAsString(
				RedditMapper.map(post("1xgm63i", raw -> raw.put("created_utc", july))))).contains(
						"\"published_at\":\"2026-07-15T12:00:00Z\"");
	}

	@ParameterizedTest
	@ValueSource(strings = { "", " ", " \n", " ", "\u0000 " })
	void emptyOrWhiteSpaceOnlyTextBecomesNull(String blank) {
		AssessmentInputV1 input = RedditMapper.map(post("1xgm63i", raw -> raw.put("selftext", blank)));

		assertThat(input.item().title()).isNotNull();
		assertThat(input.item().body()).isNull();
		assertThat(RedditMapper.map(post("1xgm63i", raw -> raw.put("title", blank))).item().title()).isNull();
	}

	@Test
	void nulIsRemovedFromTheText() {
		// The real self-post with a NUL put into its text; the database rejects U+0000.
		RedditItem self = post("1xgm63i");

		AssessmentInputV1 input = RedditMapper
			.map(post("1xgm63i", raw -> raw.put("selftext", self.selftext() + "\u0000")));

		assertThat(input.item().body()).isEqualTo(self.selftext());
	}

	@Test
	void aRemovedPostIsRejected() {
		// The real r/stocks post a moderator removed: its title is intact, its text is "[removed]".
		RedditItem removed = stocks("1xgdm0y");

		assertThat(removed.selftext()).isEqualTo("[removed]");
		assertThatExceptionOfType(UnmappableItemException.class).isThrownBy(() -> RedditMapper.map(removed))
			.withMessageContaining("removed or deleted");
	}

	@ParameterizedTest
	@ValueSource(strings = { "[removed]", "[deleted]" })
	void aPostWhoseTextIsGoneIsRejected(String selftext) {
		assertThatExceptionOfType(UnmappableItemException.class)
			.isThrownBy(() -> RedditMapper.map(post("1xgm63i", raw -> raw.put("selftext", selftext))))
			.withMessageContaining("removed or deleted");
	}

	@Test
	void aPostWithoutTextIsRejected() {
		assertThatExceptionOfType(UnmappableItemException.class)
			.isThrownBy(() -> RedditMapper.map(post("1xgto1s", raw -> raw.put("title", " \n"))))
			.withMessageContaining("no text");
		assertThatExceptionOfType(UnmappableItemException.class)
			.isThrownBy(() -> RedditMapper.map(post("1xgm63i", raw -> raw.putNull("title").remove("selftext"))));
	}

	@Test
	void aMissingOrNegativeTimeIsRejected() {
		assertThatExceptionOfType(UnmappableItemException.class)
			.isThrownBy(() -> RedditMapper.map(post("1xgm63i", raw -> raw.remove("created_utc"))))
			.withMessageContaining("created_utc");
		assertThatExceptionOfType(UnmappableItemException.class)
			.isThrownBy(() -> RedditMapper.map(post("1xgm63i", raw -> raw.put("created_utc", -1))));
	}

	@Test
	void aPostWithoutASubredditIsRejected() {
		assertThatExceptionOfType(UnmappableItemException.class)
			.isThrownBy(() -> RedditMapper.map(post("1xgm63i", raw -> raw.remove("subreddit"))))
			.withMessageContaining("no subreddit");
		assertThatExceptionOfType(UnmappableItemException.class)
			.isThrownBy(() -> RedditMapper.map(post("1xgm63i", raw -> raw.put("subreddit", " "))));
	}

	/** The real r/inwestowanie post {@code id}. */
	private static RedditItem post(String id) {
		return post(id, raw -> {
		});
	}

	/** The real r/inwestowanie post {@code id}, changed by {@code change} (a case the recording lacks). */
	private static RedditItem post(String id, Consumer<ObjectNode> change) {
		return item(INWESTOWANIE, id, change);
	}

	/** The real r/stocks post {@code id}. */
	private static RedditItem stocks(String id) {
		return item(STOCKS, id, raw -> {
		});
	}

	private static RedditItem item(JsonNode posts, String id, Consumer<ObjectNode> change) {
		for (JsonNode post : posts) {
			if (id.equals(post.get("id").stringValue())) {
				ObjectNode copy = (ObjectNode) post.deepCopy();
				change.accept(copy);
				return MAPPER.treeToValue(copy, RedditItem.class);
			}
		}
		throw new AssertionError("no post " + id + " in the fixture");
	}

}
