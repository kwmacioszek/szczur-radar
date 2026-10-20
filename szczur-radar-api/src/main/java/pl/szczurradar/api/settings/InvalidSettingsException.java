package pl.szczurradar.api.settings;

/**
 * An environment setting has an invalid value, so the application or an operator command cannot start
 * (e.g. {@code LLM_TIMEOUT=120}). One type for the settings checked through this package
 * ({@code LLM_*}, {@code PIPELINE_STALL_TIMEOUT}, {@code HEALTHCHECK_PING_URL}), so that a configuration
 * error is told apart from a failure of the database or a migration: Spring Boot reports it as
 * {@code APPLICATION FAILED TO START} without a stack trace, and an operator command exits with its
 * usage-error code.
 *
 * <p>Other settings (e.g. {@code PIPELINE_TRIGGER_TOKEN}, {@code WYKOP_*}, {@code PIPELINE_LLM_CONCURRENCY})
 * still fail with a plain {@link IllegalStateException}.
 *
 * <p>The message names each setting and its problem. It never contains the value of {@code LLM_API_KEY}
 * or {@code HEALTHCHECK_PING_URL}, and repeats a refused value only in part when it is long
 * ({@link SettingText#echoed}).
 */
public class InvalidSettingsException extends IllegalStateException {

	public InvalidSettingsException(String message) {
		super(message);
	}

}
