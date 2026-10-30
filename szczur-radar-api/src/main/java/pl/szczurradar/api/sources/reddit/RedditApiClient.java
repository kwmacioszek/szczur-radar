package pl.szczurradar.api.sources.reddit;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import pl.szczurradar.api.sources.transport.HttpRetry;
import pl.szczurradar.api.sources.transport.HttpRetryException;

/**
 * Transport to Arctic Shift (https://github.com/ArthurHeitmann/arctic_shift/blob/master/api/README.md),
 * the public archive of Reddit this source reads: unauthenticated {@code GET}s whose answer is an envelope
 * {@code {"data": [...]}}, or {@code {"data": null, "error": "..."}} on a failure. One request → the
 * {@code data} list of a 2xx answer.
 *
 * <p>Retries: every request goes through the sources' shared loop {@link HttpRetry}: up to
 * {@value HttpRetry#MAX_ATTEMPTS} attempts on HTTP 429, HTTP 500–599, HTTP 422 (Arctic Shift's
 * "Timeout. Maybe slow down a bit", which asks for a short wait) and transport errors (connection errors
 * and request time-outs), waiting {@code Retry-After} or else {@code X-RateLimit-Reset} (seconds, capped at
 * {@link HttpRetry#MAX_RETRY_AFTER}) or, without a readable one, 2 s and then 4 s. Other 4xx answers,
 * malformed answers and interruptions are never retried. Every retry is one WARN line; the last failure is
 * thrown without a log of its own (the caller logs it). Nothing is retried past the caller's deadline: an
 * attempt starts only if its time-out fits before the deadline, a wait only if the wait and the next
 * attempt's time-out fit, otherwise the call fails with {@link RedditFailure#DEADLINE_EXCEEDED}.
 *
 * <p>Arctic Shift has no key: no {@code Authorization} is sent and a message has no secret to hide; every
 * message is capped at {@value #MAX_MESSAGE} characters. One instance per run; it is thread-safe.
 */
public class RedditApiClient {

	public static final URI DEFAULT_BASE_URL = URI.create("https://arctic-shift.photon-reddit.com/");

	public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

	/** Who asks: the project and where to reach it, as the archive's maintainer may want to know. */
	static final String USER_AGENT = "szczur-radar (+https://github.com/kwmacioszek/szczur-radar)";

	/** Arctic Shift's "Timeout. Maybe slow down a bit": retried after a short wait. */
	private static final int SLOW_DOWN = 422;

	private static final int MAX_MESSAGE = 500;

	private final URI baseUrl;

	private final Duration timeout;

	private final HttpRetry retry;

	private final InstantSource clock;

	private final HttpClient http;

	private final JsonMapper json = JsonMapper.builder().build();

	public RedditApiClient() {
		this(DEFAULT_BASE_URL, DEFAULT_TIMEOUT);
	}

	/** @param baseUrl the API root ending with {@code /}; a local server in tests */
	public RedditApiClient(URI baseUrl, Duration timeout) {
		this(baseUrl, timeout, HttpRetry.Sleeper.REAL, InstantSource.system());
	}

	/**
	 * @param baseUrl the API root ending with {@code /}; a local server in tests
	 * @param sleeper waits between attempts
	 * @param clock the clock of deadlines and of {@code Retry-After} dates
	 */
	public RedditApiClient(URI baseUrl, Duration timeout, HttpRetry.Sleeper sleeper, InstantSource clock) {
		this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
		this.timeout = Objects.requireNonNull(timeout, "timeout");
		this.clock = Objects.requireNonNull(clock, "clock");
		this.retry = new HttpRetry("Reddit", status -> HttpRetry.transientStatus(status) || status == SLOW_DOWN,
				List.of("Retry-After", "X-RateLimit-Reset"), sleeper, clock);
		this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
	}

	/** {@code budget} from now on this client's clock: a deadline for {@link #get}. */
	public Instant deadline(Duration budget) {
		return clock.instant().plus(budget);
	}

