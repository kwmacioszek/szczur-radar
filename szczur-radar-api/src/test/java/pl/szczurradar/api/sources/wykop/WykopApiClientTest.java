package pl.szczurradar.api.sources.wykop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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
 * {@link WykopApiClient} against a local server. The auth answer and error bodies are short
 * synthetic JSON in the API's shape ({@code {"data": {"token": …}}}); a real one would carry a live
 * token. Waits between attempts are recorded instead of slept, and they move a fake clock; request
 * counts prove there are no hidden retries.
 */
@ExtendWith(OutputCaptureExtension.class)
class WykopApiClientTest {

	private static final String KEY = "w5-test-key";

	private static final String SECRET = "test-secret-value";

	private static final String TOKEN_1 = "{\"data\":{\"token\":\"token-1\"}}";

	private static final String UNAVAILABLE = "{\"error\":{\"code\":503,\"message\":\"Service unavailable\"}}";

	private static final Duration TIMEOUT = Duration.ofSeconds(5);

	private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-20T00:30:00Z"));

	private final List<Duration> waits = new ArrayList<>();

	@Test
	void authorizesOnceAndSendsTheTokenAndTheQuery() {
		try (LocalWykopServer server = new LocalWykopServer(request -> request.path().endsWith("/auth")
				? Reply.ok(TOKEN_1) : Reply.ok("{\"data\":[]}"))) {
			WykopApiClient client = client(server);
			Map<String, Object> query = new LinkedHashMap<>();
			query.put("tags", List.of("gielda"));
			query.put("date_from", "2026-10-01 00:00:00");
			query.put("page", 2);

			JsonNode first = client.get("search/entries", query);
			client.get("search/links", Map.of());

			assertThat(first.path("data").isArray()).isTrue();
			List<Request> requests = server.requests();
			assertThat(requests).extracting(Request::path)
				.containsExactly("/api/v3/auth", "/api/v3/search/entries", "/api/v3/search/links");
			assertThat(requests.getFirst().method()).isEqualTo("POST");
			assertThat(requests.getFirst().body())
				.isEqualTo("{\"data\":{\"key\":\"" + KEY + "\",\"secret\":\"" + SECRET + "\"}}");
			assertThat(requests.get(1).authorization()).isEqualTo("Bearer token-1");
			assertThat(requests.get(1).query()).isEqualTo("tags[]=gielda&date_from=2026-10-01 00:00:00&page=2");
		}
	}

	@Test
	void anExpiredTokenIsFetchedAgainOnceOn401() {
		AtomicInteger auths = new AtomicInteger();
		try (LocalWykopServer server = new LocalWykopServer(request -> {
			if (request.path().endsWith("/auth")) {
				return Reply.ok("{\"data\":{\"token\":\"token-" + auths.incrementAndGet() + "\"}}");
			}
			return "Bearer token-1".equals(request.authorization()) ? new Reply(401, "{\"error\":{\"code\":401}}")
					: Reply.ok("{\"data\":[]}");
		})) {
			client(server).get("search/entries", Map.of());

			assertThat(server.requests()).extracting(Request::path)
				.containsExactly("/api/v3/auth", "/api/v3/search/entries", "/api/v3/auth", "/api/v3/search/entries");
			assertThat(server.requests().getLast().authorization()).isEqualTo("Bearer token-2");
		}
	}

	@Test
	void aFailedAuthNamesTheStatusButNeverTheSecret() {
		try (LocalWykopServer server = new LocalWykopServer(
				request -> new Reply(403, "{\"error\":{\"code\":403,\"message\":\"Bad key\"}}"))) {
			assertThatExceptionOfType(WykopApiException.class)
				.isThrownBy(() -> client(server).get("search/entries", Map.of()))
				.satisfies(ex -> {
					assertThat(ex.failure()).isEqualTo(WykopFailure.HTTP_STATUS);
					assertThat(ex.status()).isEqualTo(403);
					assertThat(ex.getMessage()).contains("auth", "403").doesNotContain(SECRET, KEY);
				});
			assertThat(server.requests()).hasSize(1);
			assertThat(client(server).toString()).doesNotContain(SECRET, KEY);
		}
	}

