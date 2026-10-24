package pl.szczurradar.api.alerts;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectProvider;

import pl.szczurradar.api.alerts.OperatorAlerts.Delivery;

/**
 * The one entry point of the error paths to the operator alert channel (S-18): {@link #raise} takes an
 * event at once, from any thread, an interrupted one too, and its own platform thread
 * ({@code operator-alerts}) reports it through {@link OperatorAlerts}, so the caller never waits for
 * healthchecks.io.
 *
 * <ul>
 *   <li><b>Repeats:</b> the same event for the same run (or for no run) within {@link #REPEAT_WINDOW} of
 *       the last one taken goes once. The repeats are counted, and the next one taken with that key after
 *       the window says how many were not sent.
 *   <li><b>Limit:</b> at most {@link #MAX_PER_MINUTE} reports in {@link #RATE_WINDOW}. healthchecks.io takes
 *       at most 5 pings a minute per check and each report is two; a refused success ping would leave the
 *       check down, and then later events would alert nobody. The rest wait, at most {@link #CAPACITY} of
 *       them; one more is dropped with a WARN naming the event.
 *   <li><b>Logs:</b> an INFO line when an event is taken (or not sent as a repeat) and another with the
 *       {@link Delivery} once it was reported, both with the event and the run. Never the ping URL (a
 *       secret, which only {@link HealthchecksAlerts} knows); {@code detail} is text for a human without
 *       secrets, and {@link HealthchecksAlerts} cuts it.
 *   <li><b>Shutdown:</b> {@link #destroy} sends what is still waiting for at most {@link #DRAIN_TIMEOUT}, then
 *       stops the thread and names the events it dropped. An event raised after it is dropped with an INFO
 *       line: beans that take the queue through an {@code ObjectProvider} do not depend on it, so it may be
 *       destroyed before them.
 * </ul>
 *
 * <p>No retries and nothing durable: an event still waiting when the process is killed is lost (the
 * in-app notification and the log stay). Only in the server, next to {@link OperatorAlerts}
 * ({@link AlertsConfiguration}). Thread-safe; {@link #raise} never throws.
 */
public class OperatorAlertQueue implements DisposableBean {

	/** The same event for the same run within this time of the last one taken is not sent again. */
	public static final Duration REPEAT_WINDOW = Duration.ofMinutes(10);

	/** The most reports in {@link #RATE_WINDOW}. */
	public static final int MAX_PER_MINUTE = 2;

	static final Duration RATE_WINDOW = Duration.ofMinutes(1);

	/** The most events waiting to be sent. */
	public static final int CAPACITY = 20;

	/** How long {@link #destroy} sends what is still waiting. */
	public static final Duration DRAIN_TIMEOUT = Duration.ofSeconds(5);

	private static final Logger log = LoggerFactory.getLogger(OperatorAlertQueue.class);

	/** Waits on the sender's thread until the limit lets the next report go; the tests' one moves their clock. */
	interface Sleeper {

		void sleep(Duration duration) throws InterruptedException;

		Sleeper REAL = Thread::sleep;

	}

	/** What {@link #raise} deduplicates on; {@code runId} may be {@code null}. */
	private record Key(AlertEvent event, @Nullable UUID runId) {
	}

	/** The last event taken with one {@link Key} and how many repeats of it were not sent since. */
	private static final class Seen {

		private final Instant taken;

		private int repeats;

		Seen(Instant taken) {
			this.taken = taken;
		}

	}

	/** One event to report; a {@link Runnable}, so the tasks {@link #destroy} drops can be named. */
	private final class Send implements Runnable {

		private final AlertEvent event;

		private final @Nullable UUID runId;

		private final String detail;

		Send(AlertEvent event, @Nullable UUID runId, String detail) {
			this.event = event;
			this.runId = runId;
			this.detail = detail;
		}

		@Override
		public void run() {
			send(this);
		}

		@Override
		public String toString() {
			return event + " (" + runLabel(runId) + ")";
		}

	}

	private final OperatorAlerts alerts;

	private final InstantSource clock;

	private final Sleeper sleeper;

	private final Duration drainTimeout;

	private final ThreadPoolExecutor executor;

	private final Object lock = new Object();

	/** Guarded by {@link #lock}. */
	private final Map<Key, Seen> seen = new HashMap<>();

	/** Guarded by {@link #lock}. */
	private boolean closed;

	/** When the last reports started; only the sender's thread uses it. */
	private final ArrayDeque<Instant> sent = new ArrayDeque<>();

	public OperatorAlertQueue(OperatorAlerts alerts) {
		this(alerts, InstantSource.system(), Sleeper.REAL, DRAIN_TIMEOUT);
	}

