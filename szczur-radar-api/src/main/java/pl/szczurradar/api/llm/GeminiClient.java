package pl.szczurradar.api.llm;

import java.util.List;

import com.google.genai.errors.ApiException;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;

import pl.szczurradar.api.config.LlmProviderProperties.Provider;
import pl.szczurradar.api.config.LlmProviderProperties.ServiceTier;

/**
 * Gemini (Google AI Studio) through Spring AI's {@code GoogleGenAiChatModel} on our own GenAI client:
 * response JSON Schema without count limits, temperature 0, no thoughts in the answer. The GenAI
 * SDK does not expose {@code Retry-After}, so 429s back off exponentially.
 *
 * <p>Spring AI converts the schema to GenAI's {@code Schema} type, which silently drops
 * {@code additionalProperties} and {@code uniqueItems} (property order and {@code required} stay);
 * the raw answer is still checked against the full schema by the caller.
 */
final class GeminiClient implements LlmClient {

	private final ChatModel chatModel;

	private final String model;

	private final TransportRetry retry;

	private final ProviderMessages messages;

	GeminiClient(ChatModel chatModel, String model, TransportRetry retry, ProviderMessages messages) {
		this.chatModel = chatModel;
		this.model = model;
		this.retry = retry;
		this.messages = messages;
	}

	@Override
	public LlmResponse complete(LlmRequest request) {
		Prompt prompt = new Prompt(
				List.of(new SystemMessage(request.systemPrompt()), new UserMessage(request.userMessage())),
				GoogleGenAiChatOptions.builder()
					.model(model)
					.temperature(0.0)
					.outputSchema(ProviderSchemas.forProvider(Provider.GEMINI, request.outputSchema()))
					.includeThoughts(false)
					.build());
		TransportRetry.Outcome<ChatResponse> outcome = retry.run(ServiceTier.DEFAULT, _ -> call(prompt));
		ChatResponse response = outcome.value();
		String reportedModel = ChatResponses.model(response);
		return new LlmResponse(ChatResponses.text(response), Provider.GEMINI,
				reportedModel != null ? reportedModel : model, Channel.GEMINI, outcome.serviceTier(), false, null,
				OutputMode.JSON_SCHEMA, ChatResponses.inputTokens(response), ChatResponses.outputTokens(response),
				ChatResponses.cachedTokens(response), outcome.attempts());
	}

	private ChatResponse call(Prompt prompt) {
		ChatResponse response;
		try {
			response = chatModel.call(prompt);
		}
		catch (RuntimeException ex) {
			throw classify(ex);
		}
		if (response.getResults().isEmpty()) {
			throw new TransportException(TransportException.Kind.REJECTED, "no candidates: the answer was blocked",
					null, null);
		}
		if (ChatResponses.text(response).isBlank() && !ChatResponses.finishReasons(response).equals("stop")) {
			throw new TransportException(TransportException.Kind.REJECTED,
					"no text, finish reason " + ChatResponses.finishReasons(response), null, null);
		}
		return response;
	}

	/** Spring AI wraps every GenAI error in {@code RuntimeException("Failed to generate content")}. */
	private TransportException classify(RuntimeException error) {
		for (Throwable cause = error; cause != null; cause = cause.getCause()) {
			if (cause instanceof ApiException api) {
				return TransportException.ofStatus(api.code(), messages.clean(api.status() + " " + api.message()), null,
						error);
			}
		}
		return TransportException.ofThrowable(error, messages);
	}

}
