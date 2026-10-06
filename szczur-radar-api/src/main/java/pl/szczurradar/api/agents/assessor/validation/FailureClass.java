package pl.szczurradar.api.agents.assessor.validation;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Why an assessment is not a valid one (contract-v2.md §12). The first two come from the model call,
 * the rest from parsing and validating its response; contract violations are never retried.
 */
public enum FailureClass {

	/** Provider error, refusal or transport error after the last attempt. */
	PROVIDER_ERROR,

	/** Time limit exceeded after the last attempt. */
	TIMEOUT,

	/** No text to read, invalid JSON, trailing tokens or duplicate keys. */
	INVALID_STRUCTURED_OUTPUT,

	/** JSON not in the shape of the V2 schema: keys, types, enums, limits §9. */
	SCHEMA_VIOLATION,

	/** Broken identifiers or references (D1–D5). */
	INVALID_REFERENCE,

	/** A quote that is not a fragment of the field it points to (D6). */
	FABRICATED_EVIDENCE,

	/** Any other contract invariant (D7–D15). */
	DOMAIN_INVARIANT_VIOLATION;

	/** The contract value, e.g. {@code fabricated_evidence}. */
	@JsonValue
	public String value() {
		return name().toLowerCase(Locale.ROOT);
	}

}
