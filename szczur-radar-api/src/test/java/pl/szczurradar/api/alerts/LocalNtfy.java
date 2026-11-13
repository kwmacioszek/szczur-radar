package pl.szczurradar.api.alerts;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * The operator alert channel of the scenarios ({@code scenario/PrEnvScenariosIT}), on its real way: the
 * {@link OperatorAlertQueue} on its own thread → {@link NtfyAlerts} → an HTTP {@code POST} to a
 * {@link LocalNtfyServer} in place of the ntfy topic, with a test token as its bearer. {@code NTFY_URL} takes
 * only {@code https} ({@link AlertSettings}), so the local server's address comes in through the package
 * constructors, not through the setting; the context sets {@code NTFY_URL} and {@code NTFY_TOKEN} empty.
 *
 * <p>Its beans are {@code @Primary} under names of their own ({@value #ALERTS_BEAN}, {@value #QUEUE_BEAN}):
 * overriding a bean definition is off, so the production {@code operatorAlerts} and {@code operatorAlertQueue}
 * stay in the context, unused. Every injection point of the queue gets this one — {@code PipelineRunService},
 * {@code RunNotifications}, {@code InternalPipelineRunController}, {@code ConcurrentAssessmentDispatcher}
 * through their {@code ObjectProvider}, {@code RunWatchdog} and {@code PipelineStartupRecovery} directly.
 *
 * <p>The queue's repeat window and its limit of {@value OperatorAlertQueue#MAX_PER_MINUTE} reports a minute run
 * on a virtual clock that only its wait for the limit moves (as in {@code OperatorAlertQueueTest}): a third
 * event goes at once, not a minute later.
 *
 * <p>The queue does not say when it is idle, and its {@code taken} line is logged before it sends. So
 * {@link #reported()} counts the reports that returned (a decorator around {@link NtfyAlerts}): once it
 * equals the scenario's {@code Operator alert … taken} lines, the queue has sent everything it took. State is
 * static, as {@code LocalReddit}'s, and {@link #clear()} empties it before a scenario.
 */
@TestConfiguration(proxyBeanMethods = false)
public class LocalNtfy {

	/** The name of the context's {@link OperatorAlerts}: not {@code operatorAlerts}, which stays. */
	public static final String ALERTS_BEAN = "localNtfyAlerts";

	/** The name of the context's {@link OperatorAlertQueue}: not {@code operatorAlertQueue}, which stays. */
	public static final String QUEUE_BEAN = "localNtfyAlertQueue";

	/** The test token the sender carries as {@code Authorization: Bearer}; never a real one. */
	public static final String TOKEN = "tk_scenarioLocalNtfyTestToken";

	/** One message the topic received. */
	public record Message(String title, String priority, @Nullable String authorization, String body) {
	}

	private static final LocalNtfyServer SERVER = new LocalNtfyServer(request -> 200);

	/** The reports {@link NtfyAlerts} returned from since the last {@link #clear()}. */
	private static final AtomicInteger REPORTED = new AtomicInteger();

	/** The queue's virtual time; only its wait for the limit moves it. */
	private static final AtomicReference<Instant> NOW = new AtomicReference<>(Instant.parse("2026-10-29T10:00:00Z"));

	@Bean(ALERTS_BEAN)
	@Primary
	OperatorAlerts localNtfyAlerts() {
		NtfyAlerts ntfy = new NtfyAlerts(SERVER.topicUrl(), TOKEN, NtfyAlerts.CONNECT_TIMEOUT,
				NtfyAlerts.REQUEST_TIMEOUT);
		return (event, detail) -> {
			try {
				return ntfy.report(event, detail);
			}
			finally {
				REPORTED.incrementAndGet();
			}
		};
	}

	@Bean(QUEUE_BEAN)
	@Primary
	OperatorAlertQueue localNtfyAlertQueue(OperatorAlerts alerts) {
		InstantSource clock = NOW::get;
		OperatorAlertQueue.Sleeper sleeper = duration -> NOW.updateAndGet(now -> now.plus(duration));
		return new OperatorAlertQueue(alerts, clock, sleeper, OperatorAlertQueue.DRAIN_TIMEOUT);
	}

	/** Every message the topic received since the last {@link #clear()}, in order. */
	public static List<Message> messages() {
		return SERVER.requests()
			.stream()
			.map(request -> new Message(request.header("Title"), request.header("Priority"),
					request.header("Authorization"), request.body()))
			.toList();
	}

	/** The messages of {@code event} whose text names {@code runId}. */
	public static List<Message> messages(AlertEvent event, UUID runId) {
		return messages(event, runId.toString());
	}

	/** The messages of {@code event} whose text contains {@code text}. */
	public static List<Message> messages(AlertEvent event, String text) {
		return messages().stream()
			.filter(message -> NtfyAlerts.title(event).equals(message.title()) && message.body().contains(text))
			.toList();
	}

	/** Waits up to {@code timeout} for a message of {@code event} naming {@code runId} and returns the first. */
	public static Message awaitMessage(AlertEvent event, UUID runId, Duration timeout) {
		return awaitMessage(event, runId.toString(), timeout);
	}

	/** Waits up to {@code timeout} for a message of {@code event} containing {@code text} and returns the first. */
	public static Message awaitMessage(AlertEvent event, String text, Duration timeout) {
		long deadline = System.nanoTime() + timeout.toNanos();
		while (true) {
			List<Message> found = messages(event, text);
			if (!found.isEmpty()) {
				return found.getFirst();
			}
			if (System.nanoTime() > deadline) {
				throw new AssertionError("no " + NtfyAlerts.title(event) + " message containing " + text + " within "
						+ timeout.toMillis() + " ms; received: " + messages().stream().map(Message::title).toList());
			}
			try {
				Thread.sleep(50);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(ex);
			}
		}
	}

	/** The local topic's address, a secret in production: a message's text never carries it. */
	public static URI topicUrl() {
		return SERVER.topicUrl();
	}

	/** How many reports returned since the last {@link #clear()}: delivered or not, each one the queue took. */
	public static int reported() {
		return REPORTED.get();
	}

	/**
	 * Forgets the messages and the count of reports, and moves the queue's virtual time past its repeat and
	 * rate windows, so an event the last scenario raised is no repeat in the next one, whatever their order.
	 */
	public static void clear() {
		SERVER.clear();
		REPORTED.set(0);
		NOW.updateAndGet(now -> now.plus(OperatorAlertQueue.REPEAT_WINDOW).plus(OperatorAlertQueue.RATE_WINDOW));
	}

}