	@Test
	void anAuthErrorQuotingTheKeyAndSecretIsMasked() {
		try (LocalWykopServer server = new LocalWykopServer(request -> new Reply(400,
				"{\"error\":{\"code\":400,\"message\":\"Invalid key " + KEY + " or secret " + SECRET + "\"}}"))) {
			assertThatExceptionOfType(WykopApiException.class)
				.isThrownBy(() -> client(server).get("search/entries", Map.of()))
				.satisfies(ex -> assertThat(ex.getMessage()).contains("HTTP 400", "Invalid key <hidden> or secret <hidden>")
					.doesNotContain(KEY, SECRET));
		}
	}

	@Test
	void aKeyInsideTheSecretLeavesNoPartOfTheSecret() {
		String key = "K3y";
		String secret = "S3cretK3yT4il";
		try (LocalWykopServer server = new LocalWykopServer(request -> new Reply(400,
				"{\"error\":{\"code\":400,\"message\":\"Invalid secret " + secret + "\"}}"))) {
			WykopApiClient client = new WykopApiClient(server.baseUrl(), key, secret, TIMEOUT, waits::add, now::get);

			assertThatExceptionOfType(WykopApiException.class)
				.isThrownBy(() -> client.get("search/entries", Map.of()))
				.satisfies(ex -> assertThat(ex.getMessage()).contains("Invalid secret <hidden>")
					.doesNotContain("S3cret", "T4il"));
		}
	}

	@Test
	void anErrorQuotingTheTokenIsMasked() {
		try (LocalWykopServer server = new LocalWykopServer(request -> request.path().endsWith("/auth")
				? Reply.ok(TOKEN_1) : new Reply(403, "{\"error\":{\"code\":403,\"message\":\"token token-1 lacks scope\"}}"))) {
			assertThatExceptionOfType(WykopApiException.class)
				.isThrownBy(() -> client(server).get("search/entries", Map.of()))
				.satisfies(ex -> assertThat(ex.getMessage()).contains("HTTP 403", "token <hidden> lacks scope")
					.doesNotContain("token-1"));
			assertThat(searches(server)).hasSize(1);
		}
	}

	@Test
	void aLongErrorBodyIsCappedAfterMasking() {
		// "Wykop auth answered HTTP 500: " (30) + "{\"error\":\"" (10) + 455 = the secret starts at 495,
		// across the 500-character cut (the " after 3 attempts" at the end is cut off too).
		String body = "{\"error\":\"" + "x".repeat(455) + SECRET + "y".repeat(600) + "\"}";
		try (LocalWykopServer server = new LocalWykopServer(request -> new Reply(500, body))) {
			assertThatExceptionOfType(WykopApiException.class)
				.isThrownBy(() -> client(server).get("search/entries", Map.of()))
				.satisfies(ex -> {
					assertThat(ex.getMessage()).hasSize(501).endsWith("…").doesNotContain(SECRET);
					// Masked before the cap: no prefix of the secret survives at the cut.
					assertThat(ex.getMessage()).doesNotContain(SECRET.substring(0, 4));
				});
			// The 500 is retried: three auth attempts.
			assertThat(server.requests()).extracting(Request::path).containsExactly("/api/v3/auth", "/api/v3/auth",
					"/api/v3/auth");
		}
	}

	@Test
	void anInvalidAuthAnswerQuotingTheSecretLeavesItOutOfTheWholeStackTrace() {
		// Jackson quotes an unrecognized token in its own message, up to the first character that
		// cannot be part of an identifier — so this secret has none.
		String secret = "SyntheticSecret123";
		try (LocalWykopServer server = new LocalWykopServer(request -> Reply.ok("{\"data\": " + secret + "}"))) {
			assertThatExceptionOfType(WykopApiException.class)
				.isThrownBy(() -> new WykopApiClient(server.baseUrl(), KEY, secret, Duration.ofSeconds(5))
					.get("search/entries", Map.of()))
				.satisfies(ex -> {
					assertThat(ex.failure()).isEqualTo(WykopFailure.MALFORMED_ANSWER);
					assertThat(stackTrace(ex)).contains("auth", "invalid JSON").doesNotContain(secret);
				});
		}
	}