	/**
	 * @param clock the clock of the repeat window and the limit
	 * @param sleeper waits for the limit
	 * @param drainTimeout how long {@link #destroy} sends what is waiting
	 */
	OperatorAlertQueue(OperatorAlerts alerts, InstantSource clock, Sleeper sleeper, Duration drainTimeout) {
		this.alerts = Objects.requireNonNull(alerts, "alerts");
		this.clock = Objects.requireNonNull(clock, "clock");
		this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
		this.drainTimeout = Objects.requireNonNull(drainTimeout, "drainTimeout");
		// One thread, at most CAPACITY waiting; one more is refused (RejectedExecutionException).
		this.executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(CAPACITY),
				task -> Thread.ofPlatform().name("operator-alerts").unstarted(task));
	}

	/**
	 * Takes {@code event} for {@code runId} ({@code null} when it concerns no run) to be reported with
	 * {@code detail} (what happened, for a human; never a secret). Returns at once, whatever the channel
	 * does and whether the calling thread is interrupted (its flag is left as it was). Never throws: a
	 * repeat, a full queue or a closed one is only logged.
	 */
	public void raise(AlertEvent event, @Nullable UUID runId, String detail) {
		try {
			synchronized (lock) {
				if (closed) {
					log.info("Operator alert {} ({}) dropped: the application is shutting down", event,
							runLabel(runId));
					return;
				}
				Instant now = clock.instant();
				forgetExpired(now);
				Key key = new Key(event, runId);
				Seen last = seen.get(key);
				if (last != null && now.isBefore(last.taken.plus(REPEAT_WINDOW))) {
					last.repeats++;
					log.info("Operator alert {} ({}) not sent: a repeat within {} min of the last one ({} so far)",
							event, runLabel(runId), REPEAT_WINDOW.toMinutes(), last.repeats);
					return;
				}
				int repeats = last == null ? 0 : last.repeats;
				// Refused when full: then the last one and its repeats stay for the next try.
				executor.execute(new Send(event, runId, withRepeats(detail, repeats)));
				seen.put(key, new Seen(now));
				log.info("Operator alert {} ({}) taken", event, runLabel(runId));
			}
		}
		catch (RejectedExecutionException ex) {
			log.warn("Operator alert {} ({}) dropped: {} alerts are already waiting to be sent", event,
					runLabel(runId), CAPACITY);
		}
		catch (RuntimeException ex) {
			log.warn("Operator alert {} ({}) dropped: {}", event, runLabel(runId), ex.getClass().getName());
		}
	}

	/**
	 * {@link #raise} through the context's queue, for a bean shared with the operator commands, which takes
	 * it as an {@link ObjectProvider}: the server has one, an operator command's context none, and then
	 * nothing happens. Never throws.
	 */
	public static void raise(ObjectProvider<OperatorAlertQueue> queue, AlertEvent event, @Nullable UUID runId,
			String detail) {
		try {
			OperatorAlertQueue alerts = queue.getIfAvailable();
			if (alerts != null) {
				alerts.raise(event, runId, detail);
			}
		}
		catch (RuntimeException ex) {
			log.warn("Operator alert {} ({}) dropped: {}", event, runLabel(runId), ex.getClass().getName());
		}
	}

	/**
	 * Sends what is still waiting for at most the drain timeout ({@link #DRAIN_TIMEOUT}), then stops the
	 * thread (an interrupted report is {@link Delivery#FAILED}) and names the events dropped. Later
	 * {@link #raise} calls are dropped.
	 */
	@Override
	public void destroy() {
		synchronized (lock) {
			closed = true;
		}
		executor.shutdown();
		try {
			if (executor.awaitTermination(drainTimeout.toNanos(), TimeUnit.NANOSECONDS)) {
				return;
			}
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
		List<Runnable> dropped = executor.shutdownNow();
		if (!dropped.isEmpty()) {
			log.warn("Operator alerts not sent within {} ms of shutdown, dropped: {}", drainTimeout.toMillis(),
					dropped);
		}
	}

	private void send(Send alert) {
		try {
			awaitLimit();
			sent.addLast(clock.instant());
			Delivery delivery = alerts.report(alert.event, alert.detail);
			log.info("Operator alert {} ({}) reported: {}", alert.event, runLabel(alert.runId), delivery);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			log.warn("Operator alert {} ({}) dropped: interrupted while waiting for the limit of {} a minute",
					alert.event, runLabel(alert.runId), MAX_PER_MINUTE);
		}
		catch (RuntimeException ex) {
			// OperatorAlerts never throws; only the class, as in HealthchecksAlerts.
			log.warn("Operator alert {} ({}) not reported: {}", alert.event, runLabel(alert.runId),
					ex.getClass().getName());
		}
	}

	/** Waits until fewer than {@link #MAX_PER_MINUTE} reports started in the last {@link #RATE_WINDOW}. */
	private void awaitLimit() throws InterruptedException {
		while (true) {
			Instant now = clock.instant();
			while (!sent.isEmpty() && !sent.getFirst().plus(RATE_WINDOW).isAfter(now)) {
				sent.removeFirst();
			}
			if (sent.size() < MAX_PER_MINUTE) {
				return;
			}
			sleeper.sleep(Duration.between(now, sent.getFirst().plus(RATE_WINDOW)));
		}
	}

	/** Forgets keys whose window ended without a repeat; one with repeats stays to report their number. */
	private void forgetExpired(Instant now) {
		seen.values().removeIf(last -> last.repeats == 0 && !now.isBefore(last.taken.plus(REPEAT_WINDOW)));
	}

	private static String withRepeats(@Nullable String detail, int repeats) {
		String text = detail == null ? "" : detail;
		if (repeats == 0) {
			return text;
		}
		return text + " (" + repeats + " repeat(s) within " + REPEAT_WINDOW.toMinutes()
				+ " min of the last alert were not sent)";
	}

	private static String runLabel(@Nullable UUID runId) {
		return runId == null ? "no run" : "run " + runId;
	}

}
