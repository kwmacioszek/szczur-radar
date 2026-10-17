package pl.szczurradar.api.pipeline.run;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The state of a pipeline run step; {@code interrupted} means its run was interrupted (or stopped)
 * while the step was running; {@link #value()} is the stored and API value.
 */
public enum StepStatus {

	RUNNING, SUCCEEDED, FAILED, INTERRUPTED;

	@JsonValue
	public String value() {
		return name().toLowerCase(Locale.ROOT);
	}

}
