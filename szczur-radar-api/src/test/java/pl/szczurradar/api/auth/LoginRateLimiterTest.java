package pl.szczurradar.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.szczurradar.api.auth.LoginRateLimiter.EMAIL_LIMIT;
import static pl.szczurradar.api.auth.LoginRateLimiter.IP_LIMIT;
import static pl.szczurradar.api.auth.LoginRateLimiter.WINDOW;

import java.time.Duration;
import java.util.Optional;
import java.util.function.LongSupplier;

import org.junit.jupiter.api.Test;

import pl.szczurradar.api.auth.LoginRateLimiter.Outcome;

/** Failed-login limiter, ported from the Python tests/test_rate_limit.py and extended. */
class LoginRateLimiterTest {

	private static final String EMAIL = "a@example.com";

	private static final String IP = "10.0.0.1";

	private final FakeClock clock = new FakeClock();

	private LoginRateLimiter limiter = new LoginRateLimiter(clock, EMAIL_LIMIT, IP_LIMIT, WINDOW);

	@Test
	void emailLimitBlocksAfterFiveFailures() {
		for (int i = 0; i < EMAIL_LIMIT; i++) {
			fail(EMAIL, "10.0.0." + i);
		}
		assertThat(limiter.reserve(EMAIL, "10.0.0.99")).contains(WINDOW);
		assertThat(reserveAndAbort("b@example.com", "10.0.0.99")).isEmpty();
	}

	@Test
	void ipLimitBlocksAcrossEmails() {
		for (int i = 0; i < IP_LIMIT; i++) {
			fail("user" + i + "@example.com", IP);
		}
		assertThat(limiter.reserve("fresh@example.com", IP)).isPresent();
		assertThat(reserveAndAbort("fresh@example.com", "10.0.0.2")).isEmpty();
	}

	@Test
	void windowSlides() {
		for (int i = 0; i < EMAIL_LIMIT; i++) {
			fail(EMAIL, IP);
			clock.advance(Duration.ofMinutes(1));
		}
		// The oldest failure (t=0) leaves the window at t=15 min; now is t=5 min.
		Duration blockedFor = limiter.reserve(EMAIL, IP).orElseThrow();
		assertThat(blockedFor).isEqualTo(WINDOW.minusMinutes(EMAIL_LIMIT));

		clock.advance(blockedFor);
		assertThat(reserveAndAbort(EMAIL, IP)).isEmpty();
	}

	@Test
	void blockLastsUntilTheFailuresLeaveTheWindow() {
		for (int i = 0; i < EMAIL_LIMIT; i++) {
			fail(EMAIL, IP);
		}
		clock.advance(Duration.ofMinutes(3));
		assertThat(limiter.reserve(EMAIL, IP)).contains(Duration.ofSeconds(720));

		clock.advance(Duration.ofMinutes(12));
		assertThat(reserveAndAbort(EMAIL, IP)).isEmpty();
	}

	@Test
	void successClearsOnlyTheEmailHistory() {
		limiter = new LoginRateLimiter(clock, EMAIL_LIMIT, EMAIL_LIMIT, WINDOW);
		for (int i = 0; i < EMAIL_LIMIT - 1; i++) {
			fail(EMAIL, IP);
		}
		assertThat(limiter.reserve(EMAIL, IP)).isEmpty();
		limiter.finish(EMAIL, IP, Outcome.SUCCESS); // same e-mail AND same IP as the failures
		fail("other@example.com", IP); // the IP's 4 kept failures + 1 = the IP limit

		// The e-mail history is cleared...
		assertThat(reserveAndAbort(EMAIL, "10.0.0.3")).isEmpty();
		// ...but the IP history is not.
		assertThat(limiter.reserve(EMAIL, IP)).isPresent();
	}

	@Test
	void parallelAttemptsCannotAllPassTheEmailLimit() {
		for (int i = 0; i < EMAIL_LIMIT; i++) {
			assertThat(limiter.reserve(EMAIL, IP)).isEmpty();
		}
		// All five are still in progress: the sixth is refused, retry in a second.
		assertThat(limiter.reserve(EMAIL, IP)).contains(Duration.ofSeconds(1));

		for (int i = 0; i < EMAIL_LIMIT; i++) {
			limiter.finish(EMAIL, IP, Outcome.FAILURE);
		}
		assertThat(limiter.reserve(EMAIL, "10.0.0.2")).contains(WINDOW);
	}

