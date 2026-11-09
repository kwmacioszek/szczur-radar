package pl.szczurradar.api.alerts;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import pl.szczurradar.api.alerts.LocalNtfyServer.Request;
import pl.szczurradar.api.alerts.OperatorAlerts.Delivery;

/**
 * {@link NtfyAlerts} against a local server: one {@code POST} to the topic with the title, the priority
 * and the body in UTF-8, the bearer token only when set, the body cut to 4096 bytes on a code point
 * boundary; every failure is {@link Delivery#FAILED} after one request, never an exception; the address
 * (its topic) and the token never in the log, a message or {@code toString}.
 */
@ExtendWith(OutputCaptureExtension.class)
class NtfyAlertsTest {

	private static final Duration SHORT = Duration.ofMillis(500);

	private static final String TOKEN = "tk_s3cr3tT0k3nNeverShown";

	/** Three bytes of UTF-8. */
	private static final String EURO = "€";

	/** Four bytes of UTF-8, a surrogate pair in Java. */
	private static final String CHART = "📈";

	@Test
	void reportsTheEventAsOneMessageWithTitlePriorityAndBody(CapturedOutput output) {
		try (LocalNtfyServer server = new LocalNtfyServer(request -> 200)) {
			Delivery delivery = alerts(server.topicUrl()).report(AlertEvent.TEST_SIGNAL, "send-test-alert from box");

			assertThat(delivery).isEqualTo(Delivery.DELIVERED);
			assertThat(server.requests()).hasSize(1);
			Request request = server.requests().getFirst();
			assertThat(request.method()).isEqualTo("POST");
			assertThat(request.path()).isEqualTo("/" + server.topic);
			assertThat(request.header("Title")).isEqualTo("Szczur Radar TEST_SIGNAL");
			assertThat(request.header("Priority")).isEqualTo("3");
			assertThat(request.header("Content-Type")).isEqualTo("text/plain; charset=utf-8");
			assertThat(request.header("Authorization")).isNull();
			assertThat(request.body()).isEqualTo(
					"TEST_SIGNAL (" + AlertEvent.TEST_SIGNAL.description() + "): send-test-alert from box");
			assertThat(output).contains("INFO", "TEST_SIGNAL delivered to ntfy").doesNotContain(server.topic);
		}
	}

	@Test
	void anErrorEventHasTheHighPriority() {
		try (LocalNtfyServer server = new LocalNtfyServer(request -> 200)) {
			alerts(server.topicUrl()).report(AlertEvent.RUN_NEEDS_ATTENTION, "dead 1");

			assertThat(server.requests().getFirst().header("Priority")).isEqualTo("4");
			assertThat(server.requests().getFirst().header("Title")).isEqualTo("Szczur Radar RUN_NEEDS_ATTENTION");
		}
	}

	@Test
	void withATokenTheRequestCarriesItAsBearerAndTheLogDoesNot(CapturedOutput output) {
		try (LocalNtfyServer server = new LocalNtfyServer(request -> 200)) {
			Delivery delivery = new NtfyAlerts(server.topicUrl(), TOKEN, SHORT, SHORT).report(AlertEvent.TEST_SIGNAL,
					"x");

			assertThat(delivery).isEqualTo(Delivery.DELIVERED);
			assertThat(server.requests().getFirst().header("Authorization")).isEqualTo("Bearer " + TOKEN);
			assertThat(output).doesNotContain(TOKEN, server.topic);
		}
	}

	@Test
	void theSenderFromSettingsHidesTheAddressAndTheToken() {
		// AlertSettings refuses http://, so the local server's address cannot pass through it: the
		// constructor from settings is checked with a real-shaped https address as far as toString; the
		// token's way to the header is checked through the package constructor above.
		AlertSettings settings = AlertSettings.from(Map.of(AlertSettings.NAME, "https://ntfy.sh/" + TOKEN + "-topic",
				AlertSettings.TOKEN_NAME, TOKEN)::get);

		assertThat(new NtfyAlerts(settings)).hasToString("NtfyAlerts[<hidden>]");
	}

	@Test
	void aTrailingSlashOfTheTopicUrlIsDropped() {
		try (LocalNtfyServer server = new LocalNtfyServer(request -> 200)) {
			alerts(URI.create(server.topicUrl() + "/")).report(AlertEvent.TEST_SIGNAL, "x");

			assertThat(server.requests()).extracting(Request::path).containsExactly("/" + server.topic);
		}
	}

	@Test
	void theBodyIsSentInUtf8() {
		try (LocalNtfyServer server = new LocalNtfyServer(request -> 200)) {
			alerts(server.topicUrl()).report(AlertEvent.TEST_SIGNAL, "zażółć " + EURO + " " + CHART);

			assertThat(server.requests().getFirst().body()).endsWith("): zażółć " + EURO + " " + CHART);
		}
	}

	@Test
	void aLongDetailIsCutTo4096Bytes() {
		try (LocalNtfyServer server = new LocalNtfyServer(request -> 200)) {
			alerts(server.topicUrl()).report(AlertEvent.TEST_SIGNAL, "x".repeat(5000));

			String body = server.requests().getFirst().body();
			assertThat(utf8Bytes(body)).isEqualTo(NtfyAlerts.MAX_BODY_BYTES);
			assertThat(body).startsWith("TEST_SIGNAL (").endsWith("x…");
		}
	}

