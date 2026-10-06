package pl.szczurradar.api.sources.wykop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import pl.szczurradar.api.sources.wykop.LocalWykopServer.Reply;
import pl.szczurradar.api.sources.wykop.LocalWykopServer.Request;

/**
 * {@link WykopApiClient} against a local server. The auth answer and error bodies are short
 * synthetic JSON in the API's shape ({@code {"data": {"token": …}}}); a real one would carry a live
 * token.
 */
class WykopApiClientTest {

	private static final String KEY = "w5-test-key";

	private static final String SECRET = "test-secret-value";

	private static final String TOKEN_1 = "{\"data\":{\"token\":\"token-1\"}}";

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
			assertThat(client(server).toString()).doesNotContain(SECRET, KEY);
		}
	}

	@Test
	void anErrorAnswerIsAnExceptionAfterOneRequestWithoutRetries() {
		try (LocalWykopServer server = new LocalWykopServer(request -> request.path().endsWith("/auth")
				? Reply.ok(TOKEN_1) : new Reply(500, "{\"error\":{\"code\":500}}"))) {
			assertThatExceptionOfType(WykopApiException.class)
				.isThrownBy(() -> client(server).get("search/entries", Map.of()))
				.satisfies(ex -> assertThat(ex.status()).isEqualTo(500));
			assertThat(server.requests()).hasSize(2);
		}
	}

	@Test
	void invalidJsonIsAMalformedAnswer() {
		try (LocalWykopServer server = new LocalWykopServer(request -> request.path().endsWith("/auth")
				? Reply.ok(TOKEN_1) : Reply.ok("<html>maintenance</html>"))) {
			assertThatExceptionOfType(WykopApiException.class)
				.isThrownBy(() -> client(server).get("search/entries", Map.of()))
				.satisfies(ex -> {
					assertThat(ex.failure()).isEqualTo(WykopFailure.MALFORMED_ANSWER);
					assertThat(ex.getCause()).isNotNull();
				});
		}
	}

	@Test
	void aConnectionErrorIsATransportFailure() {
		LocalWykopServer server = new LocalWykopServer(request -> Reply.ok(TOKEN_1));
		WykopApiClient client = client(server);
		server.close();

		assertThatExceptionOfType(WykopApiException.class).isThrownBy(() -> client.get("search/entries", Map.of()))
			.satisfies(ex -> assertThat(ex.failure()).isEqualTo(WykopFailure.TRANSPORT));
	}

	private static WykopApiClient client(LocalWykopServer server) {
		return new WykopApiClient(server.baseUrl(), KEY, SECRET, Duration.ofSeconds(5));
	}

}
