package pl.szczurradar.api.agents.assessor.contract;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonValue;

/** {@code stance.direction} (contract-v2.md §3, table §7.2). */
public enum Direction {

	BULLISH, BEARISH, NEUTRAL, MIXED, NOT_EXPRESSED, UNCLEAR;

	/** The contract value: the lower-case constant name. */
	@JsonValue
	public String value() {
		return name().toLowerCase(Locale.ROOT);
	}

}
