package pl.szczurradar.api.llm;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonValue;

/** {@code LLM_REASONING_EFFORT}; {@link #id()} is the setting's value, the API value and the JSON value. */
public enum ReasoningEffort {

	NONE, MINIMAL, LOW, MEDIUM, HIGH, XHIGH;

	@JsonValue
	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

}
