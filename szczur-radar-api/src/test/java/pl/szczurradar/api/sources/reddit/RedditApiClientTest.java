package pl.szczurradar.api.sources.reddit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import tools.jackson.databind.JsonNode;

import pl.szczurradar.api.sources.LocalApiServer.Reply;
import pl.szczurradar.api.sources.LocalApiServer.Request;

/**
 * {@link RedditApiClient} against a local server in place of Arctic Shift, on the real answer of
 * r/inwestowanie for 2026-10-27 ({@code fixtures/reddit/search-posts-inwestowanie.json}). Error bodies are
 * short synthetic JSON in the API's envelope ({@code {"data":null,"error":…}}). Waits between attempts are
 * recorded instead of slept, and they move a fake clock; request counts prove there are no hidden retries.
 */
@ExtendWith(OutputCaptureExtension.class)
class RedditApiClientTest {

	private static final String SEARCH = "api/posts/search";

	private static final String DAY = LocalRedditServer.fixture("search-posts-inwestowanie.json");

	private static final String SLOW_DOWN = "{\"data\":null,\"error\":\"Timeout. Maybe slow down a bit\"}";

	private static final String SERVER_ERROR = "{\"data\":null,\"error\":\"Internal server error\"}";

	private static final Duration TIMEOUT = Duration.ofSeconds(5);

	private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-30T00:30:00Z"));

	private final List<Duration> waits = new ArrayList<>();

	@Test
	void sendsThePathAndTheQueryWithItsUserAgentAndNoAuthorization() {
		try (LocalRedditServer server = new LocalRedditServer(request -> Reply.ok(DAY))) {
			RedditApiClient client = client(server);
			Map<String, Object> query = new LinkedHashMap<>();
			query.put("subreddit", "inwestowanie");
			query.put("after", 1793055599L);
			query.put("before", 1793142000L);
			query.put("sort", "asc");
			query.put("limit", 100);

			JsonNode data = client.get(SEARCH, query, far());

			assertThat(data.isArray()).isTrue();
			assertThat(data).hasSize(14);
			assertThat(data.get(0).path("id").stringValue()).isEqualTo("1xgm63i");
			Request request = server.requests().getFirst();
			assertThat(server.requests()).hasSize(1);
			assertThat(request.method()).isEqualTo("GET");
			assertThat(request.path()).isEqualTo("/api/posts/search");
			assertThat(request.query())
				.isEqualTo("subreddit=inwestowanie&after=1793055599&before=1793142000&sort=asc&limit=100");
			assertThat(request.header("User-Agent"))
				.isEqualTo("szczur-radar (+https://github.com/kwmacioszek/szczur-radar)");
			assertThat(request.header("Accept")).isEqualTo("application/json");
			assertThat(request.authorization()).isNull();
			assertThat(client).hasToString("RedditApiClient[baseUrl=" + server.baseUrl() + "]");
		}
	}

	@Test
	void aServerErrorIsRetriedAndASuccessEndsTheCall(CapturedOutput output) {
		AtomicInteger requests = new AtomicInteger();
		try (LocalRedditServer server = new LocalRedditServer(
				request -> requests.incrementAndGet() == 1 ? new Reply(503, SERVER_ERROR) : Reply.ok(DAY))) {
			JsonNode data = client(server).get(SEARCH, Map.of(), far());

			assertThat(data).hasSize(14);
			assertThat(server.requests()).hasSize(2);
			assertThat(waits).containsExactly(Duration.ofSeconds(2));
		}
		assertThat(output).contains("WARN", "Retrying Reddit GET api/posts/search in 2 s after attempt 1 of 3");
	}

	@Test
	void aServerErrorThatStaysFailsAfterThreeAttempts() {
		try (LocalRedditServer server = new LocalRedditServer(request -> new Reply(500, SERVER_ERROR))) {
			assertThatExceptionOfType(RedditApiException.class)
				.isThrownBy(() -> client(server).get(SEARCH, Map.of(), far()))
				.satisfies(ex -> {
					assertThat(ex.failure()).isEqualTo(RedditFailure.HTTP_STATUS);
					assertThat(ex.status()).isEqualTo(500);
					assertThat(ex.getMessage())
						.isEqualTo("Reddit GET api/posts/search answered HTTP 500: " + SERVER_ERROR
								+ " after 3 attempts");
				});
			assertThat(server.requests()).hasSize(3);
			assertThat(waits).containsExactly(Duration.ofSeconds(2), Duration.ofSeconds(4));
		}
	}

