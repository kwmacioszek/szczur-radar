package pl.szczurradar.api.alerts;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import pl.szczurradar.api.settings.InvalidSettingsException;

/**
 * {@link AlertsConfiguration}: in the server the bean follows {@code HEALTHCHECK_PING_URL}, a bad value
 * fails the start and the {@link OperatorAlertQueue} sits over it; an operator command's context (not a
 * web application) has neither and starts whatever the value.
 */
class AlertsConfigurationTest {

	private static final String SECRET_UUID = "0f6a2a6c-2bd5-4c1e-9a6e-3f2c1d5e8b7a";

	private final WebApplicationContextRunner server = new WebApplicationContextRunner()
		.withUserConfiguration(AlertsConfiguration.class)
		// An empty value overrides a real HEALTHCHECK_PING_URL of the machine.
		.withPropertyValues(AlertSettings.NAME + "=");

	private final ApplicationContextRunner command = new ApplicationContextRunner()
		.withUserConfiguration(AlertsConfiguration.class)
		.withPropertyValues(AlertSettings.NAME + "=");

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
	void withAPingUrlTheServerReportsToHealthchecks() {
		server.withPropertyValues(AlertSettings.NAME + "=https://hc-ping.com/" + SECRET_UUID)
			.run(started -> assertThat(started).getBean(OperatorAlerts.class).isInstanceOf(HealthchecksAlerts.class));
	}

	@Test
	void anInvalidValueFailsTheServerStartWithoutRepeatingIt() {
		server.withPropertyValues(AlertSettings.NAME + "=http://hc-ping.com/" + SECRET_UUID).run(started -> {
			assertThat(started).hasFailed();
			assertThat(started.getStartupFailure()).rootCause()
				.isInstanceOf(InvalidSettingsException.class)
				.hasMessageContaining(AlertSettings.NAME)
				.hasMessageNotContaining(SECRET_UUID);
		});
	}

	@Test
	void anOperatorCommandsContextHasNoSenderAndIgnoresAnInvalidValue() {
		command.run(started -> {
			assertThat(started).hasNotFailed();
			assertThat(started).doesNotHaveBean(OperatorAlerts.class);
			assertThat(started).doesNotHaveBean(OperatorAlertQueue.class);
		});
		command.withPropertyValues(AlertSettings.NAME + "=http://hc-ping.com/" + SECRET_UUID).run(started -> {
			assertThat(started).hasNotFailed();
			assertThat(started).doesNotHaveBean(OperatorAlerts.class);
			assertThat(started).doesNotHaveBean(OperatorAlertQueue.class);
		});
	}

}
