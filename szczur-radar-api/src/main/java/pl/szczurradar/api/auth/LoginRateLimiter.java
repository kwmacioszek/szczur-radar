package pl.szczurradar.api.auth;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;

import org.springframework.stereotype.Component;

/**
 * In-process limit of failed login attempts over a sliding window: 5 failures per 15 minutes per
 * normalized e-mail and 20 per client IP. Ported from the Python {@code app/auth/rate_limit.py}.
 *
 * <p>Attempts in progress are reserved: {@link #reserve} atomically checks both limits — counting
 * recent failures plus attempts still in progress — and only then reserves one slot in each, so
 * parallel attempts cannot all slip past the limit. Every successful reservation is released
 * exactly once by {@link #finish}: a failure is recorded in both histories; a success clears the
 * e-mail's failure history only (not the IP's, and not other attempts in progress); an aborted
 * attempt records nothing.
 *
 * <p>The state lives in this process only, which is correct while the API runs a single replica
 * ({@code .railway/railway.ts}); more replicas need a shared store.
 */
@Component
public class LoginRateLimiter {

	public static final int EMAIL_LIMIT = 5;

	public static final int IP_LIMIT = 20;

	public static final Duration WINDOW = Duration.ofMinutes(15);

	private static final long NANOS_PER_SECOND = Duration.ofSeconds(1).toNanos();

	/** How a reserved attempt ended. */
	public enum Outcome {

		/** Authentication ran and failed. */
		FAILURE,
		/** Authentication succeeded. */
		SUCCESS,
		/** The attempt ended without an authentication verdict (e.g. an exception). */
		ABORTED

	}

	private final LongSupplier nanoClock;

	private final int emailLimit;

	private final int ipLimit;

	private final long windowNanos;

	private final Map<String, ArrayDeque<Long>> failuresByEmail = new HashMap<>();

	private final Map<String, ArrayDeque<Long>> failuresByIp = new HashMap<>();

	private final Map<String, Integer> pendingByEmail = new HashMap<>();

	private final Map<String, Integer> pendingByIp = new HashMap<>();

	private final ReentrantLock lock = new ReentrantLock();

	private long nextSweep;

	public LoginRateLimiter() {
		this(System::nanoTime, EMAIL_LIMIT, IP_LIMIT, WINDOW);
	}

	/** @param nanoClock monotonic clock in nanoseconds (injected by tests) */
	LoginRateLimiter(LongSupplier nanoClock, int emailLimit, int ipLimit, Duration window) {
		this.nanoClock = nanoClock;
		this.emailLimit = emailLimit;
		this.ipLimit = ipLimit;
		this.windowNanos = window.toNanos();
		this.nextSweep = nanoClock.getAsLong() + windowNanos;
	}

	/**
	 * Reserves one attempt for {@code email} and {@code ip} if both limits allow it.
	 *
	 * @return empty if reserved (then {@link #finish} must follow exactly once), otherwise how long
	 *     to wait; a refusal reserves nothing
	 */
	public Optional<Duration> reserve(String email, String ip) {
		lock.lock();
		try {
			long now = nanoClock.getAsLong();
			sweepIfDue(now);
			long emailWait = retryAfterSeconds(recent(failuresByEmail, email, now), pendingByEmail.getOrDefault(email, 0),
					emailLimit, now);
			long ipWait = retryAfterSeconds(recent(failuresByIp, ip, now), pendingByIp.getOrDefault(ip, 0), ipLimit, now);
			long wait = Math.max(emailWait, ipWait);
			if (wait > 0) {
				return Optional.of(Duration.ofSeconds(wait));
			}
			pendingByEmail.merge(email, 1, Integer::sum);
			pendingByIp.merge(ip, 1, Integer::sum);
			return Optional.empty();
		}
		finally {
			lock.unlock();
		}
	}

	/** Releases a reservation made by {@link #reserve}, recording only completed attempts. */
	public void finish(String email, String ip, Outcome outcome) {
		lock.lock();
		try {
			release(pendingByEmail, email);
			release(pendingByIp, ip);
			switch (outcome) {
				case FAILURE -> {
					long now = nanoClock.getAsLong();
					failuresByEmail.computeIfAbsent(email, key -> new ArrayDeque<>()).addLast(now);
					failuresByIp.computeIfAbsent(ip, key -> new ArrayDeque<>()).addLast(now);
				}
				case SUCCESS -> failuresByEmail.remove(email);
				case ABORTED -> {
				}
			}
		}
		finally {
			lock.unlock();
		}
	}

	/** Number of keys with a failure history (for tests of the sweep). */
	int trackedHistories() {
		lock.lock();
		try {
			return failuresByEmail.size() + failuresByIp.size();
		}
		finally {
			lock.unlock();
		}
	}

	private static void release(Map<String, Integer> pending, String key) {
		pending.computeIfPresent(key, (k, count) -> count == 1 ? null : count - 1);
	}

	/** Failures for {@code key} still inside the window; expired ones (and empty histories) dropped. */
	private ArrayDeque<Long> recent(Map<String, ArrayDeque<Long>> histories, String key, long now) {
		ArrayDeque<Long> failures = histories.get(key);
		if (failures == null) {
			return new ArrayDeque<>();
		}
		long cutoff = now - windowNanos;
		while (!failures.isEmpty() && failures.peekFirst() - cutoff <= 0) {
			failures.removeFirst();
		}
		if (failures.isEmpty()) {
			histories.remove(key);
		}
		return failures;
	}

	/**
	 * Seconds to wait (0 = allowed). Blocked only by attempts in progress: 1 s, they may end soon.
	 * Blocked by failures: until the failure freeing a slot leaves the window, rounded up, at least 1.
	 */
	private long retryAfterSeconds(ArrayDeque<Long> failures, int pending, int limit, long now) {
		if (failures.size() + pending < limit) {
			return 0;
		}
		if (failures.size() < limit) {
			return 1;
		}
		Iterator<Long> it = failures.iterator();
		for (int skip = failures.size() - limit; skip > 0; skip--) {
			it.next();
		}
		long freesAt = it.next() + windowNanos;
		return Math.max(1, Math.ceilDiv(freesAt - now, NANOS_PER_SECOND));
	}

	/** Drops expired histories of inactive keys, at most once per window. Never touches reservations. */
	private void sweepIfDue(long now) {
		if (now - nextSweep < 0) {
			return;
		}
		for (Map<String, ArrayDeque<Long>> histories : List.of(failuresByEmail, failuresByIp)) {
			for (String key : List.copyOf(histories.keySet())) {
				recent(histories, key, now);
			}
		}
		nextSweep = now + windowNanos;
	}

}
