package pl.szczurradar.api.sources.wykop;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

/**
 * The fields of a Wykop API v3 stream element that the assessment needs. Everything else of the
 * element (votes, media, comments, …) is ignored here; the raw element is kept apart by the pipeline.
 *
 * @param resource {@code entry} (microblog entry) or {@code link} (found link); other values exist
 * @param id the source id
 * @param createdAt local Warsaw time, {@code yyyy-MM-dd HH:mm:ss}
 * @param content the text of an entry
 * @param title the title of a link
 * @param description the description of a link
 * @param tags the element's tags, without {@code #}
 * @param author the author
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WykopItem(@Nullable String resource, @Nullable Long id, @JsonProperty("created_at") @Nullable String createdAt,
		@Nullable String content, @Nullable String title, @Nullable String description, @Nullable List<String> tags,
		@Nullable Author author) {

	/** The author of an element; never passed to the model (contract-v2.md §2). */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Author(@Nullable String username) {
	}

}
