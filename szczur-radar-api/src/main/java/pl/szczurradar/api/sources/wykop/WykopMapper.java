package pl.szczurradar.api.sources.wykop;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.jspecify.annotations.Nullable;

import pl.szczurradar.api.agents.assessor.contract.AssessmentInputV1;
import pl.szczurradar.api.agents.assessor.contract.Context;
import pl.szczurradar.api.agents.assessor.contract.Item;
import pl.szczurradar.api.agents.assessor.contract.ItemType;
import pl.szczurradar.api.agents.assessor.text.Whitespace;

/**
 * A Wykop API v3 element → {@link AssessmentInputV1} (contract-v2.md §2), one definition for the gold
 * sample and S-02: an entry becomes a {@code post} ({@code title = null}, {@code body = content}), a
 * link a {@code link} ({@code title}, {@code body = description}); {@code created_at} is Warsaw local
 * time; {@code channels} is the tag the element was read from. Author, URL and id never reach the
 * input. Text goes in unchanged; empty or white-space-only text becomes {@code null}.
 */
public final class WykopMapper {

	/** The zone of Wykop's {@code created_at}. */
	public static final ZoneId WYKOP_ZONE = ZoneId.of("Europe/Warsaw");

	private static final DateTimeFormatter CREATED_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private WykopMapper() {
	}

	/**
	 * @param channel the tag whose stream the element came from, e.g. {@code gielda}
	 * @throws UnmappableItemException for a resource other than {@code entry}/{@code link}, an element
	 *     without text, or a missing or malformed {@code created_at}
	 */
	public static AssessmentInputV1 map(WykopItem item, String channel) {
		WykopResource resource = WykopResource.of(item.resource())
			.orElseThrow(() -> new UnmappableItemException(
					"unsupported Wykop resource '" + item.resource() + "' (id " + item.id() + ")"));
		ItemType type;
		String title;
		String body;
		switch (resource) {
			case ENTRY -> {
				type = ItemType.POST;
				title = null;
				body = textOrNull(item.content());
			}
			case LINK -> {
				type = ItemType.LINK;
				title = textOrNull(item.title());
				body = textOrNull(item.description());
			}
			default -> throw new IllegalStateException(resource.name());
		}
		if (title == null && body == null) {
			throw new UnmappableItemException("Wykop " + resource.value() + " " + item.id() + " has no text");
		}
		return AssessmentInputV1.of(new Item(type, publishedAt(item), List.of(channel), title, body), Context.NONE);
	}

	/** {@code created_at} as Warsaw time with its offset ({@code +01:00} in winter, {@code +02:00} in summer). */
	public static OffsetDateTime publishedAt(WykopItem item) {
		if (item.createdAt() == null) {
			throw new UnmappableItemException("Wykop element " + item.id() + " has no created_at");
		}
		try {
			return LocalDateTime.parse(item.createdAt(), CREATED_AT).atZone(WYKOP_ZONE).toOffsetDateTime();
		}
		catch (DateTimeParseException ex) {
			throw new UnmappableItemException(
					"Wykop element " + item.id() + " has a malformed created_at '" + item.createdAt() + "'");
		}
	}

	private static @Nullable String textOrNull(@Nullable String text) {
		return text == null || Whitespace.normalize(text).isEmpty() ? null : text;
	}

}
