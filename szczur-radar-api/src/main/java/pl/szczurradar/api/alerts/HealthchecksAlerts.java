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
 * {@link OperatorAlerts} through a healthchecks.io check (F-04). One report is two pings:
 *
 * <ol>
 *   <li>{@code POST <url>/fail} with {@code "<EVENT> (<description>): <detail>"} as plain text, cut to
 *       {@value #MAX_BODY} characters (what the Telegram message shows): the check goes down and alerts;
 *   <li>after a 2xx, {@code POST <url>} with {@code "ok"}: the check is up again. healthchecks.io alerts
 *       only when the state changes, so without it the next event would send nothing.
 * </ol>
 *
 * <p>{@link Delivery#DELIVERED} only when both answered 2xx. One attempt per request, no retries (they
 * live only in {@code llm/transport}); connect {@link #CONNECT_TIMEOUT}, each request
 * {@link #REQUEST_TIMEOUT}; no executor or timer of its own. Any other answer, a transport error, a
 * time-out or an interrupt (the flag is restored) is {@link Delivery#FAILED} with one WARN line naming
 * the event and the status or the exception's class — never the URL (a secret) and never the exception
 * itself, whose message or stack trace could quote it (as in {@code WykopApiClient}). It never throws.
 * Thread-safe.
 */
public final class HealthchecksAlerts implements OperatorAlerts {

	public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

	public static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

	/** The longest body sent; healthchecks.io shows up to this many characters of it in the alert. */
	static final int MAX_BODY = 1000;

	static final String SUCCESS_BODY = "ok";

	private static final Logger log = LoggerFactory.getLogger(HealthchecksAlerts.class);

	private final URI pingUrl;

	private final URI failUrl;

	private final Duration requestTimeout;

	private final HttpClient http;

	/** @param settings a configured {@code HEALTHCHECK_PING_URL} */
	public HealthchecksAlerts(AlertSettings settings) {
		this(settings.pingUrl(), CONNECT_TIMEOUT, REQUEST_TIMEOUT);
	}

	/** @param pingUrl the check's ping URL; a local {@code http://} server in tests */
	HealthchecksAlerts(URI pingUrl, Duration connectTimeout, Duration requestTimeout) {
		this.pingUrl = AlertSettings.withoutTrailingSlash(Objects.requireNonNull(pingUrl, "pingUrl"));
		this.failUrl = URI.create(this.pingUrl + "/fail");
		this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
		this.http = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
	}

	@Override
	public Delivery report(AlertEvent event, @Nullable String detail) {
		try {
			int failStatus = post(failUrl, body(event, detail));
			if (!success(failStatus)) {
				log.warn("Operator alert {} not delivered: healthchecks.io answered HTTP {} to the failure ping",
						event, failStatus);
				return Delivery.FAILED;
			}
			int okStatus = post(pingUrl, SUCCESS_BODY);
			if (!success(okStatus)) {
				log.warn("Operator alert {} reached healthchecks.io, but it answered HTTP {} to the success ping:"
						+ " the check may stay down, so the next alert may send nothing", event, okStatus);
				return Delivery.FAILED;
			}
			log.info("Operator alert {} delivered to healthchecks.io", event);
			return Delivery.DELIVERED;
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			log.warn("Operator alert {} not delivered: interrupted", event);
			return Delivery.FAILED;
		}
		catch (IOException | RuntimeException ex) {
			// Only the class: the message (or a stack trace) could quote the URL.
			log.warn("Operator alert {} not delivered: {}", event, ex.getClass().getName());
			return Delivery.FAILED;
		}
	}

	/** Never prints the URL. */
	@Override
	public String toString() {
		return "HealthchecksAlerts[<hidden>]";
	}

	/** {@code "<EVENT> (<description>): <detail>"}, at most {@value #MAX_BODY} characters. */
	static String body(AlertEvent event, @Nullable String detail) {
		String text = event.name() + " (" + event.description() + "): " + (detail == null ? "" : detail.strip());
		if (text.length() <= MAX_BODY) {
			return text;
		}
		int end = MAX_BODY - 1;
		if (Character.isHighSurrogate(text.charAt(end - 1))) {
			end--;
		}
		return text.substring(0, end) + "…";
	}

	private int post(URI uri, String body) throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(uri)
			.timeout(requestTimeout)
			.header("Content-Type", "text/plain; charset=utf-8")
			.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
			.build();
		return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
	}

	private static boolean success(int status) {
		return status >= 200 && status <= 299;
	}

}
