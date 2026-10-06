package pl.szczurradar.api.sources.wykop;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Transport to Wykop API v3 (https://doc.wykop.pl/): an app token from {@code POST auth} with the
 * app key and secret, then authorized {@code GET}s returning the JSON answer. The token is kept and
 * fetched again once when a call answers 401. No retries otherwise — the caller decides.
 *
 * <p>The key, secret and token are secrets: the key and secret are only sent to {@code auth}, and
 * none of them appears in an exception message or {@link #toString()} — every message passes
 * through {@link #clean} (each secret → {@code <hidden>}, at most {@value #MAX_MESSAGE} characters),
 * also when Wykop quotes them in an error body. One instance per run; it is thread-safe.
 */
public class WykopApiClient {

	public static final URI DEFAULT_BASE_URL = URI.create("https://wykop.pl/api/v3/");

	public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

	private static final int MAX_MESSAGE = 500;

	private final URI baseUrl;

	private final String key;

	private final String secret;

	private final Duration timeout;

	private final HttpClient http;

	private final JsonMapper json = JsonMapper.builder().build();

	private volatile @Nullable String token;

	public WykopApiClient(String key, String secret) {
		this(DEFAULT_BASE_URL, key, secret, DEFAULT_TIMEOUT);
	}

	/** @param baseUrl the API root ending with {@code /}; a local server in tests */
	public WykopApiClient(URI baseUrl, String key, String secret, Duration timeout) {
		this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
		this.key = requireText(key, "key");
		this.secret = requireText(secret, "secret");
		this.timeout = Objects.requireNonNull(timeout, "timeout");
		this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
	}

	/**
	 * {@code GET <path>?<query>} with the app token.
	 *
	 * @param query parameters in order; a list value is sent as repeated {@code name[]=value}
	 * @throws WykopApiException on a transport error or a non-2xx answer
	 */
	public JsonNode get(String path, Map<String, ?> query) {
		URI uri = baseUrl.resolve(path + queryString(query));
		String used = currentToken();
		HttpResponse<String> response = send(authorized(uri, used));
		if (response.statusCode() == 401) {
			response = send(authorized(uri, refreshToken(used)));
		}
		return parse(requireSuccess(response, "GET " + path), "GET " + path);
	}

	@Override
	public String toString() {
		return "WykopApiClient[baseUrl=" + baseUrl + ", key=<hidden>, secret=<hidden>]";
	}

	private String currentToken() {
		String current = token;
		return current != null ? current : refreshToken(null);
	}

	/**
	 * A fresh token, unless another thread already replaced {@code stale} — then that one is reused,
	 * so concurrent 401s cause one auth call.
	 */
	private synchronized String refreshToken(@Nullable String stale) {
		String current = token;
		if (current != null && !current.equals(stale)) {
			return current;
		}
		ObjectNode credentials = json.createObjectNode().put("key", key).put("secret", secret);
		String body = json.writeValueAsString(json.createObjectNode().set("data", credentials));
		HttpRequest request = HttpRequest.newBuilder(baseUrl.resolve("auth"))
			.timeout(timeout)
			.header("Accept", "application/json")
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(body))
			.build();
		JsonNode answer = parse(requireSuccess(send(request), "auth"), "auth");
		String fresh = answer.path("data").path("token").asString("");
		if (fresh.isEmpty()) {
			throw new WykopApiException(WykopFailure.MALFORMED_ANSWER, "Wykop auth answered without data.token");
		}
		token = fresh;
		return fresh;
	}

	private HttpRequest authorized(URI uri, String bearer) {
		return HttpRequest.newBuilder(uri)
			.timeout(timeout)
			.header("Accept", "application/json")
			.header("Authorization", "Bearer " + bearer)
			.GET()
			.build();
	}

	private HttpResponse<String> send(HttpRequest request) {
		try {
			return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		}
		catch (IOException ex) {
			throw new WykopApiException(WykopFailure.TRANSPORT,
					clean("Wykop " + request.method() + " " + request.uri().getPath() + " failed: " + ex), ex);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new WykopApiException(WykopFailure.INTERRUPTED, "interrupted during Wykop " + request.uri().getPath(), ex);
		}
	}

	private String requireSuccess(HttpResponse<String> response, String call) {
		int status = response.statusCode();
		if (status < 200 || status > 299) {
			String body = response.body() == null ? "" : response.body();
			throw WykopApiException.httpStatus(status, clean("Wykop " + call + " answered HTTP " + status + ": " + body));
		}
		return response.body();
	}

	private JsonNode parse(String body, String call) {
		try {
			return json.readTree(body);
		}
		catch (JacksonException ex) {
			throw new WykopApiException(WykopFailure.MALFORMED_ANSWER,
					clean("Wykop " + call + " answered with invalid JSON: " + ex.getOriginalMessage()), ex);
		}
	}

	/**
	 * {@code text} with the key, the secret and the current token replaced by {@code <hidden>} (an
	 * error body may quote them), capped at {@value #MAX_MESSAGE} characters after masking.
	 */
	private String clean(String text) {
		String cleaned = text.replace(key, "<hidden>").replace(secret, "<hidden>");
		String current = token;
		if (current != null) {
			cleaned = cleaned.replace(current, "<hidden>");
		}
		return cleaned.length() > MAX_MESSAGE ? cleaned.substring(0, MAX_MESSAGE) + "…" : cleaned;
	}

	private static String queryString(Map<String, ?> query) {
		if (query.isEmpty()) {
			return "";
		}
		return query.entrySet().stream().flatMap(entry -> {
			if (entry.getValue() instanceof List<?> values) {
				return values.stream().map(value -> encode(entry.getKey() + "[]") + "=" + encode(String.valueOf(value)));
			}
			return Stream.of(encode(entry.getKey()) + "=" + encode(String.valueOf(entry.getValue())));
		}).collect(Collectors.joining("&", "?", ""));
	}

	private static String encode(String text) {
		return URLEncoder.encode(text, StandardCharsets.UTF_8);
	}

	private static String requireText(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("Wykop " + name + " is not set");
		}
		return value;
	}

}
