package pl.szczurradar.api.sources.reddit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import pl.szczurradar.api.sources.Source;
import pl.szczurradar.api.sources.SourceFetcher;
import pl.szczurradar.api.sources.SourcePost;
import pl.szczurradar.api.sources.UnmappableItemException;

/**
 * {@link SourceFetcher} for Reddit: the posts of the configured subreddits created in the range, read from
 * Arctic Shift by Reddit's own clock ({@code created_utc}, UTC seconds). Configured when
 * {@code REDDIT_SUBREDDITS} names at least one subreddit ({@link RedditConfiguration}). Posts taken down are
 * no elements of the fetch: the repository leaves them out before counting, so they are neither fetched nor
 * skipped.
 */
public class RedditFetcher implements SourceFetcher {

	private final Optional<RedditRepository> repository;

	RedditFetcher(Optional<RedditRepository> repository) {
		this.repository = repository;
	}

	@Override
	public Source source() {
		return Source.REDDIT;
	}

	@Override
	public boolean configured() {
		return repository.map(reddit -> !reddit.subreddits().isEmpty()).orElse(false);
	}

	@Override
	public Fetched fetch(Instant from, Instant to) {
		RedditRepository reddit = repository.filter(configured -> !configured.subreddits().isEmpty())
			.orElseThrow(() -> new IllegalStateException("Reddit is not configured (REDDIT_SUBREDDITS)"));
		RedditSearchResult result = reddit.postsCreatedBetween(from, to);
		List<SourcePost> posts = new ArrayList<>();
		int skipped = result.skippedElements();
		for (RedditSearchResult.Element element : result.elements()) {
			try {
				posts.add(RedditPosts.post(element));
			}
			catch (UnmappableItemException ex) {
				skipped++;
			}
		}
		return new Fetched(posts, result.elements().size() + result.skippedElements(), skipped);
	}

}
