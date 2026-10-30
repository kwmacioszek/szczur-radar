package pl.szczurradar.api.sources.reddit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

/**
 * The fields of a Reddit post (as Arctic Shift returns it, the full Reddit object) that mapping and leaving
 * out removed posts need. Everything else of the post (score, flair, media, …) is ignored here; the raw
 * post is kept apart by the pipeline.
 *
 * @param id the post's base-36 id, e.g. {@code 1xgm63i}
 * @param subreddit the subreddit's name as Reddit spells it, without {@code r/}
 * @param title the title
 * @param selftext the text of a self-post (a link post may have one too); {@code [removed]} or
 *     {@code [deleted]} when the post was taken down
 * @param isSelf {@code true} for a self-post (text), {@code false} for a link post
 * @param url a self-post's own address or a link post's target
 * @param permalink the post's path on reddit.com, e.g. {@code /r/stocks/comments/<id>/<slug>/}
 * @param author the author's nick, {@code [deleted]} for a deleted account; never passed to the model
 *     (contract-v2.md §2)
 * @param createdUtc when it was posted: seconds since the epoch, UTC
 * @param removedByCategory who took the post down ({@code moderator}, {@code reddit}, {@code deleted}, …),
 *     {@code null} for a live post
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RedditItem(@JsonProperty("id") @Nullable String id, @Nullable String subreddit, @Nullable String title,
		@Nullable String selftext, @JsonProperty("is_self") @Nullable Boolean isSelf, @Nullable String url,
		@Nullable String permalink, @Nullable String author, @JsonProperty("created_utc") @Nullable Long createdUtc,
		@JsonProperty("removed_by_category") @Nullable String removedByCategory) {

	/** The post's address on reddit.com, if it has a permalink. */
	public @Nullable String webUrl() {
		return permalink == null || permalink.isEmpty() ? null : "https://www.reddit.com" + permalink;
	}

	/**
	 * Whether the post was taken down (by a moderator, Reddit or its author): it has a
	 * {@code removed_by_category}, or its {@code selftext} is exactly {@code [removed]} or {@code [deleted]}.
	 * Such a post is the source's state, not content: it is left out of a fetch.
	 */
	public boolean removed() {
		return removedByCategory != null || "[removed]".equals(selftext) || "[deleted]".equals(selftext);
	}

}
