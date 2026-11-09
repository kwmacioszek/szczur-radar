package pl.szczurradar.api.alerts;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link OperatorAlerts} through an ntfy topic (S-34; before it a healthchecks.io check, F-04). One
 * report is one {@code POST} to the topic's address ({@code NTFY_URL}): the headers
 * {@code Title: Szczur Radar <EVENT>}, {@code Priority: <n>} ({@link AlertEvent#priority()}) and
 * {@code Content-Type: text/plain; charset=utf-8}, with {@code Authorization: Bearer <token>} only when
 * {@code NTFY_TOKEN} is set, and the body {@code "<EVENT> (<description>): <detail>"} cut to
 * {@value #MAX_BODY_BYTES} bytes of UTF-8 (ntfy's limit of a message; a longer one becomes an attachment).
 *
 * <p>{@link Delivery#DELIVERED} on a 2xx. One attempt, no retries (they live only in the transport layers
 * {@code llm/transport} and {@code sources/transport}); connect {@link #CONNECT_TIMEOUT}, the request
 * {@link #REQUEST_TIMEOUT}; no executor or timer of its own. Any other answer — 429 (ntfy's limit) and
 * 401/403 (a wrong token) included —, a transport error, a time-out or an interrupt (the flag is
 * restored) is {@link Delivery#FAILED} with one WARN line naming the event and the status or the
 * exception's class — never the address or the token (secrets) and never the exception itself, whose
 * message or stack trace could quote them (as in {@code WykopApiClient}). It never throws. Thread-safe.
 */
public final class NtfyAlerts implements OperatorAlerts {

	public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

	public static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

	/** The longest body sent, in bytes of UTF-8: ntfy's limit of a message. */
	static final int MAX_BODY_BYTES = 4096;

	static final String TITLE_PREFIX = "Szczur Radar ";

	private static final String ELLIPSIS = "…";

	private static final int ELLIPSIS_BYTES = ELLIPSIS.getBytes(StandardCharsets.UTF_8).length;

	private static final Logger log = LoggerFactory.getLogger(NtfyAlerts.class);

	private final URI topicUrl;

	private final @Nullable String token;

	private final Duration requestTimeout;

	private final HttpClient http;

	/** @param settings a configured {@code NTFY_URL}, with or without {@code NTFY_TOKEN} */
	public NtfyAlerts(AlertSettings settings) {
		this(settings.topicUrl(), settings.token(), CONNECT_TIMEOUT, REQUEST_TIMEOUT);
	}

	/**
	 * @param topicUrl the topic's address; a local {@code http://} server in tests
	 * @param token the access token, or {@code null} for no {@code Authorization} header
	 */
	NtfyAlerts(URI topicUrl, @Nullable String token, Duration connectTimeout, Duration requestTimeout) {
		this.topicUrl = AlertSettings.withoutTrailingSlash(Objects.requireNonNull(topicUrl, "topicUrl"));
		this.token = token;
		this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
		this.http = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
	}

	@Override
	public Delivery report(AlertEvent event, @Nullable String detail) {
		try {
			int status = post(event, body(event, detail));
			if (!success(status)) {
				log.warn("Operator alert {} not delivered: ntfy answered HTTP {}", event, status);
				return Delivery.FAILED;
			}
			log.info("Operator alert {} delivered to ntfy", event);
			return Delivery.DELIVERED;
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			log.warn("Operator alert {} not delivered: interrupted", event);
			return Delivery.FAILED;
		}
		catch (IOException | RuntimeException ex) {
			// Only the class: the message (or a stack trace) could quote the address or the token.
			log.warn("Operator alert {} not delivered: {}", event, ex.getClass().getName());
			return Delivery.FAILED;
		}
	}

	/** Never prints the address or the token. */
	@Override
	public String toString() {
		return "NtfyAlerts[<hidden>]";
	}

	/** The message's title: {@value #TITLE_PREFIX} and the event's name, ASCII ({@code send-test-alert} names it). */
	public static String title(AlertEvent event) {
		return TITLE_PREFIX + event.name();
	}

	/**
	 * {@code "<EVENT> (<description>): <detail>"}, at most {@value #MAX_BODY_BYTES} bytes of UTF-8. A longer
	 * text is cut on a code point boundary (a surrogate pair stays whole, as every code point does) so that
	 * it and the {@code …} after it fit the limit.
	 */
	static String body(AlertEvent event, @Nullable String detail) {
		String text = event.name() + " (" + event.description() + "): " + (detail == null ? "" : detail.strip());
		if (text.getBytes(StandardCharsets.UTF_8).length <= MAX_BODY_BYTES) {
			return text;
		}
		int budget = MAX_BODY_BYTES - ELLIPSIS_BYTES;
		int bytes = 0;
		int end = 0;
		while (end < text.length()) {
			int codePoint = text.codePointAt(end);
			int codePointBytes = utf8Length(codePoint);
			if (bytes + codePointBytes > budget) {
				break;
			}
			bytes += codePointBytes;
			end += Character.charCount(codePoint);
		}
		return text.substring(0, end) + ELLIPSIS;
	}

	/**
	 * The bytes of one code point in UTF-8. A lone surrogate counts as 3 (its range), though the encoder
	 * replaces it with one byte: the cut then only comes a little earlier, never past the limit.
	 */
	private static int utf8Length(int codePoint) {
		if (codePoint < 0x80) {
			return 1;
		}
		if (codePoint < 0x800) {
			return 2;
		}
		if (codePoint < 0x10000) {
			return 3;
		}
		return 4;
	}

	private int post(AlertEvent event, String body) throws IOException, InterruptedException {
		HttpRequest.Builder request = HttpRequest.newBuilder(topicUrl)
			.timeout(requestTimeout)
			.header("Title", title(event))
			.header("Priority", Integer.toString(event.priority()))
			.header("Content-Type", "text/plain; charset=utf-8");
		if (token != null) {
			request.header("Authorization", "Bearer " + token);
		}
		return http.send(request.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build(),
				HttpResponse.BodyHandlers.discarding()).statusCode();
	}

	private static boolean success(int status) {
		return status >= 200 && status <= 299;
	}

}
