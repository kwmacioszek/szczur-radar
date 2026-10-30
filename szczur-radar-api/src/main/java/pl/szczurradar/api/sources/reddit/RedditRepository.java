package pl.szczurradar.api.sources.reddit;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the posts of the chosen subreddits from Arctic Shift's search ({@code GET api/posts/search} with
 * {@code subreddit}, {@code after}, {@code before}, {@code sort=asc}, {@code limit}): every post created
 * within a time range, subreddit after subreddit, oldest first, {@value #PAGE_SIZE} per page; each post is
 * kept once (by id). Posts come back in Reddit's shape: the fields mapping needs ({@link RedditItem}) next to
 * the raw post; mapping is {@code RedditMapper}'s job.
 *
 * <p>Paging goes by time, as Arctic Shift has no cursor and no total, and its {@code after} and
 * {@code before} are exclusive. A subreddit's first page asks for {@code after} one second before the range
 * and {@code before} one second after it, so both ends are in; each next page for {@code after} = the last
 * post's {@code created_utc} − 1: the last second is read again, so a post of that second that the page's
 * limit cut off is not lost, and the posts read twice are kept once. A page shorter than {@value #PAGE_SIZE}
 * ends the subreddit. A full page without a post not seen before (the archive ignored {@code after}, or a
 * second holds more than a page of posts), or posts whose {@code created_utc} goes down, cannot go on
 * without a gap: the fetch fails ({@link RedditFailure#INCOMPLETE_PAGING}) rather than return an incomplete
 * range.
 *
 * <p>Left out and counted: a post taken down ({@link RedditItem#removed()}: by a moderator, Reddit or its
 * author) is the source's state, not content, and no element of the fetch — neither in
 * {@link RedditSearchResult#elements()} nor skipped (a skipped element alerts the operator, and most nights
 * have removed posts); an element without a string {@code id}, a {@code created_utc} number ≥ 0 or a
 * {@code subreddit}, or of an unexpected type, is skipped (one without an id at every occurrence). Each
 * subreddit's counts are one INFO line.
 */
public class RedditRepository {

	/** Arctic Shift's largest numeric {@code limit}. */
	static final int PAGE_SIZE = 100;

	/** Guard against a subreddit whose pages never end (per subreddit). */
	static final int MAX_PAGES = 50;

	static final Duration DEFAULT_PAUSE = Duration.ofMillis(300);

	/**
	 * The deadline of one {@link #postsCreatedBetween} (every subreddit) for the client's attempts and waits.
	 * With the last attempt's time-out (and one connection retry inside the JDK) a fetch ends within about
	 * {@code FETCH_DEADLINE + 2 × RedditApiClient.DEFAULT_TIMEOUT}, which must stay below the watchdog's
	 * shortest default ({@code PipelineStallSettings.MIN_DEFAULT}; pinned by a test).
	 */
	static final Duration FETCH_DEADLINE = Duration.ofMinutes(10);

	static final String SEARCH = "api/posts/search";

	private static final Logger log = LoggerFactory.getLogger(RedditRepository.class);

	/** Waits between pages and between subreddits; replaced in tests. */
	@FunctionalInterface
	public interface Pause {

		void pause(Duration duration) throws InterruptedException;

		Pause REAL = Thread::sleep;

	}

	private final RedditApiClient client;

	private final List<String> subreddits;

	private final Pause pause;

	private final Duration between;

	private final JsonMapper json = JsonMapper.builder().build();

	/** @param subreddits the subreddits to read, in order, each as Reddit spells it (without {@code r/}) */
	public RedditRepository(RedditApiClient client, List<String> subreddits) {
		this(client, subreddits, Pause.REAL, DEFAULT_PAUSE);
	}

	public RedditRepository(RedditApiClient client, List<String> subreddits, Pause pause, Duration between) {
		this.client = Objects.requireNonNull(client, "client");
		this.subreddits = List.copyOf(subreddits);
		this.pause = Objects.requireNonNull(pause, "pause");
		this.between = Objects.requireNonNull(between, "between");
	}

	/** The subreddits a fetch reads, in order (unmodifiable); empty means none. */
	public List<String> subreddits() {
		return subreddits;
	}

	/**
	 * The posts of every subreddit of {@link #subreddits()}, in that order, created between {@code from} and
	 * {@code to} (both inclusive, to the second), each once; removed posts left out and counted.
	 *
	 * @throws RedditApiException when a subreddit's paging cannot go on without a gap
	 *     ({@link RedditFailure#INCOMPLETE_PAGING}), the page limit is reached, the API fails (after the
	 *     client's retries), the thread is interrupted or the fetch does not fit {@link #FETCH_DEADLINE}
	 *     ({@link RedditFailure#DEADLINE_EXCEEDED}) — never with part of the range as a result
	 */
	public RedditSearchResult postsCreatedBetween(Instant from, Instant to) {
		Objects.requireNonNull(from, "from");
		Objects.requireNonNull(to, "to");
		Instant deadline = client.deadline(FETCH_DEADLINE);
		Map<String, RedditSearchResult.Element> elements = new LinkedHashMap<>();
		int skipped = 0;
		int removed = 0;
		boolean first = true;
		for (String subreddit : subreddits()) {
			Read read = read(subreddit, from, to, deadline, !first);
			first = false;
			read.elements().forEach(elements::putIfAbsent);
			skipped += read.skipped();
			removed += read.removed();
			log.info("Reddit r/{}: {} post(s) in range, {} removed or deleted left out, {} of unexpected shape",
					subreddit, read.elements().size(), read.removed(), read.skipped());
		}
		return new RedditSearchResult(new ArrayList<>(elements.values()), skipped, removed);
	}

	/**
	 * Every page of one subreddit, keyed by post id.
	 *
	 * @param pauseFirst pause before the first page too (a subreddit read before it just asked)
	 */
	private Read read(String subreddit, Instant from, Instant to, Instant deadline, boolean pauseFirst) {
		Map<String, RedditSearchResult.Element> elements = new LinkedHashMap<>();
		Set<String> seen = new HashSet<>();
		int skipped = 0;
		int removed = 0;
		long after = from.getEpochSecond() - 1;
		long before = to.getEpochSecond() + 1;
		long latest = Long.MIN_VALUE;
		for (int page = 1; page <= MAX_PAGES; page++) {
			if (page > 1 || pauseFirst) {
				pause();
			}
			Map<String, Object> query = new LinkedHashMap<>();
			query.put("subreddit", subreddit);
			query.put("after", after);
			query.put("before", before);
			query.put("sort", "asc");
			query.put("limit", PAGE_SIZE);
			JsonNode data = client.get(SEARCH, query, deadline);
			int fresh = 0;
			for (JsonNode post : data) {
				String id = text(post.path("id"));
				Long created = seconds(post.path("created_utc"));
				if (created != null) {
					if (created < latest) {
						throw new RedditApiException(RedditFailure.INCOMPLETE_PAGING, "Reddit r/" + subreddit + " page "
								+ page + " goes back in time: created_utc " + created + " after " + latest);
					}
					latest = created;
				}
				if (id != null && !seen.add(id)) {
					// The second read again from the previous page: kept once.
					continue;
				}
				if (id != null) {
					fresh++;
				}
				if (id == null || created == null || text(post.path("subreddit")) == null) {
					skipped++;
					continue;
				}
				RedditItem item;
				try {
					item = json.treeToValue(post, RedditItem.class);
				}
				catch (JacksonException ex) {
					// One element of an unexpected shape must not lose the rest of the range.
					skipped++;
					continue;
				}
				if (item.removed()) {
					removed++;
					continue;
				}
				elements.put(id, new RedditSearchResult.Element(item, post));
			}
			if (data.size() < PAGE_SIZE) {
				return new Read(elements, skipped, removed);
			}
			if (fresh == 0) {
				throw new RedditApiException(RedditFailure.INCOMPLETE_PAGING, "Reddit r/" + subreddit + " page " + page
						+ " is full without a post not seen before (after=" + after + ")");
			}
			if (latest == Long.MIN_VALUE) {
				throw new RedditApiException(RedditFailure.INCOMPLETE_PAGING,
						"Reddit r/" + subreddit + " page " + page + " is full without a created_utc to go on from");
			}
			// A full page: the next one from its last second, read again.
			after = latest - 1;
		}
		throw new RedditApiException(RedditFailure.PAGE_LIMIT,
				"Reddit r/" + subreddit + " did not end within " + MAX_PAGES + " pages");
	}

	/** One subreddit's posts (keyed by id), its skipped elements and its removed posts. */
	private record Read(Map<String, RedditSearchResult.Element> elements, int skipped, int removed) {
	}

	/** A non-blank string, else {@code null}. */
	private static @Nullable String text(JsonNode node) {
		return node.isString() && !node.stringValue().isBlank() ? node.stringValue() : null;
	}

	/** A number of seconds ≥ 0, else {@code null}. */
	private static @Nullable Long seconds(JsonNode node) {
		if (!node.isNumber() || !node.canConvertToLong()) {
			return null;
		}
		long seconds = node.asLong();
		return seconds >= 0 ? seconds : null;
	}

	private void pause() {
		try {
			pause.pause(between);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new RedditApiException(RedditFailure.INTERRUPTED, "interrupted between Reddit pages", ex);
		}
	}

}
