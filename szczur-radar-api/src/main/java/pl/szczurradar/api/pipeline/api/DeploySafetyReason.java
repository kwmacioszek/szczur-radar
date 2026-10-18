package pl.szczurradar.api.pipeline.api;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Why a deploy is not safe now ({@code GET /api/deploy-safety}); {@link #value()} is the API value.
 * A running run outranks a queued one: both can exist at once (a retry runs, the range waits behind it).
 */
enum DeploySafetyReason {

	PIPELINE_RUNNING, PIPELINE_QUEUED;

	@JsonValue
	String value() {
		return name().toLowerCase(Locale.ROOT);
	}

}
