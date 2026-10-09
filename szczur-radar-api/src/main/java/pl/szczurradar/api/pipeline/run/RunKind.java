package pl.szczurradar.api.pipeline.run;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * What a pipeline run processes: a date range of the source, the leftovers of earlier runs, or
 * posts picked by an admin; {@link #value()} is the stored and API value.
 */
public enum RunKind {

	RANGE, RETRY, POSTS;

	@JsonValue
	public String value() {
		return name().toLowerCase(Locale.ROOT);
	}

}
