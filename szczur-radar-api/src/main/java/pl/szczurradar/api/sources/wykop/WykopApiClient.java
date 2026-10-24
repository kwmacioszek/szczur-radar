package pl.szczurradar.api.sources.wykop;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Transport to Wykop API v3 (https://doc.wykop.pl/): an app token from {@code POST auth} with the
 * app key and secret, then authorized {@code GET}s returning the JSON answer. The token is kept and
 * fetched again once when a call answers 401.
 *
 * <p>Retries: every HTTP exchange ({@code POST auth}, a {@code GET}, the {@code GET} after a token
 * refresh) gets up to {@value #MAX_ATTEMPTS} attempts on HTTP 429, HTTP 500–599 and transport errors
 * (connection errors and request time-outs), waiting {@code Retry-After} (seconds or an HTTP date,
 * capped at {@link #MAX_RETRY_AFTER}) or, without a readable one, 2 s and then 4 s. Other 4xx answers,
 * malformed answers and interruptions are never retried. Every retry is one WARN line; the last failure
 * is thrown without a log of its own (the caller logs it). Nothing is retried past the caller's
 * deadline: an attempt starts only if its time-out fits before the deadline, a wait only if the wait and
 * the next attempt's time-out fit, otherwise the call fails with {@link WykopFailure#DEADLINE_EXCEEDED}.
 *
 * <p>The key, secret and token are secrets: the key and secret are only sent to {@code auth}, and
 * none of them appears in an exception message, a log line or {@link #toString()} — every message
 * passes through {@link #clean} (each secret → {@code <hidden>}, at most {@value #MAX_MESSAGE}
 * characters), also when Wykop quotes them in an error body, and including the token a request used
 * when another thread has replaced it since. The original transport or parse exception is never
 * attached as the cause: its message (e.g. Jackson quoting the answer) would bypass the masking in a
 * stack trace. One instance per run; it is thread-safe.
 */
public class WykopApiClient {

	public static final URI DEFAULT_BASE_URL = URI.create("https://wykop.pl/api/v3/");

	public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

	/** Attempts of one HTTP exchange. */
	static final int MAX_ATTEMPTS = 3;

	/** The longest wait a {@code Retry-After} can ask for. */
	static final Duration MAX_RETRY_AFTER = Duration.ofSeconds(30);

	/** The wait after the first failed attempt without a readable {@code Retry-After}; doubled after each one. */
	static final Duration FIRST_BACKOFF = Duration.ofSeconds(2);

	private static final int MAX_MESSAGE = 500;

	private static final Logger log = LoggerFactory.getLogger(WykopApiClient.class);

	/** Waits between attempts; replaced in tests. */
	@FunctionalInterface
	public interface Sleeper {

		void sleep(Duration duration) throws InterruptedException;

		Sleeper REAL = Thread::sleep;

	}

	/**
	 * A failed attempt that may be retried; {@code description} is not yet cleaned and never leaves this
	 * class unless through {@link #clean}.
	 */
	private record Failure(WykopFailure kind, int status, String description, @Nullable Duration retryAfter) {
	}

	private final URI baseUrl;

	private final String key;

	private final String secret;

	private final Duration timeout;

	private final Sleeper sleeper;

	private final InstantSource clock;

	private final HttpClient http;

	private final JsonMapper json = JsonMapper.builder().build();

	private volatile @Nullable String token;

	public WykopApiClient(String key, String secret) {
		this(DEFAULT_BASE_URL, key, secret, DEFAULT_TIMEOUT);
	}

	/** @param baseUrl the API root ending with {@code /}; a local server in tests */
	public WykopApiClient(URI baseUrl, String key, String secret, Duration timeout) {
		this(baseUrl, key, secret, timeout, Sleeper.REAL, InstantSource.system());
	}

	/**
	 * @param baseUrl the API root ending with {@code /}; a local server in tests
	 * @param sleeper waits between attempts
	 * @param clock the clock of deadlines and of {@code Retry-After} dates
	 */
	public WykopApiClient(URI baseUrl, String key, String secret, Duration timeout, Sleeper sleeper,
			InstantSource clock) {
		this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
		this.key = requireText(key, "key");
		this.secret = requireText(secret, "secret");
		this.timeout = Objects.requireNonNull(timeout, "timeout");
		this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
		this.clock = Objects.requireNonNull(clock, "clock");
		this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
	}

	/** {@code budget} from now on this client's clock: a deadline for {@link #get(String, Map, Instant)}. */
	public Instant deadline(Duration budget) {
		return clock.instant().plus(budget);
	}

	/**
	 * {@link #get(String, Map, Instant)} with a deadline that fits one exchange's attempts and waits —
	 * for a single call outside a repository's fetch.
	 */
	public JsonNode get(String path, Map<String, ?> query) {
		return get(path, query,
				deadline(timeout.multipliedBy(MAX_ATTEMPTS).plus(MAX_RETRY_AFTER.multipliedBy(MAX_ATTEMPTS - 1))));
	}

	/**
	 * {@code GET <path>?<query>} with the app token, retried as described on the class, never past
	 * {@code deadline}.
	 *
	 * @param query parameters in order; a list value is sent as repeated {@code name[]=value}
	 * @param deadline nothing is attempted or waited for that would end after it
	 * @throws WykopApiException on a transport error or a non-2xx answer (after the attempts for a
	 *     retried one), {@link WykopFailure#DEADLINE_EXCEEDED} when the deadline comes first
	 */
	public JsonNode get(String path, Map<String, ?> query, Instant deadline) {
		Objects.requireNonNull(deadline, "deadline");
		URI uri = baseUrl.resolve(path + queryString(query));
		String call = "GET " + path;
		String used = currentToken(deadline);
		HttpResponse<String> response = exchange(authorized(uri, used), call, used, deadline);
		if (response.statusCode() == 401) {
			used = refreshToken(used, deadline);
			response = exchange(authorized(uri, used), call, used, deadline);
		}
		return parse(requireSuccess(response, call, used), call, used);
	}

	@Override
	public String toString() {
		return "WykopApiClient[baseUrl=" + baseUrl + ", key=<hidden>, secret=<hidden>]";
	}

	private String currentToken(Instant deadline) {
		String current = token;
		return current != null ? current : refreshToken(null, deadline);
	}

	/**
	 * A fresh token, unless another thread already replaced {@code stale} — then that one is reused,
	 * so concurrent 401s cause one auth call. Retries of {@code auth} wait under this lock.
	 */
	private synchronized String refreshToken(@Nullable String stale, Instant deadline) {
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
		JsonNode answer = parse(requireSuccess(exchange(request, "auth", null, deadline), "auth", null), "auth", null);
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

	/**
	 * Sends {@code request} until it gets an answer that is not retried (2xx, 401 or another 4xx),
	 * which is returned.
	 *
	 * @param bearer the token the request sends, {@code null} for {@code auth}
	 * @throws WykopApiException when the attempts are used up (the last failure's kind), the deadline
	 *     comes first ({@link WykopFailure#DEADLINE_EXCEEDED}) or the thread is interrupted
	 */
	private HttpResponse<String> exchange(HttpRequest request, String call, @Nullable String bearer,
			Instant deadline) {
		Failure last = null;
		for (int attempt = 1;; attempt++) {
			requireTime(Duration.ZERO, deadline, call, last, attempt - 1, bearer);
			Failure failure;
			try {
				HttpResponse<String> response = http.send(request,
						HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
				if (!retried(response.statusCode())) {
					return response;
				}
				String body = response.body() == null ? "" : response.body();
				failure = new Failure(WykopFailure.HTTP_STATUS, response.statusCode(),
						"Wykop " + call + " answered HTTP " + response.statusCode() + ": " + body,
						retryAfter(response.headers().firstValue("Retry-After").orElse(null)));
			}
			catch (IOException ex) {
				failure = new Failure(WykopFailure.TRANSPORT, 0,
						"Wykop " + request.method() + " " + request.uri().getPath() + " failed: " + ex, null);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				throw new WykopApiException(WykopFailure.INTERRUPTED,
						clean("interrupted during Wykop " + request.uri().getPath(), bearer), ex);
			}
			if (attempt == MAX_ATTEMPTS) {
				String message = clean(failure.description() + " after " + MAX_ATTEMPTS + " attempts", bearer);
				throw failure.kind() == WykopFailure.HTTP_STATUS
						? WykopApiException.httpStatus(failure.status(), message)
						: new WykopApiException(failure.kind(), message);
			}
			Duration wait = wait(failure, attempt);
			requireTime(wait, deadline, call, failure, attempt, bearer);
			log.warn("{}", clean("Retrying Wykop " + call + " in " + format(wait) + " after attempt " + attempt + " of "
					+ MAX_ATTEMPTS + ": " + failure.description(), bearer));
			try {
				sleeper.sleep(wait);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				throw new WykopApiException(WykopFailure.INTERRUPTED,
						clean("interrupted while waiting to retry Wykop " + call, bearer), ex);
			}
			last = failure;
		}
	}

	/** HTTP 429 and 5xx are retried; every other status is returned to the caller. */
	private static boolean retried(int status) {
		return status == 429 || (status >= 500 && status <= 599);
	}

	/**
	 * Fails with {@link WykopFailure#DEADLINE_EXCEEDED} unless {@code wait} and one attempt's time-out
	 * still end by {@code deadline}.
	 *
	 * @param last the failure of attempt {@code attempts}, {@code null} before the first attempt
	 */
	private void requireTime(Duration wait, Instant deadline, String call, @Nullable Failure last, int attempts,
			@Nullable String bearer) {
		if (!clock.instant().plus(wait).plus(timeout).isAfter(deadline)) {
			return;
		}
		String message = last == null ? "Wykop " + call + " reached its deadline before the first attempt"
				: "Wykop " + call + " reached its deadline after " + attempts + " attempt(s): " + last.description();
		throw new WykopApiException(WykopFailure.DEADLINE_EXCEEDED, clean(message, bearer));
	}

	/** The wait after failed attempt {@code attempt}: {@code Retry-After} (capped) or 2 s, 4 s, … */
	private static Duration wait(Failure failure, int attempt) {
		Duration retryAfter = failure.retryAfter();
		if (retryAfter != null) {
			return retryAfter.compareTo(MAX_RETRY_AFTER) > 0 ? MAX_RETRY_AFTER : retryAfter;
		}
		return FIRST_BACKOFF.multipliedBy(1L << (attempt - 1));
	}

	/** {@code Retry-After} in seconds or as an HTTP date (a past one: no wait), else {@code null}. */
	private @Nullable Duration retryAfter(@Nullable String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String text = value.strip();
		try {
			long seconds = Long.parseLong(text);
			return seconds >= 0 ? Duration.ofSeconds(seconds) : null;
		}
		catch (NumberFormatException notSeconds) {
			try {
				Duration until = Duration.between(clock.instant(),
						ZonedDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
				return until.isNegative() ? Duration.ZERO : until;
			}
			catch (DateTimeParseException notDate) {
				return null;
			}
		}
	}

	private static String format(Duration wait) {
		return wait.toMillis() % 1000 == 0 ? wait.toSeconds() + " s" : wait.toMillis() + " ms";
	}

	private String requireSuccess(HttpResponse<String> response, String call, @Nullable String bearer) {
		int status = response.statusCode();
		if (status < 200 || status > 299) {
			String body = response.body() == null ? "" : response.body();
			throw WykopApiException.httpStatus(status,
					clean("Wykop " + call + " answered HTTP " + status + ": " + body, bearer));
		}
		return response.body();
	}

	private JsonNode parse(String body, String call, @Nullable String bearer) {
		try {
			return json.readTree(body);
		}
		catch (JacksonException ex) {
			throw new WykopApiException(WykopFailure.MALFORMED_ANSWER,
					clean("Wykop " + call + " answered with invalid JSON: " + ex.getOriginalMessage(), bearer));
		}
	}

	/**
	 * {@code text} with the key, the secret, the current token and {@code bearer} (the token the
	 * request used, which another thread may already have replaced) replaced by {@code <hidden>} — an
	 * error body may quote them — capped at {@value #MAX_MESSAGE} characters after masking.
	 */
	private String clean(String text, @Nullable String bearer) {
		String cleaned = text;
		// Longest first: a value that contains another one is hidden whole, not cut around the shorter one.
		List<String> hidden = Stream.of(key, secret, token, bearer)
			.filter(value -> value != null && !value.isEmpty())
			.sorted(Comparator.comparingInt(String::length).reversed())
			.toList();
		for (String value : hidden) {
			cleaned = cleaned.replace(value, "<hidden>");
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
