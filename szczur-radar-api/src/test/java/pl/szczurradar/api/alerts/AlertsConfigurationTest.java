package pl.szczurradar.api.alerts;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import pl.szczurradar.api.settings.InvalidSettingsException;

/**
 * {@link AlertsConfiguration}: in the server the bean follows {@code NTFY_URL}, a bad value (of it or of
 * {@code NTFY_TOKEN}) fails the start and the {@link OperatorAlertQueue} sits over it; an operator
 * command's context (not a web application) has neither and starts whatever the value.
 */
class AlertsConfigurationTest {

	private static final String SECRET_TOPIC = "szczur-radar-0f6a2a6c2bd54c1e9a6e3f2c1d5e8b7a";

	private static final String SECRET_TOKEN = "tk_0f6a2a6c2bd54c1e9a6e3f2c1d5e8b7a";

	private final WebApplicationContextRunner server = new WebApplicationContextRunner()
		.withUserConfiguration(AlertsConfiguration.class)
		// Empty values override a real NTFY_URL / NTFY_TOKEN of the machine.
		.withPropertyValues(AlertSettings.NAME + "=", AlertSettings.TOKEN_NAME + "=");

	private final ApplicationContextRunner command = new ApplicationContextRunner()
		.withUserConfiguration(AlertsConfiguration.class)
		.withPropertyValues(AlertSettings.NAME + "=", AlertSettings.TOKEN_NAME + "=");

	@Test
	void withoutTheVariableTheServerSendsNothing() {
		server.run(started -> {
			assertThat(started).hasNotFailed();
			assertThat(started).getBean(OperatorAlerts.class).isInstanceOf(NoOperatorAlerts.class);
		});
	}

	@Test
	void theServerHasTheQueueOfTheErrorPaths() {
		server.run(started -> assertThat(started).hasSingleBean(OperatorAlertQueue.class));
	}

	@Test
	void withATopicUrlTheServerReportsToNtfy() {
		server.withPropertyValues(AlertSettings.NAME + "=https://ntfy.sh/" + SECRET_TOPIC)
			.run(started -> assertThat(started).getBean(OperatorAlerts.class).isInstanceOf(NtfyAlerts.class));
		server
			.withPropertyValues(AlertSettings.NAME + "=https://ntfy.sh/" + SECRET_TOPIC,
					AlertSettings.TOKEN_NAME + "=" + SECRET_TOKEN)
			.run(started -> assertThat(started).getBean(OperatorAlerts.class).isInstanceOf(NtfyAlerts.class));
	}

	@Test
	void anInvalidValueFailsTheServerStartWithoutRepeatingIt() {
		server.withPropertyValues(AlertSettings.NAME + "=http://ntfy.sh/" + SECRET_TOPIC).run(started -> {
			assertThat(started).hasFailed();
			assertThat(started.getStartupFailure()).rootCause()
				.isInstanceOf(InvalidSettingsException.class)
				.hasMessageContaining(AlertSettings.NAME)
				.hasMessageNotContaining(SECRET_TOPIC);
		});
		server.withPropertyValues(AlertSettings.TOKEN_NAME + "=" + SECRET_TOKEN).run(started -> {
			assertThat(started).hasFailed();
			assertThat(started.getStartupFailure()).rootCause()
				.isInstanceOf(InvalidSettingsException.class)
				.hasMessageContaining(AlertSettings.TOKEN_NAME)
				.hasMessageNotContaining(SECRET_TOKEN);
		});
	}

	@Test
	void anOperatorCommandsContextHasNoSenderAndIgnoresAnInvalidValue() {
		command.run(started -> {
			assertThat(started).hasNotFailed();
			assertThat(started).doesNotHaveBean(OperatorAlerts.class);
			assertThat(started).doesNotHaveBean(OperatorAlertQueue.class);
		});
		command.withPropertyValues(AlertSettings.NAME + "=http://ntfy.sh/" + SECRET_TOPIC).run(started -> {
			assertThat(started).hasNotFailed();
			assertThat(started).doesNotHaveBean(OperatorAlerts.class);
			assertThat(started).doesNotHaveBean(OperatorAlertQueue.class);
		});
	}

}
