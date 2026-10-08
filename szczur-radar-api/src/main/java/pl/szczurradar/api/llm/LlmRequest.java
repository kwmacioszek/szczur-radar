package pl.szczurradar.api.llm;

import java.util.Objects;

import pl.szczurradar.api.llm.transport.ProviderSchemas;

/**
 * Input of {@link LlmClient#complete(LlmRequest)}.
 *
 * @param systemPrompt the instructions
 * @param userMessage the material to work on
 * @param outputSchema the full JSON Schema (draft 2020-12) of the expected output; each client sends
 *     the subset its provider accepts ({@link ProviderSchemas})
 */
public record LlmRequest(String systemPrompt, String userMessage, String outputSchema) {

	public LlmRequest {
		Objects.requireNonNull(systemPrompt, "systemPrompt");
		Objects.requireNonNull(userMessage, "userMessage");
		Objects.requireNonNull(outputSchema, "outputSchema");
	}

}
