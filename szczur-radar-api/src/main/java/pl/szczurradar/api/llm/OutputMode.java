package pl.szczurradar.api.llm;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonValue;

/** How the output's JSON shape was requested from the provider. */
public enum OutputMode {

	/** The provider enforces a JSON Schema (Gemini response schema, OpenRouter strict json_schema). */
	JSON_SCHEMA,

	/** The provider only guarantees a JSON object; the schema is in the system prompt (DeepSeek). */
	JSON_MODE;

	@JsonValue
	public String value() {
		return name().toLowerCase(Locale.ROOT);
	}

}
