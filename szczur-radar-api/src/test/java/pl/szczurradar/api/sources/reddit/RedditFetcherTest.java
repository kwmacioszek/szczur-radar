package pl.szczurradar.api.sources.reddit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import pl.szczurradar.api.agents.assessor.contract.ItemType;
import pl.szczurradar.api.sources.Source;
import pl.szczurradar.api.sources.SourceFetcher;
import pl.szczurradar.api.sources.SourcePost;

/**
 * {@link RedditFetcher} on the real Arctic Shift answers of 2026-10-27 ({@code fixtures/reddit/}), with the
 * repository mocked: its elements are the fixtures' posts without the removed ones, as it returns them.
 */
class RedditFetcherTest {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	// 2026-10-27, the whole day in Warsaw time (UTC+1): passed to the repository as it is.
	private static final Instant FROM = Instant.parse("2026-10-26T23:00:00Z");

	private static final Instant TO = Instant.parse("2026-10-27T22:59:59Z");

	private final RedditRepository repository = mock(RedditRepository.class);

	@Test
	void postsOfEverySubredditBecomePostsOfTheSharedModelWithTheirRawElement() {
		List<RedditSearchResult.Element> elements = elements("search-posts-inwestowanie.json");
		elements.addAll(elements("search-posts-stocks.json"));
		when(repository.subreddits()).thenReturn(List.of("inwestowanie", "stocks"));
		// One element of an unexpected shape and the 19 removed r/stocks posts the repository left out.
		when(repository.postsCreatedBetween(FROM, TO)).thenReturn(new RedditSearchResult(elements, 1, 19));

		SourceFetcher.Fetched batch = new RedditFetcher(Optional.of(repository)).fetch(FROM, TO);

		// Removed posts are no elements of the fetch: neither fetched nor skipped.
		assertThat(batch.fetched()).isEqualTo(14 + 6 + 1);
		assertThat(batch.skipped()).isEqualTo(1);
		assertThat(batch.posts()).hasSize(20);
		assertThat(batch.posts()).extracting(SourcePost::itemType).containsOnly(ItemType.POST, ItemType.LINK);
		assertThat(batch.posts()).extracting(SourcePost::channel).containsOnly("inwestowanie", "stocks");
		SourcePost first = batch.posts().getFirst();
		assertThat(first.source()).isEqualTo(Source.REDDIT);
		assertThat(first.sourceItemId()).isEqualTo(elements.getFirst().item().id());
		assertThat(MAPPER.readTree(first.raw())).isEqualTo(elements.getFirst().raw());
	}

	@Test
	void anElementThatCannotBecomeAPostIsSkipped() {
		List<RedditSearchResult.Element> elements = elements("search-posts-inwestowanie.json");
		// The first real post without its id and the second without text (minimally changed).
		elements.set(0, element(((ObjectNode) elements.get(0).raw().deepCopy()).putNull("id")));
		elements.set(1, element(((ObjectNode) elements.get(1).raw().deepCopy()).put("title", " ").put("selftext", "")));
		when(repository.subreddits()).thenReturn(List.of("inwestowanie"));
		when(repository.postsCreatedBetween(FROM, TO)).thenReturn(new RedditSearchResult(elements, 0, 0));

		SourceFetcher.Fetched batch = new RedditFetcher(Optional.of(repository)).fetch(FROM, TO);

		assertThat(batch.fetched()).isEqualTo(14);
		assertThat(batch.skipped()).isEqualTo(2);
		assertThat(batch.posts()).hasSize(12);
	}

	@Test
	void aFailureOfTheSourceIsThrown() {
		when(repository.subreddits()).thenReturn(List.of("inwestowanie"));
		when(repository.postsCreatedBetween(FROM, TO))
			.thenThrow(new RedditApiException(RedditFailure.TRANSPORT, "Reddit GET api/posts/search failed"));

		assertThatExceptionOfType(RedditApiException.class)
			.isThrownBy(() -> new RedditFetcher(Optional.of(repository)).fetch(FROM, TO));
	}

	@ParameterizedTest
	@MethodSource("unrepresentableTimes")
	void aPostWithAnUnrepresentableTimeIsSkippedWithoutLosingTheOtherPosts(long seconds) {
		List<RedditSearchResult.Element> elements = elements("search-posts-inwestowanie.json");
		RedditSearchResult.Element last = elements.removeLast();
		List<String> expectedIds = elements.stream().map(element -> element.item().id()).toList();
		// Only the last real post's timestamp is changed: outside OffsetDateTime or Instant's range.
		elements.add(element(((ObjectNode) last.raw().deepCopy()).put("created_utc", seconds)));
		when(repository.subreddits()).thenReturn(List.of("inwestowanie"));
		when(repository.postsCreatedBetween(FROM, TO)).thenReturn(new RedditSearchResult(elements, 0, 0));

		SourceFetcher.Fetched batch = new RedditFetcher(Optional.of(repository)).fetch(FROM, TO);

		assertThat(batch.fetched()).isEqualTo(14);
		assertThat(batch.skipped()).isEqualTo(1);
		assertThat(batch.posts()).hasSize(13);
		assertThat(batch.posts()).extracting(SourcePost::sourceItemId).containsExactlyElementsOf(expectedIds);
	}

	@Test
	void withoutSubredditsItIsNotConfiguredAndCannotFetch() {
		when(repository.subreddits()).thenReturn(List.of());
		RedditFetcher fetcher = new RedditFetcher(Optional.of(repository));

		assertThat(fetcher.configured()).isFalse();
		assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> fetcher.fetch(FROM, TO))
			.withMessage("Reddit is not configured (REDDIT_SUBREDDITS)");
		verify(repository, never()).postsCreatedBetween(any(), any());
	}

	@Test
	void withoutTheSettingItIsNotConfiguredAndCannotFetch() {
		RedditFetcher fetcher = new RedditFetcher(Optional.empty());

		assertThat(fetcher.source()).isEqualTo(Source.REDDIT);
		assertThat(fetcher.configured()).isFalse();
		assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> fetcher.fetch(FROM, TO));
	}

	@Test
	void withSubredditsItIsConfigured() {
		when(repository.subreddits()).thenReturn(List.of("stocks"));

		assertThat(new RedditFetcher(Optional.of(repository)).configured()).isTrue();
	}

	private static Stream<Long> unrepresentableTimes() {
		return Stream.of(Instant.MAX.getEpochSecond(), Long.MAX_VALUE);
	}

	/** The posts of a fixture as the repository returns them: the removed ones left out. */
	private static List<RedditSearchResult.Element> elements(String fixture) {
		List<RedditSearchResult.Element> elements = new ArrayList<>();
		MAPPER.readTree(LocalRedditServer.fixture(fixture)).get("data").forEach(raw -> {
			RedditSearchResult.Element element = element(raw);
			if (!element.item().removed()) {
				elements.add(element);
			}
		});
		return elements;
	}

	private static RedditSearchResult.Element element(JsonNode raw) {
		return new RedditSearchResult.Element(MAPPER.treeToValue(raw, RedditItem.class), raw);
	}

}
