package pl.szczurradar.api.sources.reddit;

import java.time.Duration;
import java.time.InstantSource;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import pl.szczurradar.api.sources.LocalApiServer.Reply;
import pl.szczurradar.api.sources.LocalApiServer.Request;

/**
 * Reddit for the engine's integration tests ({@code PipelineRunIT}, {@code RunWatchdogIT},
 * {@code MissingDaysIT}), imported by those that use it: the production {@link RedditApiClient},
 * {@link RedditRepository}, {@link RedditFetcher} and {@link RedditPosts} over a {@link LocalRedditServer} in
 * place of Arctic Shift. Its {@link RedditRepository} bean stands in for the one {@link RedditConfiguration}
 * makes from {@code REDDIT_SUBREDDITS}, which no test sets: a second repository bean would make the fetcher's
 * {@code Optional<RedditRepository>} ambiguous.
 *
 * <p>Every search answers the real, anonymized day of r/inwestowanie ({@value #DEFAULT_PAGE}: 14 posts,
 * none removed) unless a scenario switches it: {@link #down()} (HTTP 500 with an error envelope, every
 * attempt), {@link #empty()} (no post), {@link #page(String)} (another fixture), {@link #hold()} (each search
 * waits without a bound until {@link #release()} or an interrupt) or {@link #delay(Duration)} (only the
 * watchdog's scenarios, which measure time). Not configured until a scenario names its subreddits
 * ({@link #configure(List)}): the repository's {@link RedditRepository#subreddits()} is that list, empty by
 * default, so a range run that names no source fetches only the others. {@link #reset()} brings all of it
 * back.
 *
 * <p>The server and the switches are static, as they outlive each Spring context of the tests, and every
 * switch is {@code volatile}: the server's threads and the run's queue thread read what the test thread set.
 * The client waits for nothing between its attempts and the repository between its pages, so a Reddit that
 * stays down fails the fetch at once (after its 3 attempts).
 */
@TestConfiguration(proxyBeanMethods = false)
public class LocalReddit {

	/** The answer of every search by default: r/inwestowanie's whole day of 2026-10-27 (14 posts). */
	public static final String DEFAULT_PAGE = "search-posts-inwestowanie.json";

	/** The short synthetic body of {@link #down()}, in Arctic Shift's error envelope. */
	public static final String DOWN_BODY = "{\"data\":null,\"error\":\"Internal server error\"}";

	/**
	 * One attempt's time-out: far longer than any scenario holds or delays a search, so a held search is
	 * ended only by the run's stop (an interrupt) or {@link #release()}, never by the client's own time-out.
	 */
	private static final Duration TIMEOUT = Duration.ofSeconds(60);

	/** The searches since the last {@link #reset()}, in order. */
	private static final List<Request> REQUESTS = new CopyOnWriteArrayList<>();

	private static volatile List<String> subreddits = List.of();

	private static volatile boolean down;

	private static volatile boolean empty;

	private static volatile String page = DEFAULT_PAGE;

	private static volatile @Nullable CountDownLatch hold;

	private static volatile @Nullable Duration delay;

	private static final LocalRedditServer SERVER = new LocalRedditServer(LocalReddit::answer);

	@Bean
	RedditRepository redditRepository() {
		RedditApiClient client = new RedditApiClient(SERVER.baseUrl(), TIMEOUT, wait -> {
		}, InstantSource.system());
		return new RedditRepository(client, List.of(), pause -> {
		}, Duration.ZERO) {

			@Override
			public List<String> subreddits() {
				return subreddits;
			}

		};
	}

	/** The subreddits a fetch reads, in order; an empty list (the default) is Reddit not configured. */
	public static void configure(List<String> names) {
		subreddits = List.copyOf(names);
	}

	/** Every search answers HTTP 500 with {@link #DOWN_BODY}. */
	public static void down() {
		down = true;
	}

	/** Every search finds no post in the range. */
	public static void empty() {
		empty = true;
	}

	/** Every search answers {@code fixture} from {@code fixtures/reddit/}. */
	public static void page(String fixture) {
		page = fixture;
	}

	/** Holds each search until {@link #release()} (or an interrupt of the server's thread) before it answers. */
	public static void hold() {
		hold = new CountDownLatch(1);
	}

	/** Lets a held search answer. */
	public static void release() {
		CountDownLatch latch = hold;
		if (latch != null) {
			latch.countDown();
		}
	}

	/** Pauses each search for {@code pause} before it answers: only for the watchdog's scenarios. */
	public static void delay(Duration pause) {
		delay = pause;
	}

	/**
	 * Back to the start: not configured, the default page, no outage, hold (a held search released) or delay,
	 * no search recorded.
	 */
	public static void reset() {
		release();
		subreddits = List.of();
		down = false;
		empty = false;
		page = DEFAULT_PAGE;
		hold = null;
		delay = null;
		REQUESTS.clear();
	}

	/** The searches since the last {@link #reset()}, in order; each attempt of the client is one. */
	public static List<Request> requests() {
		return List.copyOf(REQUESTS);
	}

	/** How many searches arrived since the last {@link #reset()}. */
	public static int calls() {
		return REQUESTS.size();
	}

	/** The answer to a search, on the server's thread: recorded first, then held or delayed, then answered. */
	private static Reply answer(Request request) {
		REQUESTS.add(request);
		try {
			CountDownLatch latch = hold;
			if (latch != null) {
				// Without a bound: only release() or an interrupt ends it.
				latch.await();
			}
			Duration pause = delay;
			if (pause != null) {
				Thread.sleep(pause);
			}
		}
		catch (InterruptedException ex) {
			// Never an empty day for a held search: the search fails, as when Arctic Shift is down.
			Thread.currentThread().interrupt();
			return new Reply(500, DOWN_BODY);
		}
		if (down) {
			return new Reply(500, DOWN_BODY);
		}
		if (empty) {
			return Reply.ok("{\"data\":[]}");
		}
		return Reply.ok(LocalRedditServer.fixture(page));
	}

}
