package pl.szczurradar.api.pipeline.run;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The result of one source in a range run's fetch ({@code pipeline_run_sources.status}): it gave at least
 * one post ({@code succeeded}), it answered without a post ({@code empty}: no element in the range, or
 * only skipped ones), or it threw ({@code failed}); {@link #value()} is the stored and API value.
 */
public enum SourceFetchStatus {

	SUCCEEDED, EMPTY, FAILED;

	@JsonValue
	public String value() {
		return name().toLowerCase(Locale.ROOT);
	}

}
