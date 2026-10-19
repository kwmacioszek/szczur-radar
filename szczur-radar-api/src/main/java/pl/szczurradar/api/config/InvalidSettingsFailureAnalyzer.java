package pl.szczurradar.api.config;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

import pl.szczurradar.api.settings.InvalidSettingsException;

/**
 * Turns an {@link InvalidSettingsException} anywhere in a failed start's cause chain into Spring Boot's
 * {@code APPLICATION FAILED TO START} block: the problems as the description, what to do as the action,
 * and the stack trace only at DEBUG. The same for the server and every operator command, whether the
 * setting failed before the context ({@link LlmConfigurationCheck}) or in a bean (e.g. {@code RunWatchdog}).
 *
 * <p>Registered in {@code META-INF/spring.factories}. Boot loads it without an application context when
 * the failure comes before one exists, so it has only a no-argument constructor and injects nothing.
 */
public class InvalidSettingsFailureAnalyzer extends AbstractFailureAnalyzer<InvalidSettingsException> {

	static final String ACTION = "Fix the named setting(s) and start again; see szczur-radar-api/README.md.";

	@Override
	protected FailureAnalysis analyze(Throwable rootFailure, InvalidSettingsException cause) {
		return new FailureAnalysis(cause.getMessage(), ACTION, cause);
	}

}