	@Test
	void aRateLimitWaitsTheSecondsOfXRateLimitReset() {
		// Arctic Shift's 429 says when its minute window resets (seconds), without Retry-After.
		AtomicInteger requests = new AtomicInteger();
		try (LocalRedditServer server = new LocalRedditServer(request -> requests.incrementAndGet() == 1
				? new Reply(429, "{\"data\":null,\"error\":\"Too many requests\"}", Map.of("X-RateLimit-Reset", "12"))
				: Reply.ok(DAY))) {
			assertThat(client(server).get(SEARCH, Map.of(), far())).hasSize(14);

			assertThat(server.requests()).hasSize(2);
			assertThat(waits).containsExactly(Duration.ofSeconds(12));
		}
	}

	@Test
	void slowDownIsRetried() {
		// 422 "Timeout. Maybe slow down a bit": the archive asks for a short wait, not a different request.
		AtomicInteger requests = new AtomicInteger();
		try (LocalRedditServer server = new LocalRedditServer(
				request -> requests.incrementAndGet() == 1 ? new Reply(422, SLOW_DOWN) : Reply.ok(DAY))) {
			assertThat(client(server).get(SEARCH, Map.of(), far())).hasSize(14);

			assertThat(server.requests()).hasSize(2);
			assertThat(waits).containsExactly(Duration.ofSeconds(2));
		}
	}

	@Test
	void aBadRequestAndANotFoundAreAskedOnce() {
		for (int status : new int[] { 400, 404 }) {
			waits.clear();
			String body = "{\"data\":null,\"error\":\"'after' must be a valid date (epoch, ISO, or relative)\"}";
			try (LocalRedditServer server = new LocalRedditServer(request -> new Reply(status, body))) {
				assertThatExceptionOfType(RedditApiException.class)
					.isThrownBy(() -> client(server).get(SEARCH, Map.of(), far()))
					.satisfies(ex -> {
						assertThat(ex.failure()).isEqualTo(RedditFailure.HTTP_STATUS);
						assertThat(ex.status()).isEqualTo(status);
						assertThat(ex.getMessage())
							.isEqualTo("Reddit GET api/posts/search answered HTTP " + status + ": " + body);
					});
				assertThat(server.requests()).hasSize(1);
				assertThat(waits).isEmpty();
			}
		}
	}

	@Test
	void aLongErrorBodyIsCapped() {
		String body = "<html>" + "x".repeat(2000) + "</html>";
		try (LocalRedditServer server = new LocalRedditServer(request -> new Reply(403, body))) {
			assertThatExceptionOfType(RedditApiException.class)
				.isThrownBy(() -> client(server).get(SEARCH, Map.of(), far()))
				.satisfies(ex -> assertThat(ex.getMessage()).hasSize(501)
					.startsWith("Reddit GET api/posts/search answered HTTP 403: <html>xxx")
					.endsWith("…"));
		}
	}

	@Test
	void anErrorEnvelopeOrAnAnswerWithoutADataListIsMalformed() {
		Map<String, String> answers = new LinkedHashMap<>();
		answers.put(SLOW_DOWN, "Reddit GET api/posts/search answered an error: Timeout. Maybe slow down a bit");
		answers.put("{\"data\":null}", "Reddit GET api/posts/search answered without a data list");
		answers.put("{\"data\":{}}", "Reddit GET api/posts/search answered without a data list");
		answers.put("[]", "Reddit GET api/posts/search answered without a data list");
		answers.put("<html>maintenance</html>", "Reddit GET api/posts/search answered with invalid JSON");
		answers.forEach((body, message) -> {
			try (LocalRedditServer server = new LocalRedditServer(request -> Reply.ok(body))) {
				assertThatExceptionOfType(RedditApiException.class)
					.isThrownBy(() -> client(server).get(SEARCH, Map.of(), far()))
					.satisfies(ex -> {
						assertThat(ex.failure()).isEqualTo(RedditFailure.MALFORMED_ANSWER);
						assertThat(ex.getMessage()).startsWith(message);
					});
				// A 2xx answer is never retried.
				assertThat(server.requests()).hasSize(1);
			}
		});
		assertThat(waits).isEmpty();
	}

