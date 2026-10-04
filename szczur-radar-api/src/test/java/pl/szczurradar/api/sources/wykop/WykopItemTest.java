package pl.szczurradar.api.sources.wykop;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** {@link WykopItem} read from the real, anonymized search answer ({@code search-entries-page.json}). */
class WykopItemTest {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	@Test
	void mediaPresenceIsReadFromTheAnswer() {
		JsonNode data = MAPPER.readTree(LocalWykopServer.fixture("search-entries-page.json")).get("data");
		List<WykopItem> items = data.valueStream().map(node -> MAPPER.treeToValue(node, WykopItem.class)).toList();

		// The recording: the first entry has a photo, the second is text only.
		assertThat(items.get(0).media()).isNotNull();
		assertThat(items.get(0).media().photo()).isNotNull();
		assertThat(items.get(1).media() == null || items.get(1).media().photo() == null).isTrue();
		assertThat(items).anySatisfy(item -> assertThat(item.slug()).isNotBlank());
	}

	@Test
	void theWebUrlFollowsTheResourceIdAndSlug() {
		assertThat(item("entry", 87L, "gielda").webUrl()).isEqualTo("https://wykop.pl/wpis/87/gielda");
		assertThat(item("link", 12L, "miedz-kghm").webUrl()).isEqualTo("https://wykop.pl/link/12/miedz-kghm");
		assertThat(item("entry", 87L, null).webUrl()).isEqualTo("https://wykop.pl/wpis/87");
		assertThat(item("entry", null, "gielda").webUrl()).isNull();
		assertThat(item("comment", 87L, "x").webUrl()).isNull();
	}

	private static WykopItem item(String resource, Long id, String slug) {
		return new WykopItem(resource, id, "2026-10-01 10:00:00", "tekst", null, null, List.of("gielda"), null, slug, null);
	}

}
