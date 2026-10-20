package pl.szczurradar.api.alerts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * The server's {@link OperatorAlerts}, from {@code HEALTHCHECK_PING_URL} ({@link AlertSettings}):
 * {@link HealthchecksAlerts} when it is set, {@link NoOperatorAlerts} otherwise. An invalid value fails
 * the server's start ({@code InvalidSettingsException}: the {@code APPLICATION FAILED TO START} block).
 *
 * <p>Only in the server, like {@code RunWatchdog}: the operator commands start the same context without
 * the web server, so a bad value never stops the pre-deploy {@code migrate}; {@code send-test-alert}
 * reads the setting itself, without Spring.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication
public class AlertsConfiguration {

	private static final Logger log = LoggerFactory.getLogger(AlertsConfiguration.class);

	@Bean
	OperatorAlerts operatorAlerts(Environment environment) {
		AlertSettings settings = AlertSettings.from(environment::getProperty);
		if (!settings.configured()) {
			log.info("Operator alerts: not configured ({} is not set), nothing is sent outside the application",
					AlertSettings.NAME);
			return new NoOperatorAlerts();
		}
		log.info("Operator alerts: sent to the healthchecks.io check in {}", AlertSettings.NAME);
		return new HealthchecksAlerts(settings);
	}

}
