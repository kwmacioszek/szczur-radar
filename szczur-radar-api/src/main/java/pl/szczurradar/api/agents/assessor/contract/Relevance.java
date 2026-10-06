package pl.szczurradar.api.agents.assessor.contract;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonValue;

/** {@code relevance} (contract-v2.md §3). */
public enum Relevance {

	INVESTMENT_RELATED, NOT_INVESTMENT_RELATED, UNCLEAR;

	/** The contract value: the lower-case constant name. */
	@JsonValue
	public String value() {
		return name().toLowerCase(Locale.ROOT);
	}

}
