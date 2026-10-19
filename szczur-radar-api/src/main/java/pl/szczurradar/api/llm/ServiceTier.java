package pl.szczurradar.api.llm;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonValue;

/** {@code LLM_SERVICE_TIER}; {@link #id()} is the setting's value and the JSON value. */
public enum ServiceTier {

	DEFAULT, FLEX;

	@JsonValue
	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

}
