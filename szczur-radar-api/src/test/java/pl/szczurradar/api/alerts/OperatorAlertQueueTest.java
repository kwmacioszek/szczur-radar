package pl.szczurradar.api.alerts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import pl.szczurradar.api.alerts.OperatorAlerts.Delivery;

/**
 * {@link OperatorAlertQueue} over a fake {@link OperatorAlerts} and a clock the tests move (the limit's
 * waits move it too), without the network: {@code raise} never waits or throws, an interrupted caller
 * still gets its event out, repeats within the window go once and are counted, at most two reports a
 * minute, a full queue drops with a WARN, and {@code destroy} sends what waits within its time limit.
 */
@ExtendWith(OutputCaptureExtension.class)
class OperatorAlertQueueTest {

	private static final Instant T0 = Instant.parse("2026-10-24T10:00:00Z");

	private static final UUID RUN = UUID.fromString("7d3f0c52-6a1e-4f7b-9c2d-5e8a1b4c3d2e");

	private static final UUID OTHER_RUN = UUID.fromString("1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d");

	private final TestClock clock = new TestClock(T0);

	private final List<Duration> sleeps = new CopyOnWriteArrayList<>();

	private final OperatorAlertQueue.Sleeper sleeper = duration -> {
		sleeps.add(duration);
		clock.advance(duration);
	};

	private final List<OperatorAlertQueue> queues = new ArrayList<>();

	@AfterEach
	void stop() {
		queues.forEach(OperatorAlertQueue::destroy);
	}