	@Test
	void aPassedDeadlineSendsNoRequest() {
		try (LocalRedditServer server = new LocalRedditServer(request -> Reply.ok(DAY))) {
			RedditApiClient client = client(server);

			assertThatExceptionOfType(RedditApiException.class)
				.isThrownBy(() -> client.get(SEARCH, Map.of(), client.deadline(Duration.ofSeconds(-1))))
				.satisfies(ex -> {
					assertThat(ex.failure()).isEqualTo(RedditFailure.DEADLINE_EXCEEDED);
					assertThat(ex.getMessage())
						.isEqualTo("Reddit GET api/posts/search reached its deadline before the first attempt");
				});
			assertThat(server.requests()).isEmpty();
		}
	}

	@Test
	void aRetryThatWouldEndAfterTheDeadlineIsNotStarted() {
		try (LocalRedditServer server = new LocalRedditServer(request -> new Reply(503, SERVER_ERROR))) {
			RedditApiClient client = client(server);
			// The first attempt fits (5 s time-out), the 2 s wait and the next attempt's 5 s do not.
			Instant deadline = client.deadline(Duration.ofSeconds(6));

			assertThatExceptionOfType(RedditApiException.class)
				.isThrownBy(() -> client.get(SEARCH, Map.of(), deadline))
				.satisfies(ex -> {
					assertThat(ex.failure()).isEqualTo(RedditFailure.DEADLINE_EXCEEDED);
					assertThat(ex.getMessage()).contains("reached its deadline after 1 attempt(s)", "HTTP 503");
				});
			assertThat(server.requests()).hasSize(1);
			assertThat(waits).isEmpty();
		}
	}

	@Test
	void anInterruptedWaitStopsTheCallAndKeepsTheInterruptFlag() {
		try (LocalRedditServer server = new LocalRedditServer(request -> new Reply(503, SERVER_ERROR))) {
			RedditApiClient client = new RedditApiClient(server.baseUrl(), TIMEOUT, wait -> {
				throw new InterruptedException("stopping");
			}, now::get);

			try {
				assertThatExceptionOfType(RedditApiException.class)
					.isThrownBy(() -> client.get(SEARCH, Map.of(), far()))
					.satisfies(ex -> {
						assertThat(ex.failure()).isEqualTo(RedditFailure.INTERRUPTED);
						assertThat(ex.getMessage())
							.isEqualTo("interrupted while waiting to retry Reddit GET api/posts/search");
					});
				assertThat(Thread.currentThread().isInterrupted()).isTrue();
			}
			finally {
				Thread.interrupted();
			}
			assertThat(server.requests()).hasSize(1);
		}
	}

	@Test
	void aConnectionErrorIsATransportFailureAfterThreeAttempts() {
		LocalRedditServer server = new LocalRedditServer(request -> Reply.ok(DAY));
		RedditApiClient client = client(server);
		server.close();

		assertThatExceptionOfType(RedditApiException.class).isThrownBy(() -> client.get(SEARCH, Map.of(), far()))
			.satisfies(ex -> {
				assertThat(ex.failure()).isEqualTo(RedditFailure.TRANSPORT);
				assertThat(ex.getMessage()).startsWith("Reddit GET /api/posts/search failed: ")
					.endsWith("after 3 attempts");
			});
		// Three attempts: two waits between them (the closed server records nothing).
		assertThat(waits).containsExactly(Duration.ofSeconds(2), Duration.ofSeconds(4));
	}

	/** A deadline every attempt and wait fits. */
	private Instant far() {
		return now.get().plus(Duration.ofMinutes(10));
	}

	/** A client whose waits are recorded and move the fake clock instead of sleeping. */
	private RedditApiClient client(LocalRedditServer server) {
		return new RedditApiClient(server.baseUrl(), TIMEOUT, wait -> {
			waits.add(wait);
			now.updateAndGet(instant -> instant.plus(wait));
		}, now::get);
	}

}
