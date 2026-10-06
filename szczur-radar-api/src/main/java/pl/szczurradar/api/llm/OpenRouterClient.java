package pl.szczurradar.api.llm;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.openai.errors.OpenAIServiceException;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

import pl.szczurradar.api.config.LlmProviderProperties.Provider;
import pl.szczurradar.api.config.LlmProviderProperties.ReasoningEffort;
import pl.szczurradar.api.config.LlmProviderProperties.ServiceTier;

/**
 * OpenRouter's OpenAI-compatible API through Spring AI's {@code OpenAiChatModel} on our own
 * openai-java client: strict {@code json_schema} without count and length limits, temperature 0,
 * optional reasoning effort. With {@code LLM_SERVICE_TIER=flex} the call is routed to OpenAI's flex
 * tier only ({@code provider.only = ["openai/flex"]}, no fallbacks); after three 429 refusals
 * {@link TransportRetry} retries it on {@code ["openai"]}.
 */
final class OpenRouterClient implements LlmClient {

	private final ChatModel chatModel;

	private final String model;

	private final ServiceTier serviceTier;

	private final @Nullable ReasoningEffort reasoningEffort;

	private final Duration timeout;

	private final TransportRetry retry;

	private final ProviderMessages messages;

	OpenRouterClient(ChatModel chatModel, String model, ServiceTier serviceTier,
			@Nullable ReasoningEffort reasoningEffort, Duration timeout, TransportRetry retry,
			ProviderMessages messages) {
		this.chatModel = chatModel;
		this.model = model;
		this.serviceTier = serviceTier;
		this.reasoningEffort = reasoningEffort;
		this.timeout = timeout;
		this.retry = retry;
		this.messages = messages;
	}

	@Override
	public LlmResponse complete(LlmRequest request) {
		String schema = ProviderSchemas.forProvider(Provider.OPENROUTER, request.outputSchema());
		TransportRetry.Outcome<ChatResponse> outcome = retry.run(serviceTier,
				tier -> call(new Prompt(
						List.of(new SystemMessage(request.systemPrompt()), new UserMessage(request.userMessage())),
						options(schema, tier))));
		ChatResponse response = outcome.value();
		String reportedModel = ChatResponses.model(response);
		return new LlmResponse(ChatResponses.text(response), Provider.OPENROUTER,
				reportedModel != null ? reportedModel : model, channel(outcome.serviceTier()), outcome.serviceTier(),
				outcome.flexFallback(), reasoningEffort, OutputMode.JSON_SCHEMA, ChatResponses.inputTokens(response),
				ChatResponses.outputTokens(response), ChatResponses.cachedTokens(response), outcome.attempts());
	}

	OpenAiChatOptions options(String schema, ServiceTier tier) {
		OpenAiChatOptions.Builder options = OpenAiChatOptions.builder()
			.model(model)
			.temperature(0.0)
			.maxTokens(MAX_OUTPUT_TOKENS)
			.timeout(timeout)
			.responseFormat(OpenAiChatModel.ResponseFormat.builder().jsonSchema(schema).strict(true).build());
		if (reasoningEffort != null) {
			options.reasoningEffort(reasoningEffort.id());
		}
		if (serviceTier == ServiceTier.FLEX) {
			options.extraBody(
					Map.of("provider", Map.of("only", List.of(upstream(tier).value()), "allow_fallbacks", false)));
		}
		return options.build();
	}

	private Channel channel(ServiceTier tier) {
		return serviceTier == ServiceTier.FLEX ? upstream(tier) : Channel.OPENROUTER;
	}

	private static Channel upstream(ServiceTier tier) {
		return tier == ServiceTier.FLEX ? Channel.OPENAI_FLEX : Channel.OPENAI;
	}

	private ChatResponse call(Prompt prompt) {
		ChatResponse response;
		try {
			response = chatModel.call(prompt);
		}
		catch (OpenAIServiceException ex) {
			List<String> retryAfter = ex.headers().values("Retry-After");
			throw TransportException.ofStatus(ex.statusCode(), messages.clean(String.valueOf(ex.getMessage())),
					TransportException.parseRetryAfter(retryAfter.isEmpty() ? null : retryAfter.getFirst()), ex);
		}
		catch (RuntimeException ex) {
			throw TransportException.ofThrowable(ex, messages);
		}
		if (response.getResults().isEmpty()) {
			throw new TransportException(TransportException.Kind.REJECTED, "no choices in the answer", null, null);
		}
		Object refusal = response.getResults().getFirst().getOutput().getMetadata().get("refusal");
		if (refusal instanceof String text && !text.isBlank()) {
			throw new TransportException(TransportException.Kind.REJECTED, "model refusal: " + text, null, null);
		}
		if (ChatResponses.finishReasons(response).contains("content_filter")) {
			throw new TransportException(TransportException.Kind.REJECTED, "answer blocked by the content filter", null,
					null);
		}
		ChatResponses.requireText(response);
		return response;
	}

}
