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
 * @param slug the last part of the element's address on wykop.pl
 * @param media attachments; their content never reaches the model (contract V2 assesses text only)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WykopItem(@Nullable String resource, @Nullable Long id, @JsonProperty("created_at") @Nullable String createdAt,
		@Nullable String content, @Nullable String title, @Nullable String description, @Nullable List<String> tags,
		@Nullable Author author, @Nullable String slug, @Nullable Media media) {

	/** The address of the element on wykop.pl ({@code /wpis/<id>/<slug>}, {@code /link/<id>/<slug>}), if known. */
	public @Nullable String webUrl() {
		if (id == null) {
			return null;
		}
		String kind = WykopResource.of(resource).map(type -> type == WykopResource.LINK ? "link" : "wpis").orElse(null);
		return kind == null ? null : "https://wykop.pl/" + kind + "/" + id + (slug == null ? "" : "/" + slug);
	}

	/**
	 * Which attachments an element has; only their presence is read.
	 *
	 * @param photo a picture
	 * @param embed an embedded video or page
	 * @param survey a poll
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Media(@Nullable Object photo, @Nullable Object embed, @Nullable Object survey) {
	}

	/** The author of an element; never passed to the model (contract-v2.md §2). */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Author(@Nullable String username) {
	}

}