	@Test
	void anInvalidAnswerQuotingTheTokenLeavesItOutOfTheWholeStackTrace() {
		try (LocalWykopServer server = new LocalWykopServer(request -> request.path().endsWith("/auth")
				? Reply.ok("{\"data\":{\"token\":\"SyntheticToken123\"}}") : Reply.ok("{\"data\": SyntheticToken123}"))) {
			assertThatExceptionOfType(WykopApiException.class)
				.isThrownBy(() -> client(server).get("search/entries", Map.of()))
				.satisfies(ex -> assertThat(stackTrace(ex)).contains("invalid JSON").doesNotContain("SyntheticToken123"));
		}
	}

	@Test
	void anAnswerQuotingTheTokenItsRequestUsedIsMaskedAfterAnotherThreadRefreshedIt() {
		// The answer to "lagged" (sent with token-1) comes only after another call got 401 for
		// token-1 and replaced it with token-2; it then quotes token-1.
		AtomicInteger auths = new AtomicInteger();
		AtomicReference<WykopApiClient> client = new AtomicReference<>();
		try (LocalWykopServer server = new LocalWykopServer(request -> {
			if (request.path().endsWith("/auth")) {
				return Reply.ok("{\"data\":{\"token\":\"token-" + auths.incrementAndGet() + "\"}}");
			}
			boolean oldToken = "Bearer token-1".equals(request.authorization());
			if (request.path().endsWith("/lagged")) {
				client.get().get("other", Map.of());
				return new Reply(403, "{\"error\":{\"code\":403,\"message\":\"token-1 rejected\"}}");
			}
			return oldToken ? new Reply(401, "{\"error\":{\"code\":401}}") : Reply.ok("{\"data\":[]}");
		})) {
			client.set(client(server));

			assertThatExceptionOfType(WykopApiException.class).isThrownBy(() -> client.get().get("lagged", Map.of()))
				.satisfies(ex -> {
					assertThat(ex.status()).isEqualTo(403);
					assertThat(ex.getMessage()).contains("<hidden> rejected").doesNotContain("token-1");
				});
			assertThat(auths).hasValue(2);
		}
	}

	@Test
	void aServerErrorIsRetriedAndASuccessEndsTheCall() {
		AtomicInteger gets = new AtomicInteger();
		try (LocalWykopServer server = new LocalWykopServer(request -> request.path().endsWith("/auth")
				? Reply.ok(TOKEN_1)
				: gets.incrementAndGet() == 1 ? new Reply(503, UNAVAILABLE) : Reply.ok("{\"data\":[]}"))) {
			JsonNode answer = client(server).get("search/entries", Map.of());

			assertThat(answer.path("data").isArray()).isTrue();
			assertThat(searches(server)).hasSize(2);
			assertThat(waits).containsExactly(Duration.ofSeconds(2));
		}
	}

	@Test
	void aServerErrorThatStaysFailsAfterThreeAttempts() {
		try (LocalWykopServer server = new LocalWykopServer(request -> request.path().endsWith("/auth")
				? Reply.ok(TOKEN_1) : new Reply(500, "{\"error\":{\"code\":500}}"))) {
			assertThatExceptionOfType(WykopApiException.class)
				.isThrownBy(() -> client(server).get("search/entries", Map.of()))
				.satisfies(ex -> {
					assertThat(ex.failure()).isEqualTo(WykopFailure.HTTP_STATUS);
					assertThat(ex.status()).isEqualTo(500);
					assertThat(ex.getMessage()).contains("GET search/entries answered HTTP 500")
						.endsWith("after 3 attempts");
				});
			assertThat(searches(server)).hasSize(3);
			assertThat(waits).containsExactly(Duration.ofSeconds(2), Duration.ofSeconds(4));
		}
	}