	/**
	 * {@code GET <path>?<query>}, retried as described on the class, never past {@code deadline}.
	 *
	 * @param query parameters in order
	 * @param deadline nothing is attempted or waited for that would end after it
	 * @return the answer's {@code data} list
	 * @throws RedditApiException on a transport error or a non-2xx answer (after the attempts for a retried
	 *     one), on a 2xx answer without a {@code data} list ({@link RedditFailure#MALFORMED_ANSWER}), and
	 *     with {@link RedditFailure#DEADLINE_EXCEEDED} when the deadline comes first
	 */
	public JsonNode get(String path, Map<String, ?> query, Instant deadline) {
		Objects.requireNonNull(deadline, "deadline");
		String call = "GET " + path;
		HttpRequest request = HttpRequest.newBuilder(baseUrl.resolve(path + queryString(query)))
			.timeout(timeout)
			.header("Accept", "application/json")
			.header("User-Agent", USER_AGENT)
			.GET()
			.build();
		return data(parse(requireSuccess(exchange(request, call, deadline), call), call), call);
	}

	@Override
	public String toString() {
		return "RedditApiClient[baseUrl=" + baseUrl + "]";
	}

	/**
	 * Sends {@code request} through {@link HttpRetry} until it gets an answer that is not retried (2xx or a
	 * 4xx other than 422), which is returned.
	 *
	 * @throws RedditApiException when the attempts are used up (the last failure's kind), the deadline comes
	 *     first ({@link RedditFailure#DEADLINE_EXCEEDED}) or the thread is interrupted
	 */
	private HttpResponse<String> exchange(HttpRequest request, String call, Instant deadline) {
		try {
			return retry.exchange(http, request, call, timeout, deadline, RedditApiClient::clean);
		}
		catch (HttpRetryException ex) {
			throw switch (ex.kind()) {
				case HTTP_STATUS -> RedditApiException.httpStatus(ex.status(), ex.getMessage());
				case TRANSPORT -> new RedditApiException(RedditFailure.TRANSPORT, ex.getMessage());
				case DEADLINE_EXCEEDED -> new RedditApiException(RedditFailure.DEADLINE_EXCEEDED, ex.getMessage());
				case INTERRUPTED -> new RedditApiException(RedditFailure.INTERRUPTED, ex.getMessage(), ex.getCause());
			};
		}
	}

	private static String requireSuccess(HttpResponse<String> response, String call) {
		int status = response.statusCode();
		if (status < 200 || status > 299) {
			String body = response.body() == null ? "" : response.body();
			throw RedditApiException.httpStatus(status,
					clean("Reddit " + call + " answered HTTP " + status + ": " + body));
		}
		return response.body();
	}

	private JsonNode parse(String body, String call) {
		try {
			return json.readTree(body);
		}
		catch (JacksonException ex) {
			throw new RedditApiException(RedditFailure.MALFORMED_ANSWER,
					clean("Reddit " + call + " answered with invalid JSON: " + ex.getOriginalMessage()));
		}
	}

	/** The envelope's {@code data} list; an {@code error} in a 2xx answer is a failure too. */
	private static JsonNode data(JsonNode answer, String call) {
		JsonNode error = answer.path("error");
		if (!error.isMissingNode() && !error.isNull()) {
			throw new RedditApiException(RedditFailure.MALFORMED_ANSWER, clean("Reddit " + call + " answered an error: "
					+ (error.isString() ? error.stringValue() : error.toString())));
		}
		JsonNode data = answer.path("data");
		if (!data.isArray()) {
			throw new RedditApiException(RedditFailure.MALFORMED_ANSWER,
					"Reddit " + call + " answered without a data list");
		}
		return data;
	}

	/** {@code text} capped at {@value #MAX_MESSAGE} characters (an error body may be a whole page). */
	private static String clean(String text) {
		return text.length() > MAX_MESSAGE ? text.substring(0, MAX_MESSAGE) + "…" : text;
	}

	private static String queryString(Map<String, ?> query) {
		if (query.isEmpty()) {
			return "";
		}
		return query.entrySet()
			.stream()
			.map(entry -> encode(entry.getKey()) + "=" + encode(String.valueOf(entry.getValue())))
			.collect(Collectors.joining("&", "?", ""));
	}

	private static String encode(String text) {
		return URLEncoder.encode(text, StandardCharsets.UTF_8);
	}

}
