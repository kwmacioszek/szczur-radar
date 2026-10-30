package pl.szczurradar.api.sources.wykop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import pl.szczurradar.api.agents.assessor.contract.ItemType;
import pl.szczurradar.api.sources.Source;
import pl.szczurradar.api.sources.SourceFetcher;
import pl.szczurradar.api.sources.SourcePost;

/** {@link WykopFetcher} on the real #gielda search answers ({@code fixtures/wykop/}). */
class WykopFetcherTest {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	// 2026-10-01, the whole day in Warsaw time (UTC+2).
	private static final Instant FROM = Instant.parse("2026-09-30T22:00:00Z");

	private static final Instant TO = Instant.parse("2026-10-01T21:59:59Z");

	private final WykopRepository repository = mock(WykopRepository.class);

	@Test
	void entriesAndLinksBecomePostsOfTheSharedModelWithTheirRawElement() {
		List<WykopSearchResult.Element> elements = elements("search-entries-page.json");
		elements.addAll(elements("search-links-page.json"));
		when(repository.itemsAddedBetween(WykopPosts.TAG, LocalDateTime.parse("2026-10-01T00:00:00"),
				LocalDateTime.parse("2026-10-01T23:59:59"))).thenReturn(new WykopSearchResult(elements, 1));

		SourceFetcher.Fetched batch = new WykopFetcher(Optional.of(repository)).fetch(FROM, TO);

		assertThat(batch.fetched()).isEqualTo(12 + 4 + 1);
		assertThat(batch.skipped()).isEqualTo(1);
		assertThat(batch.posts()).hasSize(16);
		assertThat(batch.posts()).extracting(SourcePost::itemType).containsOnly(ItemType.POST, ItemType.LINK);
		// The channel is the input's channels[0]: the tag the search read.
		assertThat(batch.posts()).extracting(SourcePost::channel).containsOnly(WykopPosts.TAG);
		SourcePost first = batch.posts().getFirst();
		assertThat(first.source()).isEqualTo(Source.WYKOP);
		assertThat(first.sourceItemId()).isEqualTo(String.valueOf(elements.getFirst().item().id()));
		assertThat(MAPPER.readTree(first.raw())).isEqualTo(elements.getFirst().raw());
	}

	@Test
	void anElementThatCannotBecomeAPostIsSkipped() {
		List<WykopSearchResult.Element> elements = elements("search-entries-page.json");
		// The first real entry without its id and the second without its text (minimally changed).
		elements.set(0, element(((ObjectNode) elements.get(0).raw().deepCopy()).putNull("id")));
		elements.set(1, element(((ObjectNode) elements.get(1).raw().deepCopy()).put("content", " ")));
		when(repository.itemsAddedBetween(WykopPosts.TAG, LocalDateTime.parse("2026-10-01T00:00:00"),
				LocalDateTime.parse("2026-10-01T23:59:59"))).thenReturn(new WykopSearchResult(elements, 0));

		SourceFetcher.Fetched batch = new WykopFetcher(Optional.of(repository)).fetch(FROM, TO);

		assertThat(batch.fetched()).isEqualTo(12);
		assertThat(batch.skipped()).isEqualTo(2);
		assertThat(batch.posts()).hasSize(10);
	}

	@Test
	void withoutKeysItIsNotConfiguredAndCannotFetch() {
		WykopFetcher fetcher = new WykopFetcher(Optional.empty());

		assertThat(fetcher.source()).isEqualTo(Source.WYKOP);
		assertThat(fetcher.configured()).isFalse();
		assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> fetcher.fetch(FROM, TO));
	}

	private static List<WykopSearchResult.Element> elements(String fixture) {
		List<WykopSearchResult.Element> elements = new ArrayList<>();
		MAPPER.readTree(read(fixture)).get("data").forEach(raw -> elements.add(element(raw)));
		return elements;
	}

	private static WykopSearchResult.Element element(JsonNode raw) {
		return new WykopSearchResult.Element(MAPPER.treeToValue(raw, WykopItem.class), raw);
	}

	private static String read(String name) {
		try (InputStream in = WykopFetcherTest.class.getResourceAsStream("/fixtures/wykop/" + name)) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
