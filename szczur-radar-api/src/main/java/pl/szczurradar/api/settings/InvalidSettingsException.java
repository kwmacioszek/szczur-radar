package pl.szczurradar.api.settings;

/**
 * An environment setting has an invalid value, so the application or an operator command cannot start
 * (e.g. {@code LLM_TIMEOUT=120}). One type for every such setting, so that a configuration error is told
 * apart from a failure of the database or a migration: Spring Boot reports it as
 * {@code APPLICATION FAILED TO START} without a stack trace, and an operator command exits with its
 * usage-error code.
 *
 * <p>The message names each setting and its problem, and never contains a secret (e.g. {@code LLM_API_KEY}).
 */
public class InvalidSettingsException extends IllegalStateException {

	public InvalidSettingsException(String message) {
		super(message);
	}

}
