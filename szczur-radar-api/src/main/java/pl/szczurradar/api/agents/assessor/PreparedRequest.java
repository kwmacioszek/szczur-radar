package pl.szczurradar.api.agents.assessor;

import java.util.Objects;

import pl.szczurradar.api.llm.LlmRequest;

/**
 * The model call for one assessable entry, built but not sent: what {@code Assessor.evaluate} needs to
 * judge the answer, whether the call is made now ({@code assess}) or later in a batch.
 *
 * @param request the system prompt, the user message and the full V2 output schema
 * @param promptVersion the version of the prompt in {@code request}
 */
public record PreparedRequest(LlmRequest request, String promptVersion) {

	public PreparedRequest {
		Objects.requireNonNull(request, "request");
		Objects.requireNonNull(promptVersion, "promptVersion");
	}

}
