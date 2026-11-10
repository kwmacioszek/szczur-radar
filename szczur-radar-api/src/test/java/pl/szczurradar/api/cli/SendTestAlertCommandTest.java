package pl.szczurradar.api.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import pl.szczurradar.api.alerts.AlertEvent;
import pl.szczurradar.api.alerts.AlertSettings;
import pl.szczurradar.api.alerts.OperatorAlerts;
import pl.szczurradar.api.alerts.OperatorAlerts.Delivery;

/**
 * {@code send-test-alert} without Spring: the exit code for each delivery, a usage error without a (valid)
 * {@code NTFY_URL} or with a bad {@code NTFY_TOKEN} before anything is sent, and neither secret ever printed.
 */
class SendTestAlertCommandTest {

	private static final String SECRET_TOPIC = "szczur-radar-9d3c1b2a7e6f4a5b8c9d0e1f2a3b4c5d";

	private static final String SECRET_TOKEN = "tk_9d3c1b2a7e6f4a5b8c9d0e1f2a3b4c5d";

	private static final String TOPIC_URL = "https://ntfy.sh/" + SECRET_TOPIC;

	private final ByteArrayOutputStream outBytes = new ByteArrayOutputStream();

	private final ByteArrayOutputStream errBytes = new ByteArrayOutputStream();

	private final Map<String, String> environment = new HashMap<>(Map.of(AlertSettings.NAME, TOPIC_URL));

	private final List<String> reports = new ArrayList<>();

	@Test
	void aDeliveredTestSignalExitsZero() {
		environment.put("HOSTNAME", "api-container");

		assertThat(command(Delivery.DELIVERED).run(new String[0])).isEqualTo(ExitCode.OK);

		assertThat(reports).containsExactly(AlertEvent.TEST_SIGNAL + ": send-test-alert from api-container");
		assertThat(stdout()).contains("Test alert delivered", AlertSettings.NAME, "Szczur Radar TEST_SIGNAL");
		assertThat(stderr()).isEmpty();
		assertThat(stdout() + stderr()).doesNotContain(SECRET_TOPIC);
	}

	@Test
	void withATokenTheDeliveryPrintsNeitherSecret() {
		environment.put(AlertSettings.TOKEN_NAME, SECRET_TOKEN);

		assertThat(command(Delivery.DELIVERED).run(new String[0])).isEqualTo(ExitCode.OK);

		assertThat(reports).hasSize(1);
		assertThat(stdout() + stderr()).doesNotContain(SECRET_TOPIC, SECRET_TOKEN);
	}

	@Test
	void withoutAHostNameTheDetailSaysOperator() {
		command(Delivery.DELIVERED).run(new String[0]);

		assertThat(reports).containsExactly(AlertEvent.TEST_SIGNAL + ": send-test-alert from operator");
	}

	@Test
	void aFailedDeliveryExitsOne() {
		assertThat(command(Delivery.FAILED).run(new String[0])).isEqualTo(ExitCode.FAILED);

		assertThat(stderr()).contains("Test alert not delivered");
		assertThat(stdout() + stderr()).doesNotContain(SECRET_TOPIC);
	}

	@Test
	void withoutTheVariableItIsAUsageErrorAndNothingIsSent() {
		environment.remove(AlertSettings.NAME);

		assertThat(command(Delivery.DELIVERED).run(new String[0])).isEqualTo(ExitCode.USAGE);

		assertThat(reports).isEmpty();
		assertThat(stderr()).contains("not configured", "set " + AlertSettings.NAME, AlertSettings.TOKEN_NAME);
	}

	@Test
	void anInvalidValueIsAUsageErrorWithoutRepeatingIt() {
		environment.put(AlertSettings.NAME, "http://ntfy.sh/" + SECRET_TOPIC);

		assertThat(command(Delivery.DELIVERED).run(new String[0])).isEqualTo(ExitCode.USAGE);

		assertThat(reports).isEmpty();
		assertThat(stderr()).startsWith("send-test-alert: invalid configuration: Invalid " + AlertSettings.NAME)
			.doesNotContain(SECRET_TOPIC);
	}

	@Test
	void anInvalidTokenIsAUsageErrorWithoutRepeatingIt() {
		environment.put(AlertSettings.TOKEN_NAME, SECRET_TOKEN + " with space");

		assertThat(command(Delivery.DELIVERED).run(new String[0])).isEqualTo(ExitCode.USAGE);

		assertThat(reports).isEmpty();
		assertThat(stderr()).startsWith("send-test-alert: invalid configuration: Invalid " + AlertSettings.TOKEN_NAME)
			.doesNotContain(SECRET_TOKEN, SECRET_TOPIC);
	}

	@Test
	void aTokenWithoutTheAddressIsAUsageErrorWithoutRepeatingIt() {
		environment.remove(AlertSettings.NAME);
		environment.put(AlertSettings.TOKEN_NAME, SECRET_TOKEN);

		assertThat(command(Delivery.DELIVERED).run(new String[0])).isEqualTo(ExitCode.USAGE);

		assertThat(reports).isEmpty();
		assertThat(stderr()).startsWith("send-test-alert: invalid configuration: Invalid " + AlertSettings.TOKEN_NAME)
			.contains(AlertSettings.NAME)
			.doesNotContain(SECRET_TOKEN);
	}

	@Test
	void anArgumentIsAUsageError() {
		assertThat(command(Delivery.DELIVERED).run(new String[] { "--email", "x@example.com" }))
			.isEqualTo(ExitCode.USAGE);

		assertThat(reports).isEmpty();
		assertThat(stderr()).contains("usage: send-test-alert");
	}

	@Test
	void aSenderWithoutConfigurationIsAUsageError() {
		assertThat(command(Delivery.NOT_CONFIGURED).run(new String[0])).isEqualTo(ExitCode.USAGE);
	}

	private SendTestAlertCommand command(Delivery delivery) {
		OperatorAlerts sender = (event, detail) -> {
			reports.add(event + ": " + detail);
			return delivery;
		};
		return new SendTestAlertCommand(environment::get, settings -> sender,
				new PrintStream(outBytes, true, StandardCharsets.UTF_8),
				new PrintStream(errBytes, true, StandardCharsets.UTF_8));
	}

	private String stdout() {
		return outBytes.toString(StandardCharsets.UTF_8);
	}

	private String stderr() {
		return errBytes.toString(StandardCharsets.UTF_8);
	}

}
