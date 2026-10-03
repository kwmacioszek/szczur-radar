package pl.szczurradar.api.agents.assessor.contract;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonValue;

/** {@code modifiers[]} (contract-v2.md §3). */
public enum Modifier {

	HUMOR, PROMOTION;

	/** The contract value: the lower-case constant name. */
	@JsonValue
	public String value() {
		return name().toLowerCase(Locale.ROOT);
	}

}
