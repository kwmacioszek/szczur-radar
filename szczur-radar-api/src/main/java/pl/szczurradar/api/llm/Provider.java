package pl.szczurradar.api.llm;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The supported providers; {@link #id()} is the {@code LLM_API_PROVIDER} value and the JSON value.
 */
public enum Provider {

	GEMINI, DEEPSEEK, OPENROUTER;

	@JsonValue
	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

}
