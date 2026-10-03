package pl.szczurradar.api.agents.assessor.contract;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonValue;

/** {@code claim.attribution} (contract-v2.md §3). */
public enum Attribution {

	AUTHOR, REPORTED, UNCLEAR;

	/** The contract value: the lower-case constant name. */
	@JsonValue
	public String value() {
		return name().toLowerCase(Locale.ROOT);
	}

}
