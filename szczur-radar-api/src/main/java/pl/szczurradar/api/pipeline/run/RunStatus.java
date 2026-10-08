package pl.szczurradar.api.pipeline.run;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The state of a pipeline run; {@code interrupted} means the process stopped (deploy, restart)
 * while it was queued or running; {@link #value()} is the stored and API value.
 */
public enum RunStatus {

	QUEUED, RUNNING, SUCCEEDED, PARTIAL, FAILED, INTERRUPTED;

	@JsonValue
	public String value() {
		return name().toLowerCase(Locale.ROOT);
	}

}
