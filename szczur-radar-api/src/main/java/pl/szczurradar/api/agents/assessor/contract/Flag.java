package pl.szczurradar.api.agents.assessor.contract;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonValue;

/** {@code flags[]} (contract-v2.md §3). */
public enum Flag {

	MISSING_CONTEXT, POSSIBLE_SARCASM, INSUFFICIENT_CONTENT, EXTRACTION_LIMIT;

	/** The contract value: the lower-case constant name. */
	@JsonValue
	public String value() {
		return name().toLowerCase(Locale.ROOT);
	}

}
