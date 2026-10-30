package pl.szczurradar.api.sources;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import pl.szczurradar.api.agents.assessor.contract.ItemType;

/**
 * A post as a source hands it to the pipeline: the shared model's fields, already mapped, and the
 * element's raw JSON, which the pipeline stores apart. Nothing in it is specific to one source.
 *
 * @param itemType the shared model's type, never the source's own name
 * @param sourceItemId the id in the source, unique per source and item type
 * @param channel the source's channel the post came from (Wykop: the tag, Reddit: the subreddit), in the
 *     source's own spelling; the same as the assessment input's {@code channels[0]}
 * @param title the title, {@code null} if none (a post's text is mapped as for the assessment input)
 * @param body the text, {@code null} if none
 * @param author the author's name in the source, never passed to the model
 * @param slug the last part of the post's address in the source
 * @param webUrl the post's address in the source
 * @param tags the source's tags or channels of the post
 * @param raw the element's JSON bytes, exactly as the pipeline will store them
 */
public record SourcePost(Source source, ItemType itemType, String sourceItemId, String channel,
		Instant publishedAt, @Nullable String title, @Nullable String body, @Nullable String author,
		@Nullable String slug, @Nullable String webUrl, List<String> tags, byte[] raw) {

	public SourcePost {
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(itemType, "itemType");
		Objects.requireNonNull(sourceItemId, "sourceItemId");
		Objects.requireNonNull(channel, "channel");
		Objects.requireNonNull(publishedAt, "publishedAt");
		tags = List.copyOf(tags);
		Objects.requireNonNull(raw, "raw");
	}

}
