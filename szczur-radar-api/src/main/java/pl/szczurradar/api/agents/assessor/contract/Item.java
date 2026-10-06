package pl.szczurradar.api.agents.assessor.contract;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * {@code item} of the input (contract-v2.md §2): the statement being assessed. The text goes to the
 * model unchanged (hard spaces, line breaks, emoji, censored words stay).
 */
@JsonPropertyOrder({ "item_type", "published_at", "channels", "title", "body" })
public record Item(@JsonProperty("item_type") ItemType itemType,
		@JsonProperty("published_at") OffsetDateTime publishedAt, List<String> channels, String title,
		String body) implements TextFields {

	public Item {
		Objects.requireNonNull(itemType, "item.item_type");
		Objects.requireNonNull(publishedAt, "item.published_at");
		channels = List.copyOf(Objects.requireNonNull(channels, "item.channels"));
		TextFields.requireText(title, body, "item");
	}

}
