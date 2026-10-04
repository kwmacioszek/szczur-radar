package pl.szczurradar.api.sources.wykop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import pl.szczurradar.api.agents.assessor.contract.AssessmentInputV1;
import pl.szczurradar.api.agents.assessor.contract.Context;
import pl.szczurradar.api.agents.assessor.contract.ContractJson;
import pl.szczurradar.api.agents.assessor.contract.ItemType;

/**
 * {@link WykopMapper} on Wykop API v3 elements: real #gielda elements of 2026-10-01 from the search
 * API, anonymized ({@code fixtures/wykop/entry.json}, {@code link.json}).
 */
class WykopMapperTest {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	@Test
	void anEntryBecomesAPostWithItsContentUnchanged() {
		WykopItem entry = fixture("entry.json");

		AssessmentInputV1 input = WykopMapper.map(entry, "gielda");

		assertThat(input.item().itemType()).isEqualTo(ItemType.POST);
		assertThat(input.item().title()).isNull();
		assertThat(input.item().body()).isEqualTo(entry.content());
		assertThat(input.item().channels()).containsExactly("gielda");
		assertThat(input.item().publishedAt()).isEqualTo(OffsetDateTime.parse("2026-10-01T07:16:25+02:00"));
		assertThat(input.context()).isEqualTo(Context.NONE);
	}

	@Test
	void aLinkBecomesALinkWithTitleAndDescription() {
		WykopItem link = fixture("link.json");

		AssessmentInputV1 input = WykopMapper.map(link, "gielda");

		assertThat(input.item().itemType()).isEqualTo(ItemType.LINK);
		assertThat(input.item().title()).isEqualTo(link.title());
		assertThat(input.item().body()).isEqualTo(link.description());
		assertThat(input.item().channels()).containsExactly("gielda");
	}

	@Test
	void neitherAuthorNorIdNorTagsReachTheInput() {
		WykopItem entry = fixture("entry.json");

		String json = ContractJson.MAPPER.writeValueAsString(WykopMapper.map(entry, "gielda"));

		assertThat(entry.author().username()).isNotBlank();
		assertThat(json).doesNotContain(entry.author().username(), "author", "\"tags\"", "\"id\"");
	}

	@Test
	void winterAndSummerTimeGetTheirOffsets() {
		assertThat(WykopMapper.publishedAt(item("entry", "2026-01-15 12:00:00", "tekst", null, null)))
			.isEqualTo(OffsetDateTime.parse("2026-01-15T12:00:00+01:00"));
		assertThat(WykopMapper.publishedAt(item("entry", "2026-07-15 12:00:00", "tekst", null, null)))
			.isEqualTo(OffsetDateTime.parse("2026-07-15T12:00:00+02:00"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "", " ", " \n", " " })
	void emptyOrWhiteSpaceOnlyTextBecomesNull(String blank) {
		AssessmentInputV1 input = WykopMapper.map(item("link", "2026-09-29 20:00:00", null, blank, "Opis"), "gielda");

		assertThat(input.item().title()).isNull();
		assertThat(input.item().body()).isEqualTo("Opis");
	}

	@Test
	void anElementWithoutTextIsRejected() {
		assertThatExceptionOfType(UnmappableItemException.class)
			.isThrownBy(() -> WykopMapper.map(item("entry", "2026-09-29 20:00:00", " \n", null, null), "gielda"))
			.withMessageContaining("no text");
		assertThatExceptionOfType(UnmappableItemException.class)
			.isThrownBy(() -> WykopMapper.map(item("link", "2026-09-29 20:00:00", null, null, null), "gielda"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "comment", "entry_comment", "" })
	void otherResourcesAreRejected(String resource) {
		assertThatExceptionOfType(UnmappableItemException.class)
			.isThrownBy(() -> WykopMapper.map(item(resource, "2026-09-29 20:00:00", "tekst", null, null), "gielda"))
			.withMessageContaining("unsupported Wykop resource");
	}

	@Test
	void aMissingOrMalformedTimeIsRejected() {
		assertThatExceptionOfType(UnmappableItemException.class)
			.isThrownBy(() -> WykopMapper.map(item("entry", null, "tekst", null, null), "gielda"));
		assertThatExceptionOfType(UnmappableItemException.class)
			.isThrownBy(() -> WykopMapper.map(item("entry", "2026-09-29T20:00:00Z", "tekst", null, null), "gielda"));
	}

	private static WykopItem item(String resource, String createdAt, String content, String title, String description) {
		return new WykopItem(resource, 1L, createdAt, content, title, description, List.of("gielda"), null, null, null);
	}

	private static WykopItem fixture(String name) {
		try (InputStream in = WykopMapperTest.class.getResourceAsStream("/fixtures/wykop/" + name)) {
			return MAPPER.readValue(in, WykopItem.class);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