	@Test
	void theCutNeverSplitsAThreeByteCharacter() {
		int prefix = utf8Bytes(NtfyAlerts.body(AlertEvent.TEST_SIGNAL, ""));
		int budget = NtfyAlerts.MAX_BODY_BYTES - utf8Bytes("…") - prefix;

		// The character would end one byte past the budget: it goes, the cut falls before it, and the body
		// is the two bytes of budget it left short of the limit.
		String straddling = NtfyAlerts.body(AlertEvent.TEST_SIGNAL, "y".repeat(budget - 2) + EURO + "z".repeat(10));
		assertThat(straddling).endsWith("y…").doesNotContain(EURO);
		assertThat(utf8Bytes(straddling)).isEqualTo(NtfyAlerts.MAX_BODY_BYTES - 2);

		// The character ends exactly on the budget: it stays.
		String fitting = NtfyAlerts.body(AlertEvent.TEST_SIGNAL, "y".repeat(budget - 3) + EURO + "z".repeat(10));
		assertThat(fitting).endsWith(EURO + "…");
		assertThat(utf8Bytes(fitting)).isEqualTo(NtfyAlerts.MAX_BODY_BYTES);
	}

	@Test
	void theCutNeverSplitsAFourByteCharacterOrItsSurrogatePair() {
		int prefix = utf8Bytes(NtfyAlerts.body(AlertEvent.TEST_SIGNAL, ""));
		int budget = NtfyAlerts.MAX_BODY_BYTES - utf8Bytes("…") - prefix;

		for (int over = 1; over <= 3; over++) {
			String straddling = NtfyAlerts.body(AlertEvent.TEST_SIGNAL,
					"y".repeat(budget - 4 + over) + CHART + "z".repeat(10));
			assertThat(straddling).endsWith("y…").doesNotContain(CHART);
			assertThat(straddling.chars().noneMatch(c -> Character.isSurrogate((char) c))).isTrue();
			assertThat(utf8Bytes(straddling)).isLessThan(NtfyAlerts.MAX_BODY_BYTES);
		}
		String fitting = NtfyAlerts.body(AlertEvent.TEST_SIGNAL, "y".repeat(budget - 4) + CHART + "z".repeat(10));
		assertThat(fitting).endsWith(CHART + "…");
		assertThat(utf8Bytes(fitting)).isEqualTo(NtfyAlerts.MAX_BODY_BYTES);
		assertThat(NtfyAlerts.body(AlertEvent.TEST_SIGNAL, null)).isEqualTo(NtfyAlerts.body(AlertEvent.TEST_SIGNAL, ""));
	}

	@Test
	void anErrorStatusIsFailedAfterOneRequestWithTheStatusInTheLog(CapturedOutput output) {
		for (int status : new int[] { 429, 401, 403, 500, 404 }) {
			try (LocalNtfyServer server = new LocalNtfyServer(request -> status)) {
				Delivery delivery = new NtfyAlerts(server.topicUrl(), TOKEN, SHORT, SHORT).report(AlertEvent.TEST_SIGNAL,
						"x");

				assertThat(delivery).isEqualTo(Delivery.FAILED);
				assertThat(server.requests()).hasSize(1);
				assertThat(output).contains("WARN", "TEST_SIGNAL not delivered", "HTTP " + status)
					.doesNotContain(server.topic, TOKEN);
			}
		}
	}

	@Test
	void anUnreachableServerIsFailed(CapturedOutput output) {
		LocalNtfyServer server = new LocalNtfyServer(request -> 200);
		URI topicUrl = server.topicUrl();
		server.close();

		assertThat(alerts(topicUrl).report(AlertEvent.TEST_SIGNAL, "x")).isEqualTo(Delivery.FAILED);
		assertThat(output).contains("WARN", "TEST_SIGNAL not delivered", "ConnectException")
			.doesNotContain(server.topic, topicUrl.toString());
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
			URI topicUrl = URI.create("http://127.0.0.1:" + socket.getLocalPort() + "/no-answer-topic");

			assertThat(alerts(topicUrl).report(AlertEvent.TEST_SIGNAL, "x")).isEqualTo(Delivery.FAILED);
			assertThat(output).contains("WARN", "TEST_SIGNAL not delivered").doesNotContain("no-answer-topic");
		}
		finally {
			executor.shutdownNow();
		}
	}

	@Test
	void aSlowAnswerTimesOutAsFailedAfterOneRequest(CapturedOutput output) {
		try (LocalNtfyServer server = new LocalNtfyServer(request -> {
			try {
				Thread.sleep(5_000);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
			return 200;
		})) {
			assertThat(alerts(server.topicUrl()).report(AlertEvent.TEST_SIGNAL, "x")).isEqualTo(Delivery.FAILED);

			assertThat(server.requests()).hasSize(1);
			assertThat(output).contains("WARN", "HttpTimeoutException").doesNotContain(server.topic);
		}
	}

	@Test
	void anInterruptIsFailedAndTheFlagIsKept(CapturedOutput output) {
		try (LocalNtfyServer server = new LocalNtfyServer(request -> 200)) {
			Thread.currentThread().interrupt();
			Delivery delivery = alerts(server.topicUrl()).report(AlertEvent.TEST_SIGNAL, "x");

			assertThat(Thread.interrupted()).isTrue();
			assertThat(delivery).isEqualTo(Delivery.FAILED);
			assertThat(output).contains("WARN", "TEST_SIGNAL not delivered: interrupted");
		}
	}

	@Test
	void toStringHidesTheAddressAndTheToken() {
		try (LocalNtfyServer server = new LocalNtfyServer(request -> 200)) {
			assertThat(alerts(server.topicUrl())).hasToString("NtfyAlerts[<hidden>]");
			assertThat(new NtfyAlerts(server.topicUrl(), TOKEN, SHORT, SHORT)).hasToString("NtfyAlerts[<hidden>]");
		}
	}

	private static NtfyAlerts alerts(URI topicUrl) {
		return new NtfyAlerts(topicUrl, null, SHORT, SHORT);
	}

	private static int utf8Bytes(String text) {
		return text.getBytes(StandardCharsets.UTF_8).length;
	}

}
