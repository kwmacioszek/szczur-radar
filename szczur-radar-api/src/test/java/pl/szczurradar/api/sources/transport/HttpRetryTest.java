package pl.szczurradar.api.sources.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntPredicate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import pl.szczurradar.api.sources.LocalApiServer.Reply;
import pl.szczurradar.api.sources.wykop.LocalWykopServer;

/**
 * {@link HttpRetry} against a local server (any source's API: the loop knows none). Waits are recorded
 * instead of slept, and they move a fake clock; request counts prove there are no hidden retries. The
 * source's own messages and their masking are in its client's test ({@code WykopApiClientTest}).
 */
@ExtendWith(OutputCaptureExtension.class)
class HttpRetryTest {

	private static final String CALL = "GET posts/search";

	private static final Duration TIMEOUT = Duration.ofSeconds(5);

	private static final List<String> WAIT_HEADERS = List.of("Retry-After", "X-RateLimit-Reset");

	private static final String SECRET = "s3cret-value";

	private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-30T00:30:00Z"));

	private final List<Duration> waits = new ArrayList<>();

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

	@AfterEach
	void closeTheClient() {
		http.close();
	}

	@Test
	void anAnswerThatIsNotRetriedIsReturnedAfterOneRequest() {
		for (int status : new int[] { 200, 401, 404, 422 }) {
			try (LocalWykopServer server = new LocalWykopServer(request -> new Reply(status, "{}"))) {
				HttpResponse<String> response = exchange(server, HttpRetry::transientStatus, far());

				assertThat(response.statusCode()).isEqualTo(status);
				assertThat(server.requests()).hasSize(1);
			}
		}
		assertThat(waits).isEmpty();
	}

	@Test
	void aStatusIsRetriedOnlyWhenTheSourceSaysSo() {
		IntPredicate withTimeouts = status -> HttpRetry.transientStatus(status) || status == 422;
		try (LocalWykopServer server = new LocalWykopServer(
				request -> new Reply(422, "{\"error\":\"Timeout. Maybe slow down a bit\"}"))) {
			assertThatExceptionOfType(HttpRetryException.class)
				.isThrownBy(() -> exchange(server, withTimeouts, far()))
				.satisfies(ex -> {
					assertThat(ex.kind()).isEqualTo(HttpRetryException.Kind.HTTP_STATUS);
					assertThat(ex.status()).isEqualTo(422);
					assertThat(ex.attempts()).isEqualTo(3);
					assertThat(ex.getMessage()).startsWith("Reddit GET posts/search answered HTTP 422: ")
						.endsWith(" after 3 attempts");
				});
			assertThat(server.requests()).hasSize(3);
			assertThat(waits).containsExactly(Duration.ofSeconds(2), Duration.ofSeconds(4));
		}
	}

	@Test
	void aServerErrorIsRetriedAndASuccessEndsTheCall() {
		AtomicInteger requests = new AtomicInteger();
		try (LocalWykopServer server = new LocalWykopServer(request -> requests.incrementAndGet() == 1
				? new Reply(503, "{}") : Reply.ok("{\"data\":[]}"))) {
			HttpResponse<String> response = exchange(server, HttpRetry::transientStatus, far());

			assertThat(response.statusCode()).isEqualTo(200);
			assertThat(server.requests()).hasSize(2);
			assertThat(waits).containsExactly(Duration.ofSeconds(2));
		}
	}

	@Test
	void theFirstWaitHeaderWinsAndTheNextOneIsReadWithoutIt() {
		assertThat(waitsAfterRateLimit(Map.of("X-RateLimit-Reset", "7"))).containsExactly(Duration.ofSeconds(7));
		assertThat(waitsAfterRateLimit(Map.of("Retry-After", "3", "X-RateLimit-Reset", "9")))
			.containsExactly(Duration.ofSeconds(3));
		// An unreadable first header is no wait: the next readable one is.
		assertThat(waitsAfterRateLimit(Map.of("Retry-After", "soon", "X-RateLimit-Reset", "5")))
			.containsExactly(Duration.ofSeconds(5));
		assertThat(waitsAfterRateLimit(Map.of("Retry-After", "soon"))).containsExactly(Duration.ofSeconds(2));
	}

	@Test
	void aLongWaitIsCappedAt30Seconds() {
		assertThat(waitsAfterRateLimit(Map.of("X-RateLimit-Reset", "120"))).containsExactly(Duration.ofSeconds(30));
	}

	@Test
	void aPassedDeadlineSendsNoRequest() {
		try (LocalWykopServer server = new LocalWykopServer(request -> Reply.ok("{}"))) {
			assertThatExceptionOfType(HttpRetryException.class)
				.isThrownBy(() -> exchange(server, HttpRetry::transientStatus, now.get().minusSeconds(1)))
				.satisfies(ex -> {
					assertThat(ex.kind()).isEqualTo(HttpRetryException.Kind.DEADLINE_EXCEEDED);
					assertThat(ex.attempts()).isZero();
					assertThat(ex.getMessage())
						.isEqualTo("Reddit GET posts/search reached its deadline before the first attempt");
				});
			assertThat(server.requests()).isEmpty();
		}
	}

