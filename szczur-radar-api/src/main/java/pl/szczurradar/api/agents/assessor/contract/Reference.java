package pl.szczurradar.api.agents.assessor.contract;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonValue;

/** {@code target.reference} (contract-v2.md §3). */
public enum Reference {

	EXPLICIT, CONTEXTUAL, AMBIGUOUS, UNRESOLVED;

	/** The contract value: the lower-case constant name. */
	@JsonValue
	public String value() {
		return name().toLowerCase(Locale.ROOT);
	}

}
