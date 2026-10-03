package pl.szczurradar.api.llm;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.ResponseFormat;

import pl.szczurradar.api.config.LlmProviderProperties.Provider;
import pl.szczurradar.api.config.LlmProviderProperties.ServiceTier;

/**
 * DeepSeek directly through Spring AI's {@code DeepSeekChatModel}: JSON mode (the API takes no
 * schema), thinking off, temperature 0, and the schema appended to the system prompt. JSON mode beat
 * strict tool calling in the 2026-10-01 smoke (96.7% vs 83.3% contract compliance).
 */
final class DeepSeekClient implements LlmClient {

	/** Fixed paragraph before the schema in the system prompt. */
	static final String SCHEMA_INSTRUCTION = "\n\nReturn exactly one JSON object, with no other text, that conforms to this JSON Schema:\n";

	private final ChatModel chatModel;

	private final String model;

	private final TransportRetry retry;

	private final DeepSeekCacheUsage cacheUsage;

	DeepSeekClient(ChatModel chatModel, String model, TransportRetry retry, DeepSeekCacheUsage cacheUsage) {
		this.chatModel = chatModel;
		this.model = model;
		this.retry = retry;
		this.cacheUsage = cacheUsage;
	}

	@Override
	public LlmResponse complete(LlmRequest request) {
		String systemPrompt = request.systemPrompt() + SCHEMA_INSTRUCTION
				+ ProviderSchemas.forProvider(Provider.DEEPSEEK, request.outputSchema());
		Prompt prompt = new Prompt(List.of(new SystemMessage(systemPrompt), new UserMessage(request.userMessage())),
				DeepSeekChatOptions.builder()
					.model(model)
					.temperature(0.0)
					.responseFormat(ResponseFormat.builder().type(ResponseFormat.Type.JSON_OBJECT).build())
					.disableThinking()
					.build());
		TransportRetry.Outcome<Call> outcome = retry.run(ServiceTier.DEFAULT, _ -> call(prompt));
		ChatResponse response = outcome.value().response();
		Integer cached = outcome.value().cacheHitTokens() != null ? outcome.value().cacheHitTokens()
				: ChatResponses.cachedTokens(response);
		String reportedModel = ChatResponses.model(response);
		return new LlmResponse(ChatResponses.text(response), Provider.DEEPSEEK,
				reportedModel != null ? reportedModel : model, Provider.DEEPSEEK.id(), outcome.serviceTier(), false,
				null, OutputMode.JSON_MODE, ChatResponses.inputTokens(response), ChatResponses.outputTokens(response),
				cached, outcome.attempts());
	}

	private record Call(ChatResponse response, @Nullable Integer cacheHitTokens) {
	}

	private Call call(Prompt prompt) {
		cacheUsage.clear();
		ChatResponse response;
		try {
			response = chatModel.call(prompt);
		}
		catch (TransportException ex) {
			// an HTTP error status, already classified by DeepSeekErrors
			throw ex;
		}
		catch (RuntimeException ex) {
			throw TransportException.ofThrowable(ex);
		}
		if (ChatResponses.finishReasons(response).contains("content_filter")) {
			throw new TransportException(TransportException.Kind.REJECTED, "answer blocked by the content filter", null,
					null);
		}
		return new Call(response, cacheUsage.take());
	}

}