	@Test
	void aWaitThatWouldEndAfterTheDeadlineIsNotStarted() {
		try (LocalWykopServer server = new LocalWykopServer(request -> new Reply(503, "{}"))) {
			// The first attempt fits (5 s time-out), the 2 s wait and the next attempt's 5 s do not.
			assertThatExceptionOfType(HttpRetryException.class)
				.isThrownBy(() -> exchange(server, HttpRetry::transientStatus, now.get().plusSeconds(6)))
				.satisfies(ex -> {
					assertThat(ex.kind()).isEqualTo(HttpRetryException.Kind.DEADLINE_EXCEEDED);
					assertThat(ex.attempts()).isEqualTo(1);
					assertThat(ex.getMessage()).contains("reached its deadline after 1 attempt(s)", "HTTP 503");
				});
			assertThat(server.requests()).hasSize(1);
			assertThat(waits).isEmpty();
		}
	}

	@Test
	void anInterruptedWaitStopsTheCallAndKeepsTheInterruptFlag() {
		try (LocalWykopServer server = new LocalWykopServer(request -> new Reply(503, "{}"))) {
			HttpRetry retry = new HttpRetry("Reddit", HttpRetry::transientStatus, WAIT_HEADERS, wait -> {
				throw new InterruptedException("stopping");
			}, now::get);

			try {
				assertThatExceptionOfType(HttpRetryException.class)
					.isThrownBy(() -> retry.exchange(http, request(server), CALL, TIMEOUT, far(), text -> text))
					.satisfies(ex -> {
						assertThat(ex.kind()).isEqualTo(HttpRetryException.Kind.INTERRUPTED);
						assertThat(ex.getMessage())
							.isEqualTo("interrupted while waiting to retry Reddit GET posts/search");
						assertThat(ex.getCause()).isInstanceOf(InterruptedException.class);
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
		LocalWykopServer server = new LocalWykopServer(request -> Reply.ok("{}"));
		server.close();

		assertThatExceptionOfType(HttpRetryException.class)
			.isThrownBy(() -> exchange(server, HttpRetry::transientStatus, far()))
			.satisfies(ex -> {
				assertThat(ex.kind()).isEqualTo(HttpRetryException.Kind.TRANSPORT);
				assertThat(ex.status()).isZero();
				assertThat(ex.getMessage()).startsWith("Reddit GET /api/v3/posts/search failed: ")
					.endsWith(" after 3 attempts");
				assertThat(ex.getCause()).isNull();
			});
		assertThat(waits).containsExactly(Duration.ofSeconds(2), Duration.ofSeconds(4));
	}

	@Test
	void theClientsCleaningCoversTheErrorAndEveryWarning(CapturedOutput output) {
		try (LocalWykopServer server = new LocalWykopServer(
				request -> new Reply(503, "{\"error\":\"token " + SECRET + " overloaded\"}"))) {
			HttpRetry retry = retry(HttpRetry::transientStatus);

			assertThatExceptionOfType(HttpRetryException.class)
				.isThrownBy(() -> retry.exchange(http, request(server), CALL, TIMEOUT, far(),
						text -> text.replace(SECRET, "<hidden>")))
				.satisfies(ex -> assertThat(ex.getMessage()).contains("token <hidden> overloaded")
					.doesNotContain(SECRET));
			assertThat(server.requests()).hasSize(3);
		}
		assertThat(output).contains("WARN", "Retrying Reddit GET posts/search in 2 s after attempt 1 of 3",
				"Retrying Reddit GET posts/search in 4 s after attempt 2 of 3")
			.doesNotContain(SECRET);
	}

	/** The waits of a call answered 429 with {@code headers}, then 200; two requests. */
	private List<Duration> waitsAfterRateLimit(Map<String, String> headers) {
		waits.clear();
		AtomicInteger requests = new AtomicInteger();
		try (LocalWykopServer server = new LocalWykopServer(request -> requests.incrementAndGet() == 1
				? new Reply(429, "{}", headers) : Reply.ok("{}"))) {
			exchange(server, HttpRetry::transientStatus, far());

			assertThat(server.requests()).hasSize(2);
			return List.copyOf(waits);
		}
	}

	private HttpResponse<String> exchange(LocalWykopServer server, IntPredicate retried, Instant deadline) {
		return retry(retried).exchange(http, request(server), CALL, TIMEOUT, deadline, text -> text);
	}

	/** A loop whose waits are recorded and move the fake clock instead of sleeping. */
	private HttpRetry retry(IntPredicate retried) {
		return new HttpRetry("Reddit", retried, WAIT_HEADERS, wait -> {
			waits.add(wait);
			now.updateAndGet(instant -> instant.plus(wait));
		}, now::get);
	}

	/** A deadline every attempt and wait fits. */
	private Instant far() {
		return now.get().plus(Duration.ofMinutes(10));
	}

	private static HttpRequest request(LocalWykopServer server) {
		URI uri = server.baseUrl().resolve("posts/search");
		return HttpRequest.newBuilder(uri).timeout(TIMEOUT).GET().build();
	}

}
