package pl.szczurradar.api.agents.assessor.contract;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonValue;

/** {@code stance.assertiveness} (contract-v2.md §3); JSON {@code null} where table §7.2 requires it. */
public enum Assertiveness {

	HEDGED, UNHEDGED;

	/** The contract value: the lower-case constant name. */
	@JsonValue
	public String value() {
		return name().toLowerCase(Locale.ROOT);
	}

}
