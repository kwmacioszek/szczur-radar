package pl.szczurradar.api.llm;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The route a call took: the provider's own API, or for OpenRouter with {@code LLM_SERVICE_TIER=flex}
 * the OpenAI upstream it was pinned to. {@link #value()} is the recorded value and, for the OpenAI
 * upstreams, the OpenRouter provider slug sent in {@code provider.only}.
 */
public enum Channel {

	GEMINI("gemini"),

	DEEPSEEK("deepseek"),

	/** OpenRouter with its own routing (default tier). */
	OPENROUTER("openrouter"),

	/** OpenRouter pinned to OpenAI's flex tier. */
	OPENAI_FLEX("openai/flex"),

	/** OpenRouter pinned to OpenAI's default tier (after a flex fallback). */
	OPENAI("openai");

	private final String value;

	Channel(String value) {
		this.value = value;
	}

	@JsonValue
	public String value() {
		return value;
	}

}
