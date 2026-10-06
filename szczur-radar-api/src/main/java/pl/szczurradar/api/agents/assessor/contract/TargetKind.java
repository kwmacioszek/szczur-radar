package pl.szczurradar.api.agents.assessor.contract;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonValue;

/** {@code target.kind} (contract-v2.md §3). */
public enum TargetKind {

	COMPANY, ASSET, SECTOR, MACRO_TOPIC, OTHER, UNKNOWN;

	/** The contract value: the lower-case constant name. */
	@JsonValue
	public String value() {
		return name().toLowerCase(Locale.ROOT);
	}

}