	@Test
	void aRateLimitWaitsRetryAfterInSeconds() {
		assertThat(waitsAfterRateLimit(Map.of("Retry-After", "7"))).containsExactly(Duration.ofSeconds(7));
	}

	@Test
	void aRateLimitWaitsRetryAfterAsAnHttpDate() {
		String inTenSeconds = DateTimeFormatter.RFC_1123_DATE_TIME
			.format(now.get().plusSeconds(10).atZone(ZoneOffset.UTC));

		assertThat(waitsAfterRateLimit(Map.of("Retry-After", inTenSeconds))).containsExactly(Duration.ofSeconds(10));
	}

	@Test
	void aLongRetryAfterIsCappedAt30Seconds() {
		assertThat(waitsAfterRateLimit(Map.of("Retry-After", "120"))).containsExactly(Duration.ofSeconds(30));
	}

	@Test
	void anUnreadableRetryAfterWaitsTheBackoff() {
		assertThat(waitsAfterRateLimit(Map.of("Retry-After", "soon"))).containsExactly(Duration.ofSeconds(2));
	}

	@Test
	void aBadRequestAndANotFoundAreNotRetried() {
		for (int status : new int[] { 400, 404 }) {
			waits.clear();
			try (LocalWykopServer server = new LocalWykopServer(request -> request.path().endsWith("/auth")
					? Reply.ok(TOKEN_1) : new Reply(status, "{\"error\":{\"code\":" + status + "}}"))) {
				assertThatExceptionOfType(WykopApiException.class)
					.isThrownBy(() -> client(server).get("search/entries", Map.of()))
					.satisfies(ex -> assertThat(ex.status()).isEqualTo(status));
				assertThat(searches(server)).hasSize(1);
				assertThat(waits).isEmpty();
			}
		}
	}

	@Test
	void aFailedAuthIsRetriedToo() {
		AtomicInteger auths = new AtomicInteger();
		try (LocalWykopServer server = new LocalWykopServer(request -> {
			if (request.path().endsWith("/auth")) {
				return auths.incrementAndGet() == 1 ? new Reply(503, UNAVAILABLE) : Reply.ok(TOKEN_1);
			}
			return Reply.ok("{\"data\":[]}");
		})) {
			client(server).get("search/entries", Map.of());

			assertThat(server.requests()).extracting(Request::path)
				.containsExactly("/api/v3/auth", "/api/v3/auth", "/api/v3/search/entries");
			assertThat(server.requests().getLast().authorization()).isEqualTo("Bearer token-1");
			assertThat(waits).containsExactly(Duration.ofSeconds(2));
		}
	}

	@Test
	void aRetryThatWouldEndAfterTheDeadlineIsNotStarted() {
		try (LocalWykopServer server = new LocalWykopServer(request -> request.path().endsWith("/auth")
				? Reply.ok(TOKEN_1) : new Reply(503, UNAVAILABLE))) {
			WykopApiClient client = client(server);
			// The first attempt fits (5 s time-out), the 2 s wait and the next attempt's 5 s do not.
			Instant deadline = client.deadline(Duration.ofSeconds(6));

			assertThatExceptionOfType(WykopApiException.class)
				.isThrownBy(() -> client.get("search/entries", Map.of(), deadline))
				.satisfies(ex -> {
					assertThat(ex.failure()).isEqualTo(WykopFailure.DEADLINE_EXCEEDED);
					assertThat(ex.getMessage()).contains("deadline", "HTTP 503");
				});
			assertThat(searches(server)).hasSize(1);
			assertThat(waits).isEmpty();
		}
	}

