package pl.szczurradar.api.pipeline.run;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonValue;

/** A step of a pipeline run, in order; {@link #value()} is the stored and API value. */
public enum RunStep {

	FETCH, STORE, SELECT, ASSESS, SUMMARIZE;

	@JsonValue
	public String value() {
		return name().toLowerCase(Locale.ROOT);
	}

}
