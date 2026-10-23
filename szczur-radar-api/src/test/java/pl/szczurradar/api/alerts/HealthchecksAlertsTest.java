package pl.szczurradar.api.alerts;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import pl.szczurradar.api.alerts.LocalPingServer.Request;
import pl.szczurradar.api.alerts.OperatorAlerts.Delivery;

/**
 * {@link HealthchecksAlerts} against a local server: the failure ping with the event, then the success
 * ping; every failure is {@link Delivery#FAILED} after one request each, never an exception; the URL
 * (and its UUID) never in the log, a message or {@code toString}.
 */
@ExtendWith(OutputCaptureExtension.class)
class HealthchecksAlertsTest {

	private static final Duration SHORT = Duration.ofMillis(500);

	@Test
	void reportsTheEventToFailThenPingsSuccess(CapturedOutput output) {
		try (LocalPingServer server = new LocalPingServer(request -> 200)) {
			Delivery delivery = alerts(server.pingUrl()).report(AlertEvent.TEST_SIGNAL, "send-test-alert from box");

			assertThat(delivery).isEqualTo(Delivery.DELIVERED);
			assertThat(server.requests()).extracting(Request::method).containsExactly("POST", "POST");
			assertThat(server.requests()).extracting(Request::path)
				.containsExactly("/" + server.uuid + "/fail", "/" + server.uuid);
			Request fail = server.requests().getFirst();
			assertThat(fail.body()).isEqualTo(
					"TEST_SIGNAL (" + AlertEvent.TEST_SIGNAL.description() + "): send-test-alert from box");
			assertThat(fail.contentType()).startsWith("text/plain");
			assertThat(server.requests().getLast().body()).isEqualTo("ok");
			assertThat(output).contains("INFO", "TEST_SIGNAL delivered").doesNotContain(server.uuid);
		}
	}

	@Test
	void aTrailingSlashOfThePingUrlIsDropped() {
		try (LocalPingServer server = new LocalPingServer(request -> 200)) {
			alerts(URI.create(server.pingUrl() + "/")).report(AlertEvent.TEST_SIGNAL, "x");

			assertThat(server.requests()).extracting(Request::path)
				.containsExactly("/" + server.uuid + "/fail", "/" + server.uuid);
		}
	}

	@Test
	void aLongDetailIsCutTo1000Characters() {
		try (LocalPingServer server = new LocalPingServer(request -> 200)) {
			alerts(server.pingUrl()).report(AlertEvent.TEST_SIGNAL, "x".repeat(2000));

			String body = server.requests().getFirst().body();
			assertThat(body).hasSize(HealthchecksAlerts.MAX_BODY).startsWith("TEST_SIGNAL (").endsWith("x…");
		}
	}

	@Test
	void theCutNeverSplitsASurrogatePair() {
		String prefix = HealthchecksAlerts.body(AlertEvent.TEST_SIGNAL, "");
		// The emoji's high surrogate is the last character before the cut.
		String detail = "y".repeat(HealthchecksAlerts.MAX_BODY - 2 - prefix.length()) + "📈" + "z".repeat(10);

		String body = HealthchecksAlerts.body(AlertEvent.TEST_SIGNAL, detail);

		assertThat(body).hasSize(HealthchecksAlerts.MAX_BODY - 1).endsWith("y…");
		assertThat(HealthchecksAlerts.body(AlertEvent.TEST_SIGNAL, null)).isEqualTo(prefix);
	}

	@Test
	void anErrorStatusOfTheFailurePingIsFailedWithoutTheSuccessPing(CapturedOutput output) {
		for (int status : new int[] { 429, 500, 404 }) {
			try (LocalPingServer server = new LocalPingServer(request -> status)) {
				assertThat(alerts(server.pingUrl()).report(AlertEvent.TEST_SIGNAL, "x")).isEqualTo(Delivery.FAILED);

				assertThat(server.requests()).hasSize(1);
				assertThat(output).contains("WARN", "TEST_SIGNAL not delivered", "HTTP " + status)
					.doesNotContain(server.uuid);
			}
		}
	}

	@Test
	void anErrorStatusOfTheSuccessPingIsFailed(CapturedOutput output) {
		try (LocalPingServer server = new LocalPingServer(request -> request.path().endsWith("/fail") ? 200 : 500)) {
			assertThat(alerts(server.pingUrl()).report(AlertEvent.TEST_SIGNAL, "x")).isEqualTo(Delivery.FAILED);

			assertThat(server.requests()).hasSize(2);
			assertThat(output).contains("WARN", "HTTP 500 to the success ping", "may stay down")
				.doesNotContain(server.uuid);
		}
	}

	@Test
	void anUnreachableServerIsFailed(CapturedOutput output) {
		LocalPingServer server = new LocalPingServer(request -> 200);
		URI pingUrl = server.pingUrl();
		server.close();

		assertThat(alerts(pingUrl).report(AlertEvent.TEST_SIGNAL, "x")).isEqualTo(Delivery.FAILED);
		assertThat(output).contains("WARN", "TEST_SIGNAL not delivered", "ConnectException")
			.doesNotContain(server.uuid, pingUrl.toString());
	}

	@Test
	void aConnectionClosedWithoutAnAnswerIsFailed(CapturedOutput output) throws IOException {
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
			executor.submit(() -> {
				while (!socket.isClosed()) {
					try (Socket client = socket.accept()) {
						client.getInputStream().read();
					}
				}
				return null;
			});
			URI pingUrl = URI.create("http://127.0.0.1:" + socket.getLocalPort() + "/no-answer-check");

			assertThat(alerts(pingUrl).report(AlertEvent.TEST_SIGNAL, "x")).isEqualTo(Delivery.FAILED);
			assertThat(output).contains("WARN", "TEST_SIGNAL not delivered").doesNotContain("no-answer-check");
		}
		finally {
			executor.shutdownNow();
		}
	}

	@Test
	void aSlowAnswerTimesOutAsFailedAfterOneRequest(CapturedOutput output) {
		try (LocalPingServer server = new LocalPingServer(request -> {
			try {
				Thread.sleep(5_000);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
			return 200;
		})) {
			assertThat(alerts(server.pingUrl()).report(AlertEvent.TEST_SIGNAL, "x")).isEqualTo(Delivery.FAILED);

			assertThat(server.requests()).hasSize(1);
			assertThat(output).contains("WARN", "HttpTimeoutException").doesNotContain(server.uuid);
		}
	}

	@Test
	void anInterruptIsFailedAndTheFlagIsKept(CapturedOutput output) {
		try (LocalPingServer server = new LocalPingServer(request -> 200)) {
			Thread.currentThread().interrupt();
			Delivery delivery = alerts(server.pingUrl()).report(AlertEvent.TEST_SIGNAL, "x");

			assertThat(Thread.interrupted()).isTrue();
			assertThat(delivery).isEqualTo(Delivery.FAILED);
			assertThat(output).contains("WARN", "TEST_SIGNAL not delivered: interrupted");
		}
	}

	@Test
	void toStringHidesTheUrl() {
		try (LocalPingServer server = new LocalPingServer(request -> 200)) {
			assertThat(alerts(server.pingUrl())).hasToString("HealthchecksAlerts[<hidden>]");
			assertThat(new HealthchecksAlerts(AlertSettings.from(name -> "https://hc-ping.com/" + server.uuid)))
				.hasToString("HealthchecksAlerts[<hidden>]");
		}
	}

	private static HealthchecksAlerts alerts(URI pingUrl) {
		return new HealthchecksAlerts(pingUrl, SHORT, SHORT);
	}

}
