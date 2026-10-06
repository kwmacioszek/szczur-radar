package pl.szczurradar.api.pipeline;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonValue;

/** The state of a pipeline run step; {@link #value()} is the stored and API value. */
public enum StepStatus {

	RUNNING, SUCCEEDED, FAILED;

	@JsonValue
	public String value() {
		return name().toLowerCase(Locale.ROOT);
	}

}
