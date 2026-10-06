package pl.szczurradar.api.agents.assessor.contract;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonValue;

/** {@code quote.document}: the input document a quote points to (contract-v2.md §3, §10). */
public enum Document {

	ITEM, PARENT_POST, PARENT_COMMENT;

	/** The contract value: the lower-case constant name. */
	@JsonValue
	public String value() {
		return name().toLowerCase(Locale.ROOT);
	}

}