	@Test
	void aDeadlineAfterAnAnswerQuotingTheSecretsLeavesThemOutOfTheError(CapturedOutput output) {
		String body = "{\"error\":{\"code\":503,\"message\":\"key " + KEY + ", secret " + SECRET
				+ ", token token-1\"}}";
		try (LocalWykopServer server = new LocalWykopServer(request -> request.path().endsWith("/auth")
				? Reply.ok(TOKEN_1) : new Reply(503, body))) {
			WykopApiClient client = client(server);
			// As above: the deadline ends the call after the first attempt, with that attempt's answer.
			Instant deadline = client.deadline(Duration.ofSeconds(6));

			assertThatExceptionOfType(WykopApiException.class)
				.isThrownBy(() -> client.get("search/entries", Map.of(), deadline))
				.satisfies(ex -> {
					assertThat(ex.failure()).isEqualTo(WykopFailure.DEADLINE_EXCEEDED);
					assertThat(stackTrace(ex)).contains("deadline", "key <hidden>")
						.doesNotContain(KEY, SECRET, "token-1");
				});
		}
		assertThat(output).doesNotContain(KEY, SECRET, "token-1");
	}

	@Test
	void anInterruptDuringARequestStopsTheCallWithoutTheSecrets() {
		try (LocalWykopServer server = new LocalWykopServer(request -> Reply.ok(TOKEN_1))) {
			WykopApiClient client = client(server);

			Thread.currentThread().interrupt();
			try {
				assertThatExceptionOfType(WykopApiException.class)
					.isThrownBy(() -> client.get("search/entries", Map.of()))
					.satisfies(ex -> {
						assertThat(ex.failure()).isEqualTo(WykopFailure.INTERRUPTED);
						assertThat(stackTrace(ex)).contains("interrupted during Wykop").doesNotContain(KEY, SECRET);
					});
				assertThat(Thread.currentThread().isInterrupted()).isTrue();
			}
			finally {
				Thread.interrupted();
			}
		}
	}

	@Test
	void aSingleCallFitsTheClientsOwnTimeout() {
		try (LocalWykopServer server = new LocalWykopServer(request -> request.path().endsWith("/auth")
				? Reply.ok(TOKEN_1) : Reply.ok("{\"data\":[]}"))) {
			// A time-out longer than the default's whole budget: the call's deadline follows this client.
			WykopApiClient client = new WykopApiClient(server.baseUrl(), KEY, SECRET, Duration.ofSeconds(200),
					waits::add, now::get);

			assertThat(client.get("search/entries", Map.of()).path("data").isArray()).isTrue();
			assertThat(searches(server)).hasSize(1);
		}
	}

	@Test
	void aPassedDeadlineSendsNoRequest() {
		try (LocalWykopServer server = new LocalWykopServer(request -> Reply.ok(TOKEN_1))) {
			WykopApiClient client = client(server);
			Instant deadline = client.deadline(Duration.ofSeconds(-1));

			assertThatExceptionOfType(WykopApiException.class)
				.isThrownBy(() -> client.get("search/entries", Map.of(), deadline))
				.satisfies(ex -> {
					assertThat(ex.failure()).isEqualTo(WykopFailure.DEADLINE_EXCEEDED);
					assertThat(ex.getMessage()).contains("before the first attempt");
				});
			assertThat(server.requests()).isEmpty();
		}
	}

	@Test
	void anInterruptedWaitStopsTheCallAndKeepsTheInterruptFlag() {
		try (LocalWykopServer server = new LocalWykopServer(request -> request.path().endsWith("/auth")
				? Reply.ok(TOKEN_1) : new Reply(503, UNAVAILABLE))) {
			WykopApiClient client = new WykopApiClient(server.baseUrl(), KEY, SECRET, TIMEOUT, wait -> {
				throw new InterruptedException("stopping");
			}, now::get);

			try {
				assertThatExceptionOfType(WykopApiException.class)
					.isThrownBy(() -> client.get("search/entries", Map.of()))
					.satisfies(ex -> assertThat(ex.failure()).isEqualTo(WykopFailure.INTERRUPTED));
				assertThat(Thread.currentThread().isInterrupted()).isTrue();
			}
			finally {
				Thread.interrupted();
			}
			assertThat(searches(server)).hasSize(1);
		}
	}