	@Test
	void successKeepsOtherReservationsAndFreesExactlyOneSlot() {
		for (int i = 0; i < EMAIL_LIMIT; i++) {
			assertThat(limiter.reserve(EMAIL, IP)).isEmpty();
		}
		limiter.finish(EMAIL, IP, Outcome.SUCCESS);

		assertThat(limiter.reserve(EMAIL, IP)).isEmpty();
		assertThat(limiter.reserve(EMAIL, IP)).contains(Duration.ofSeconds(1));
	}

	@Test
	void ipRefusalDoesNotConsumeAnEmailReservation() {
		limiter = new LoginRateLimiter(clock, 2, 1, WINDOW);
		fail("other@example.com", IP); // the IP is full

		assertThat(limiter.reserve(EMAIL, IP)).isPresent();
		// Both e-mail slots are still free.
		assertThat(limiter.reserve(EMAIL, "10.0.0.2")).isEmpty();
		assertThat(limiter.reserve(EMAIL, "10.0.0.3")).isEmpty();
	}

	@Test
	void emailRefusalDoesNotConsumeAnIpReservation() {
		limiter = new LoginRateLimiter(clock, 1, 2, WINDOW);
		fail(EMAIL, "10.0.0.9"); // the e-mail is full

		assertThat(limiter.reserve(EMAIL, IP)).isPresent();
		// Both IP slots are still free.
		assertThat(limiter.reserve("b@example.com", IP)).isEmpty();
		assertThat(limiter.reserve("c@example.com", IP)).isEmpty();
	}

	@Test
	void abortedAttemptReleasesItsReservationWithoutRecordingAFailure() {
		limiter = new LoginRateLimiter(clock, 1, 1, WINDOW);
		assertThat(limiter.reserve(EMAIL, IP)).isEmpty();
		assertThat(limiter.reserve(EMAIL, IP)).isPresent();

		limiter.finish(EMAIL, IP, Outcome.ABORTED);

		assertThat(limiter.reserve(EMAIL, IP)).isEmpty();
	}

	@Test
	void expiredHistoriesOfInactiveKeysAreSweptOnTheNextRequest() {
		for (int i = 0; i < 50; i++) {
			fail("user" + i + "@example.com", "10.0.0." + i);
		}
		assertThat(limiter.trackedHistories()).isEqualTo(100);

		clock.advance(WINDOW);
		assertThat(reserveAndAbort("fresh@example.com", "10.0.1.1")).isEmpty();

		assertThat(limiter.trackedHistories()).isZero();
	}

	@Test
	void timeDoesNotReleaseReservationsInProgress() {
		limiter = new LoginRateLimiter(clock, 1, IP_LIMIT, WINDOW);
		assertThat(limiter.reserve(EMAIL, IP)).isEmpty();

		clock.advance(WINDOW.multipliedBy(3)); // several sweeps are due

		assertThat(limiter.reserve(EMAIL, "10.0.0.2")).contains(Duration.ofSeconds(1));
	}

	@Test
	void retryAfterRoundsUpToWholeSeconds() {
		limiter = new LoginRateLimiter(clock, 1, IP_LIMIT, WINDOW);
		fail(EMAIL, IP);
		clock.advance(WINDOW.minusMillis(1_500)); // 1.5 s left

		assertThat(limiter.reserve(EMAIL, IP)).contains(Duration.ofSeconds(2));

		clock.advance(Duration.ofMillis(1_499)); // 1 ms left
		assertThat(limiter.reserve(EMAIL, IP)).contains(Duration.ofSeconds(1));
	}

	@Test
	void retryAfterIsTheLongerWaitWhenBothLimitsBlock() {
		limiter = new LoginRateLimiter(clock, 1, 1, WINDOW);
		fail(EMAIL, "10.0.0.9"); // e-mail blocked from t=0
		clock.advance(Duration.ofMinutes(5));
		fail("other@example.com", IP); // IP blocked from t=5 min

		// e-mail frees at t=15 min (10 min left), IP at t=20 min (15 min left).
		assertThat(limiter.reserve(EMAIL, IP)).contains(WINDOW);
	}

	private void fail(String email, String ip) {
		assertThat(limiter.reserve(email, ip)).as("reserve %s %s", email, ip).isEmpty();
		limiter.finish(email, ip, Outcome.FAILURE);
	}

	private Optional<Duration> reserveAndAbort(String email, String ip) {
		Optional<Duration> wait = limiter.reserve(email, ip);
		if (wait.isEmpty()) {
			limiter.finish(email, ip, Outcome.ABORTED);
		}
		return wait;
	}

	private static final class FakeClock implements LongSupplier {

		private long now = Duration.ofHours(1).toNanos();

		@Override
		public long getAsLong() {
			return now;
		}

		void advance(Duration duration) {
			now += duration.toNanos();
		}

	}

}
