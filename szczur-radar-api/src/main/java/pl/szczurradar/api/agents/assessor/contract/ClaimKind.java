package pl.szczurradar.api.agents.assessor.contract;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonValue;

/** {@code claim.kind} (contract-v2.md §3). */
public enum ClaimKind {

	FACTUAL_CLAIM, OPINION, INVESTMENT_THESIS, QUESTION, TRADE_ACTION;

	/** The contract value: the lower-case constant name. */
	@JsonValue
	public String value() {
		return name().toLowerCase(Locale.ROOT);
	}

}
