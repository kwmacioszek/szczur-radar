package pl.szczurradar.api.sources.reddit;

import java.util.List;
import java.util.Objects;

import tools.jackson.databind.JsonNode;

/**
 * The posts a fetch returned, each once; how many elements could not be read as a {@link RedditItem}
 * (no id, no time, no subreddit, an unexpected type) and were skipped; and how many posts were left out as
 * removed or deleted ({@link RedditItem#removed()}), which are neither elements nor skipped.
 */
public record RedditSearchResult(List<Element> elements, int skippedElements, int removed) {

	public RedditSearchResult {
		elements = List.copyOf(elements);
	}

	/**
	 * One post: the fields mapping needs and the post exactly as the answer had it, with every field (score,
	 * flair, media, …) — the raw payload the pipeline keeps apart.
	 */
	public record Element(RedditItem item, JsonNode raw) {

		public Element {
			Objects.requireNonNull(item, "item");
			Objects.requireNonNull(raw, "raw");
		}

	}

}