	@Test
	void raiseReturnsAtOnceWhileTheChannelHangs() throws InterruptedException {
		FakeAlerts alerts = FakeAlerts.hanging(clock);
		OperatorAlertQueue queue = queue(alerts, OperatorAlertQueue.DRAIN_TIMEOUT);

		assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
			queue.raise(AlertEvent.RUN_NEEDS_ATTENTION, RUN, "first");
			assertThat(alerts.entered.await(1, TimeUnit.SECONDS)).isTrue();
			queue.raise(AlertEvent.RUN_NEEDS_ATTENTION, OTHER_RUN, "second");
			queue.raise(AlertEvent.WATCHDOG_FAILED, RUN, "third");
		});

		alerts.release.countDown();
		assertThat(List.of(alerts.next(), alerts.next(), alerts.next())).extracting(Report::detail)
			.containsExactly("first", "second", "third");
	}

	@Test
	void anInterruptedCallerGetsItsEventOutAndKeepsItsFlag() throws InterruptedException {
		FakeAlerts alerts = new FakeAlerts(clock);
		OperatorAlertQueue queue = queue(alerts, OperatorAlertQueue.DRAIN_TIMEOUT);

		Thread.currentThread().interrupt();
		queue.raise(AlertEvent.RUN_CLOSE_FAILED, RUN, "closing on shutdown");
		boolean stillInterrupted = Thread.interrupted();

		assertThat(stillInterrupted).isTrue();
		Report report = alerts.next();
		assertThat(report.event()).isEqualTo(AlertEvent.RUN_CLOSE_FAILED);
		assertThat(report.detail()).isEqualTo("closing on shutdown");
	}

	@Test
	void aRepeatWithinTheWindowIsNotSentAndTheNextOneAfterItCountsIt(CapturedOutput output)
			throws InterruptedException {
		FakeAlerts alerts = new FakeAlerts(clock);
		OperatorAlertQueue queue = queue(alerts, OperatorAlertQueue.DRAIN_TIMEOUT);

		queue.raise(AlertEvent.WATCHDOG_FAILED, RUN, "first");
		assertThat(alerts.next().detail()).isEqualTo("first");

		clock.advance(Duration.ofMinutes(5));
		queue.raise(AlertEvent.WATCHDOG_FAILED, RUN, "second");
		queue.raise(AlertEvent.WATCHDOG_FAILED, RUN, "third");
		clock.advance(OperatorAlertQueue.REPEAT_WINDOW.minus(Duration.ofMinutes(5)).minusMillis(1));
		queue.raise(AlertEvent.WATCHDOG_FAILED, RUN, "fourth");
		// One thread, in order: the marker comes next only when no repeat went before it.
		queue.raise(AlertEvent.RECOVERY_FAILED, null, "marker");
		assertThat(alerts.next().detail()).isEqualTo("marker");
		assertThat(output).contains("WATCHDOG_FAILED (run " + RUN + ") not sent: a repeat", "(3 so far)");

		clock.advance(Duration.ofMillis(1));
		queue.raise(AlertEvent.WATCHDOG_FAILED, RUN, "fifth");
		Report after = alerts.next();
		assertThat(after.event()).isEqualTo(AlertEvent.WATCHDOG_FAILED);
		assertThat(after.detail()).isEqualTo("fifth (3 repeat(s) within 10 min of the last alert were not sent)");

		// The count starts again from the one just taken.
		clock.advance(OperatorAlertQueue.REPEAT_WINDOW);
		queue.raise(AlertEvent.WATCHDOG_FAILED, RUN, "sixth");
		assertThat(alerts.next().detail()).isEqualTo("sixth");
	}

	@Test
	void anotherEventOrAnotherRunWithinTheWindowIsSent() throws InterruptedException {
		FakeAlerts alerts = new FakeAlerts(clock);
		OperatorAlertQueue queue = queue(alerts, OperatorAlertQueue.DRAIN_TIMEOUT);

		queue.raise(AlertEvent.RUN_NEEDS_ATTENTION, RUN, "run, first event");
		queue.raise(AlertEvent.RUN_NEEDS_ATTENTION, OTHER_RUN, "other run");
		queue.raise(AlertEvent.NOTIFICATION_FAILED, RUN, "other event");
		queue.raise(AlertEvent.RECOVERY_FAILED, null, "no run");
		queue.raise(AlertEvent.RECOVERY_FAILED, null, "no run, repeat");
		queue.raise(AlertEvent.ORPHAN_CLEANUP_FAILED, null, "marker");

		List<String> details = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			details.add(alerts.next().detail());
		}
		assertThat(details).containsExactly("run, first event", "other run", "other event", "no run", "marker");
	}

	@Test
	void aThirdEventInAMinuteWaitsForTheEndOfTheMinute() throws InterruptedException {
		FakeAlerts alerts = new FakeAlerts(clock);
		OperatorAlertQueue queue = queue(alerts, OperatorAlertQueue.DRAIN_TIMEOUT);

		queue.raise(AlertEvent.RUN_NEEDS_ATTENTION, RUN, "1");
		queue.raise(AlertEvent.RUN_NEEDS_ATTENTION, OTHER_RUN, "2");
		queue.raise(AlertEvent.RUN_COUNTERS_LOST, RUN, "3");

		assertThat(List.of(alerts.next(), alerts.next(), alerts.next())).extracting(Report::at)
			.containsExactly(T0, T0, T0.plus(Duration.ofMinutes(1)));
		assertThat(sleeps).containsExactly(Duration.ofMinutes(1));
	}

	@Test
	void aClockGoingBackDoesNotHoldTheNextEvent() throws InterruptedException {
		FakeAlerts alerts = new FakeAlerts(clock);
		OperatorAlertQueue queue = queue(alerts, OperatorAlertQueue.DRAIN_TIMEOUT);
		queue.raise(AlertEvent.RUN_NEEDS_ATTENTION, RUN, "1");
		queue.raise(AlertEvent.RUN_NEEDS_ATTENTION, OTHER_RUN, "2");
		alerts.next();
		alerts.next();

		clock.advance(Duration.ofHours(-1));
		queue.raise(AlertEvent.RUN_COUNTERS_LOST, RUN, "3");

		assertThat(alerts.next().at()).isEqualTo(T0.minus(Duration.ofHours(1)));
		assertThat(sleeps).isEmpty();
	}

	@Test
	void aFullQueueDropsTheEventWithAWarnAndDoesNotThrow(CapturedOutput output) throws InterruptedException {
		FakeAlerts alerts = FakeAlerts.hanging(clock);
		OperatorAlertQueue queue = queue(alerts, OperatorAlertQueue.DRAIN_TIMEOUT);
		queue.raise(AlertEvent.RUN_NEEDS_ATTENTION, UUID.randomUUID(), "in flight");
		assertThat(alerts.entered.await(1, TimeUnit.SECONDS)).isTrue();
		for (int i = 0; i < OperatorAlertQueue.CAPACITY; i++) {
			queue.raise(AlertEvent.RUN_NEEDS_ATTENTION, UUID.randomUUID(), "waiting " + i);
		}

		assertThatNoException()
			.isThrownBy(() -> queue.raise(AlertEvent.ORPHAN_CLEANUP_FAILED, RUN, "one too many"));

		assertThat(output).contains("WARN", "ORPHAN_CLEANUP_FAILED (run " + RUN + ") dropped",
				OperatorAlertQueue.CAPACITY + " alerts are already waiting");
		alerts.release.countDown();
		queue.destroy();
		assertThat(alerts.reports).hasSize(OperatorAlertQueue.CAPACITY + 1)
			.extracting(Report::detail)
			.doesNotContain("one too many");
	}

	@Test
	void destroySendsWhatWaitsAndARaiseAfterItIsDroppedQuietly(CapturedOutput output) {
		FakeAlerts alerts = FakeAlerts.hanging(clock);
		OperatorAlertQueue queue = queue(alerts, OperatorAlertQueue.DRAIN_TIMEOUT);
		queue.raise(AlertEvent.RUN_CLOSE_FAILED, RUN, "1");
		queue.raise(AlertEvent.RUN_CLOSE_FAILED, OTHER_RUN, "2");
		queue.raise(AlertEvent.RUN_COUNTERS_LOST, RUN, "3");

		alerts.release.countDown();
		queue.destroy();

		assertThat(alerts.reports).extracting(Report::detail).containsExactly("1", "2", "3");
		assertThatNoException().isThrownBy(() -> queue.raise(AlertEvent.WATCHDOG_FAILED, RUN, "late"));
		assertThat(output).contains("INFO",
				"WATCHDOG_FAILED (run " + RUN + ") dropped: the application is shutting down");
		assertThat(alerts.reports).hasSize(3);
	}

	@Test
	void destroyStopsAfterItsTimeLimitAndNamesWhatItDropped(CapturedOutput output) {
		FakeAlerts alerts = FakeAlerts.hanging(clock);
		OperatorAlertQueue queue = queue(alerts, Duration.ofMillis(100));
		queue.raise(AlertEvent.RUN_CLOSE_FAILED, RUN, "in flight");
		queue.raise(AlertEvent.RECOVERY_FAILED, null, "waiting");

		assertTimeoutPreemptively(Duration.ofSeconds(3), queue::destroy);

		assertThat(output).contains("WARN", "dropped: [RECOVERY_FAILED (no run)]");
		assertThat(alerts.reports).isEmpty();
	}

	@Test
	void logsTheTakenEventAndItsDeliveryWithTheRun(CapturedOutput output) throws InterruptedException {
		FakeAlerts alerts = new FakeAlerts(clock);
		OperatorAlertQueue queue = queue(alerts, OperatorAlertQueue.DRAIN_TIMEOUT);

		queue.raise(AlertEvent.RUN_NEEDS_ATTENTION, RUN, "x");
		alerts.next();
		queue.destroy();

		assertThat(output).contains("INFO", "RUN_NEEDS_ATTENTION (run " + RUN + ") taken",
				"RUN_NEEDS_ATTENTION (run " + RUN + ") reported: DELIVERED");
	}

	private OperatorAlertQueue queue(OperatorAlerts alerts, Duration drainTimeout) {
		OperatorAlertQueue queue = new OperatorAlertQueue(alerts, clock, sleeper, drainTimeout);
		queues.add(queue);
		return queue;
	}

	private record Report(AlertEvent event, @Nullable String detail, Instant at) {
	}

	/** Records each report with the clock's time; a hanging one waits for {@link #release} first. */
	private static final class FakeAlerts implements OperatorAlerts {

		final BlockingQueue<Report> reports = new LinkedBlockingQueue<>();

		final CountDownLatch entered = new CountDownLatch(1);

		final CountDownLatch release;

		private final InstantSource clock;

		FakeAlerts(InstantSource clock) {
			this(clock, new CountDownLatch(0));
		}

		private FakeAlerts(InstantSource clock, CountDownLatch release) {
			this.clock = clock;
			this.release = release;
		}

		static FakeAlerts hanging(InstantSource clock) {
			return new FakeAlerts(clock, new CountDownLatch(1));
		}

		@Override
		public Delivery report(AlertEvent event, @Nullable String detail) {
			entered.countDown();
			try {
				if (!release.await(10, TimeUnit.SECONDS)) {
					return Delivery.FAILED;
				}
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				return Delivery.FAILED;
			}
			reports.add(new Report(event, detail, clock.instant()));
			return Delivery.DELIVERED;
		}

		Report next() throws InterruptedException {
			Report report = reports.poll(5, TimeUnit.SECONDS);
			assertThat(report).as("a report within 5 s").isNotNull();
			return report;
		}

	}

	/** A clock only the test (and the fake sleeper) moves. */
	private static final class TestClock implements InstantSource {

		private final AtomicReference<Instant> now;

		TestClock(Instant start) {
			this.now = new AtomicReference<>(start);
		}

		@Override
		public Instant instant() {
			return now.get();
		}

		void advance(Duration duration) {
			now.updateAndGet(instant -> instant.plus(duration));
		}

	}

}