	@Test
	void retriesQuotingTheSecretsLeaveThemOutOfTheErrorAndTheWarnings(CapturedOutput output) {
		String body = "{\"error\":{\"code\":503,\"message\":\"key " + KEY + ", secret " + SECRET
				+ ", token token-1\"}}";
		try (LocalWykopServer server = new LocalWykopServer(request -> request.path().endsWith("/auth")
				? Reply.ok(TOKEN_1) : new Reply(503, body))) {
			assertThatExceptionOfType(WykopApiException.class)
				.isThrownBy(() -> client(server).get("search/entries", Map.of()))
				.satisfies(ex -> assertThat(stackTrace(ex)).contains("after 3 attempts", "key <hidden>")
					.doesNotContain(KEY, SECRET, "token-1"));
			assertThat(searches(server)).hasSize(3);
		}
		assertThat(output).contains("WARN", "Retrying Wykop GET search/entries", "attempt 1 of 3", "attempt 2 of 3")
			.doesNotContain(KEY, SECRET, "token-1");
	}

	@Test
	void invalidJsonIsAMalformedAnswer() {
		try (LocalWykopServer server = new LocalWykopServer(request -> request.path().endsWith("/auth")
				? Reply.ok(TOKEN_1) : Reply.ok("<html>maintenance</html>"))) {
			assertThatExceptionOfType(WykopApiException.class)
				.isThrownBy(() -> client(server).get("search/entries", Map.of()))
				.satisfies(ex -> {
					assertThat(ex.failure()).isEqualTo(WykopFailure.MALFORMED_ANSWER);
					assertThat(ex.getMessage()).contains("invalid JSON");
				});
		}
	}

	@Test
	void aConnectionErrorIsATransportFailureAfterThreeAttempts() {
		LocalWykopServer server = new LocalWykopServer(request -> Reply.ok(TOKEN_1));
		WykopApiClient client = client(server);
		server.close();

		assertThatExceptionOfType(WykopApiException.class).isThrownBy(() -> client.get("search/entries", Map.of()))
			.satisfies(ex -> {
				assertThat(ex.failure()).isEqualTo(WykopFailure.TRANSPORT);
				assertThat(ex.getMessage()).contains("auth").endsWith("after 3 attempts");
			});
		// Three attempts of auth: two waits between them (the closed server records nothing).
		assertThat(waits).containsExactly(Duration.ofSeconds(2), Duration.ofSeconds(4));
	}

	/** The waits of a search answered 429 with {@code headers}, then 200; two searches. */
	private List<Duration> waitsAfterRateLimit(Map<String, String> headers) {
		AtomicInteger gets = new AtomicInteger();
		try (LocalWykopServer server = new LocalWykopServer(request -> request.path().endsWith("/auth")
				? Reply.ok(TOKEN_1)
				: gets.incrementAndGet() == 1 ? new Reply(429, "{\"error\":{\"code\":429}}", headers)
						: Reply.ok("{\"data\":[]}"))) {
			client(server).get("search/entries", Map.of());

			assertThat(searches(server)).hasSize(2);
			return List.copyOf(waits);
		}
	}

	private static List<Request> searches(LocalWykopServer server) {
		return server.requests().stream().filter(request -> request.path().contains("/search/")).toList();
	}

	private static String stackTrace(Throwable error) {
		StringWriter trace = new StringWriter();
		error.printStackTrace(new PrintWriter(trace));
		return trace.toString();
	}

	/** A client whose waits are recorded and move the fake clock instead of sleeping. */
	private WykopApiClient client(LocalWykopServer server) {
		return new WykopApiClient(server.baseUrl(), KEY, SECRET, TIMEOUT, wait -> {
			waits.add(wait);
			now.updateAndGet(instant -> instant.plus(wait));
		}, now::get);
	}

}
