package pl.szczurradar.api.sources.transport;

import java.io.IOException;
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
import java.util.List;
import java.util.Objects;
import java.util.function.IntPredicate;
import java.util.function.UnaryOperator;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The one retry loop of a source's HTTP client (Wykop, Reddit): every exchange gets up to
 * {@value #MAX_ATTEMPTS} attempts on the statuses the source retries ({@code retriedStatus}; usually
 * {@link #transientStatus}) and on transport errors (connection errors and request time-outs), waiting
 * the first readable of the source's wait headers (seconds or an HTTP date, capped at
 * {@link #MAX_RETRY_AFTER}) or, without one, 2 s and then 4 s. Other answers are returned to the client,
 * which decides what they mean (a 401 refreshes a token, a 4xx is an error); interruptions are never
 * retried. Every retry is one WARN line; the last failure is thrown without a log of its own (the caller
 * logs it). Nothing is retried past the caller's deadline: an attempt starts only if its time-out fits
 * before the deadline, a wait only if the wait and the next attempt's time-out fit, otherwise the call
 * fails with {@link HttpRetryException.Kind#DEADLINE_EXCEEDED}.
 *
 * <p>Secrets stay the client's business: every message and WARN line passes through the client's
 * {@code clean} (masking, length cap), and the transport exception is never attached as the cause — its
 * message would bypass the masking in a stack trace. Stateless besides its settings; thread-safe.
 */
public final class HttpRetry {

	/** Attempts of one HTTP exchange. */
	public static final int MAX_ATTEMPTS = 3;

	/** The longest wait a wait header can ask for. */
	public static final Duration MAX_RETRY_AFTER = Duration.ofSeconds(30);

	/** The wait after the first failed attempt without a readable wait header; doubled after each one. */
	public static final Duration FIRST_BACKOFF = Duration.ofSeconds(2);

	private static final Logger log = LoggerFactory.getLogger(HttpRetry.class);

	/** Waits between attempts; replaced in tests. */
	@FunctionalInterface
	public interface Sleeper {

		void sleep(Duration duration) throws InterruptedException;

		Sleeper REAL = Thread::sleep;

	}

	/**
	 * A failed attempt that may be retried; {@code description} is not yet cleaned and never leaves this
	 * class unless through the client's {@code clean}.
	 */
	private record Failure(HttpRetryException.Kind kind, int status, String description,
			@Nullable Duration retryAfter) {
	}

	private final String source;

	private final IntPredicate retriedStatus;

	private final List<String> waitHeaders;

	private final Sleeper sleeper;

	private final InstantSource clock;

	/**
	 * @param source the source's name in messages ({@code Wykop}, {@code Reddit})
	 * @param retriedStatus whether an answer with this status is retried; every other one is returned
	 * @param waitHeaders the headers that say how long to wait, in order; the first readable one wins
	 * @param sleeper waits between attempts
	 * @param clock the clock of deadlines and of HTTP-date waits
	 */
	public HttpRetry(String source, IntPredicate retriedStatus, List<String> waitHeaders, Sleeper sleeper,
			InstantSource clock) {
		this.source = Objects.requireNonNull(source, "source");
		this.retriedStatus = Objects.requireNonNull(retriedStatus, "retriedStatus");
		this.waitHeaders = List.copyOf(waitHeaders);
		this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	/** HTTP 429 and 5xx: the statuses every source retries. */
	public static boolean transientStatus(int status) {
		return status == 429 || (status >= 500 && status <= 599);
	}

	/**
	 * Sends {@code request} until it gets an answer whose status is not retried, which is returned.
	 *
	 * @param call the call in messages, e.g. {@code GET search/entries}
	 * @param timeout one attempt's time-out (the request's own), for the deadline checks
	 * @param deadline nothing is attempted or waited for that would end after it
	 * @param clean the client's masking of secrets, applied to every message and WARN line
	 * @throws HttpRetryException when the attempts are used up (the last failure's kind), the deadline
	 *     comes first ({@link HttpRetryException.Kind#DEADLINE_EXCEEDED}) or the thread is interrupted
	 *     ({@link HttpRetryException.Kind#INTERRUPTED}, the interrupt flag kept)
	 */
	public HttpResponse<String> exchange(HttpClient http, HttpRequest request, String call, Duration timeout,
			Instant deadline, UnaryOperator<String> clean) {
		Failure last = null;
		for (int attempt = 1;; attempt++) {
			requireTime(Duration.ZERO, timeout, deadline, call, last, attempt - 1, clean);
			Failure failure;
			try {
				HttpResponse<String> response = http.send(request,
						HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
				if (!retriedStatus.test(response.statusCode())) {
					return response;
				}
				String body = response.body() == null ? "" : response.body();
				failure = new Failure(HttpRetryException.Kind.HTTP_STATUS, response.statusCode(),
						source + " " + call + " answered HTTP " + response.statusCode() + ": " + body,
						waitHeader(response));
			}
			catch (IOException ex) {
				failure = new Failure(HttpRetryException.Kind.TRANSPORT, 0,
						source + " " + request.method() + " " + request.uri().getPath() + " failed: " + ex, null);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				throw new HttpRetryException(HttpRetryException.Kind.INTERRUPTED, 0, attempt,
						clean.apply("interrupted during " + source + " " + request.uri().getPath()), ex);
			}
			if (attempt == MAX_ATTEMPTS) {
				throw new HttpRetryException(failure.kind(), failure.status(), attempt,
						clean.apply(failure.description() + " after " + MAX_ATTEMPTS + " attempts"), null);
			}
			Duration wait = wait(failure, attempt);
			requireTime(wait, timeout, deadline, call, failure, attempt, clean);
			log.warn("{}", clean.apply("Retrying " + source + " " + call + " in " + format(wait) + " after attempt "
					+ attempt + " of " + MAX_ATTEMPTS + ": " + failure.description()));
			try {
				sleeper.sleep(wait);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				throw new HttpRetryException(HttpRetryException.Kind.INTERRUPTED, 0, attempt,
						clean.apply("interrupted while waiting to retry " + source + " " + call), ex);
			}
			last = failure;
		}
	}

	/**
	 * Fails with {@link HttpRetryException.Kind#DEADLINE_EXCEEDED} unless {@code wait} and one attempt's
	 * time-out still end by {@code deadline}.
	 *
	 * @param last the failure of attempt {@code attempts}, {@code null} before the first attempt
	 */
	private void requireTime(Duration wait, Duration timeout, Instant deadline, String call, @Nullable Failure last,
			int attempts, UnaryOperator<String> clean) {
		if (!clock.instant().plus(wait).plus(timeout).isAfter(deadline)) {
			return;
		}
		String message = last == null ? source + " " + call + " reached its deadline before the first attempt"
				: source + " " + call + " reached its deadline after " + attempts + " attempt(s): "
						+ last.description();
		throw new HttpRetryException(HttpRetryException.Kind.DEADLINE_EXCEEDED, 0, attempts, clean.apply(message),
				null);
	}

	/** The wait after failed attempt {@code attempt}: the wait header (capped) or 2 s, 4 s, … */
	private static Duration wait(Failure failure, int attempt) {
		Duration retryAfter = failure.retryAfter();
		if (retryAfter != null) {
			return retryAfter.compareTo(MAX_RETRY_AFTER) > 0 ? MAX_RETRY_AFTER : retryAfter;
		}
		return FIRST_BACKOFF.multipliedBy(1L << (attempt - 1));
	}

	/** The first of {@link #waitHeaders} the answer has in a readable form, else {@code null}. */
	private @Nullable Duration waitHeader(HttpResponse<?> response) {
		for (String header : waitHeaders) {
			Duration wait = waitOf(response.headers().firstValue(header).orElse(null));
			if (wait != null) {
				return wait;
			}
		}
		return null;
	}

	/** A wait in seconds or as an HTTP date (a past one: no wait), else {@code null}. */
	private @Nullable Duration waitOf(@Nullable String value) {
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

}
