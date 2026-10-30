package pl.szczurradar.api.sources.reddit;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.jspecify.annotations.Nullable;

import pl.szczurradar.api.agents.assessor.contract.AssessmentInputV1;
import pl.szczurradar.api.agents.assessor.contract.Context;
import pl.szczurradar.api.agents.assessor.contract.Item;
import pl.szczurradar.api.agents.assessor.contract.ItemType;
import pl.szczurradar.api.sources.UnmappableItemException;
import pl.szczurradar.api.text.Nul;
import pl.szczurradar.api.text.Whitespace;

/**
 * A Reddit post (as Arctic Shift returns it) → {@link AssessmentInputV1} (contract-v2.md §2): a self-post
 * ({@code is_self} true) becomes a {@code post}, any other post a {@code link}; both keep {@code title}
 * and take {@code selftext} as the body (a link post may have one). {@code created_utc} is UTC seconds, so
 * {@code published_at} has the offset {@code Z}; {@code channels} is the subreddit as the payload spells it.
 * Author, URL and id never reach the input. Text goes in unchanged except U+0000; empty or
 * white-space-only text becomes {@code null}.
 */
public final class RedditMapper {

	/** The {@code selftext} Reddit leaves in a post taken down by a moderator or Reddit. */
	static final String REMOVED = "[removed]";

	/** The {@code selftext} Reddit leaves in a post its author deleted. */
	static final String DELETED = "[deleted]";

	private RedditMapper() {
	}

	/**
	 * @throws UnmappableItemException for a removed or deleted post ({@code selftext} {@code [removed]} or
	 *     {@code [deleted]}; the repository leaves such posts out already), a post without a subreddit or
	 *     without text, or a missing or negative {@code created_utc}
	 */
	public static AssessmentInputV1 map(RedditItem item) {
		if (REMOVED.equals(item.selftext()) || DELETED.equals(item.selftext())) {
			throw new UnmappableItemException("Reddit post " + item.id() + " was removed or deleted");
		}
		if (item.subreddit() == null || Whitespace.normalize(item.subreddit()).isEmpty()) {
			throw new UnmappableItemException("Reddit post " + item.id() + " has no subreddit");
		}
		ItemType type = Boolean.TRUE.equals(item.isSelf()) ? ItemType.POST : ItemType.LINK;
		String title = textOrNull(item.title());
		String body = textOrNull(item.selftext());
		if (title == null && body == null) {
			throw new UnmappableItemException("Reddit post " + item.id() + " has no text");
		}
		return AssessmentInputV1.of(new Item(type, publishedAt(item), List.of(item.subreddit()), title, body),
				Context.NONE);
	}

	/** {@code created_utc} as an instant with the offset {@code Z} (Reddit's clock is UTC all year). */
	public static OffsetDateTime publishedAt(RedditItem item) {
		Long seconds = item.createdUtc();
		if (seconds == null || seconds < 0) {
			throw new UnmappableItemException(
					"Reddit post " + item.id() + " has no valid created_utc (" + seconds + ")");
		}
		return OffsetDateTime.ofInstant(Instant.ofEpochSecond(seconds), ZoneOffset.UTC);
	}

	/** {@code text} without U+0000 ({@link Nul}), or {@code null} when nothing but white space is left. */
	private static @Nullable String textOrNull(@Nullable String text) {
		String stored = Nul.removed(text);
		return stored == null || Whitespace.normalize(stored).isEmpty() ? null : stored;
	}

}
