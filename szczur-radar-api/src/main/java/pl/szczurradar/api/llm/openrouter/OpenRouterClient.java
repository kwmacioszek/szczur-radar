package pl.szczurradar.api.llm.openrouter;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.client.OpenAIClientImpl;
import com.openai.client.okhttp.OkHttpClient;
import com.openai.client.okhttp.OpenAIOkHttpClientAsync;
import com.openai.core.ClientOptions;
import com.openai.core.JsonObject;
import com.openai.core.JsonString;
import com.openai.core.JsonValue;
import com.openai.errors.OpenAIServiceException;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

import pl.szczurradar.api.config.LlmProviderProperties;
import pl.szczurradar.api.config.LlmProviderProperties.Provider;
import pl.szczurradar.api.config.LlmProviderProperties.ReasoningEffort;
import pl.szczurradar.api.config.LlmProviderProperties.ServiceTier;
import pl.szczurradar.api.llm.Channel;
import pl.szczurradar.api.llm.LlmClient;
import pl.szczurradar.api.llm.LlmRequest;
import pl.szczurradar.api.llm.LlmResponse;
import pl.szczurradar.api.llm.OutputMode;
import pl.szczurradar.api.llm.transport.ChatResponses;
import pl.szczurradar.api.llm.transport.ProviderMessages;
import pl.szczurradar.api.llm.transport.ProviderSchemas;
import pl.szczurradar.api.llm.transport.TransportException;
import pl.szczurradar.api.llm.transport.TransportRetry;

/**
 * OpenRouter's OpenAI-compatible API through Spring AI's {@code OpenAiChatModel} on our own
 * openai-java client: strict {@code json_schema} without count and length limits, temperature 0,
 * optional reasoning effort. With {@code LLM_SERVICE_TIER=flex} the call is routed to OpenAI's flex
 * tier only ({@code provider.only = ["openai/flex"]}, no fallbacks); after three 429 refusals
 * {@link TransportRetry} retries it on {@code ["openai"]}.
 *
 * <p>OpenRouter reports an error that occurs once the model has started on the request in the body of
 * an HTTP 200 ({@code {"error":{"code",…}}} instead of {@code choices}); openai-java then fails with
 * "{@code choices} is not set". {@link OpenRouterRawResponse} keeps the raw answer, so such an error is
 * classified by its {@code code} like an HTTP status — a flex refusal is a 429 — and an answer without
 * {@code choices} is retried like a 5xx.
 * An error after partial output ({@code choices[0].error}) is classified before that output is accepted.
 *
 * <p>A 403 — as the HTTP status or an error's {@code code} — for a spent budget (the key's credit limit,
 * a workspace or a guardrail budget) is the configuration: every later call fails the same way until the
 * budget resets. Any other 403 (e.g. moderation) refuses one request and stays rejected.
 */
public final class OpenRouterClient implements LlmClient {

	static final String BASE_URL = "https://openrouter.ai/api/v1";

	/**
	 * How OpenRouter's 403 message starts once the key's own credit limit is spent
	 * (openrouter.ai/docs/guides/best-practices/capping-shared-agent-keys).
	 */
	static final String KEY_LIMIT_EXCEEDED = "Key limit exceeded";

	/**
	 * {@code error.metadata.limit_source} of a 403 for a spent budget: the key's credit limit, a workspace
	 * budget or a guardrail budget (openrouter.ai/docs/api_reference/limits).
	 */
	static final Set<String> BUDGET_LIMIT_SOURCES = Set.of("openrouter_key_limit", "openrouter_workspace_budget",
			"openrouter_guardrail_budget");

	private final ChatModel chatModel;

	private final String model;

	private final ServiceTier serviceTier;

	private final @Nullable ReasoningEffort reasoningEffort;

	private final Duration timeout;

	private final TransportRetry retry;

	private final OpenRouterRawResponse rawResponse;

	private final ProviderMessages messages;

	OpenRouterClient(ChatModel chatModel, String model, ServiceTier serviceTier,
			@Nullable ReasoningEffort reasoningEffort, Duration timeout, TransportRetry retry,
			OpenRouterRawResponse rawResponse, ProviderMessages messages) {
		this.chatModel = chatModel;
		this.model = model;
		this.serviceTier = serviceTier;
		this.reasoningEffort = reasoningEffort;
		this.timeout = timeout;
		this.retry = retry;
		this.rawResponse = rawResponse;
		this.messages = messages;
	}

	/**
	 * The client on Spring AI's {@code OpenAiChatModel} over openai-java clients with no retries and the
	 * {@code LLM_TIMEOUT} limit; the SDK appends {@code chat/completions} to the base URL.
	 *
	 * @param baseUrl the API root instead of OpenRouter's (tests), {@code null} = the real one
	 */
	public static LlmClient create(LlmProviderProperties properties, @Nullable String baseUrl, TransportRetry retry,
			ProviderMessages messages) {
		String url = baseUrl != null ? baseUrl : BASE_URL;
		// The synchronous client on our raw-answer reader around openai-java's OkHttp client (the SDK's
		// builder has no interceptor), with the same base URL, key, time limit and no retries.
		OpenRouterRawResponse rawResponse = new OpenRouterRawResponse(
				OkHttpClient.builder().timeout(properties.timeout()).build());
		OpenAIClient client = new OpenAIClientImpl(ClientOptions.builder()
			.httpClient(rawResponse)
			.baseUrl(url)
			.apiKey(properties.apiKey())
			.maxRetries(0)
			.timeout(properties.timeout())
			.build());
		OpenAIClientAsync clientAsync = OpenAIOkHttpClientAsync.builder()
			.baseUrl(url)
			.apiKey(properties.apiKey())
			.maxRetries(0)
			.timeout(properties.timeout())
			.build();
		ChatModel chatModel = OpenAiChatModel.builder()
			.openAiClient(client)
			.openAiClientAsync(clientAsync)
			.options(OpenAiChatOptions.builder().model(properties.model()).build())
			.build();
		return new OpenRouterClient(chatModel, properties.model(), properties.serviceTier(), properties.reasoningEffort(),
				properties.timeout(), retry, rawResponse, messages);
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
		rawResponse.clear();
		ChatResponse response;
		try {
			response = chatModel.call(prompt);
		}
		catch (OpenAIServiceException ex) {
			List<String> retryAfter = ex.headers().values("Retry-After");
			JsonValue error = ex.body(); // openai-java keeps the answer's error object
			throw statusFailure(ex.statusCode(), text(error, "message"), text(error, "metadata", "limit_source"),
					messages.clean(String.valueOf(ex.getMessage())),
					TransportException.parseRetryAfter(retryAfter.isEmpty() ? null : retryAfter.getFirst()), ex);
		}
		catch (RuntimeException ex) {
			// e.g. openai-java's "`choices` is not set" on an error reported in a 200 answer
			throw failedAnswer(rawResponse.take(), ex).orElseGet(() -> TransportException.ofThrowable(ex, messages));
		}
		Optional<TransportException> failure = failedAnswer(rawResponse.take(), null);
		if (failure.isPresent()) {
			throw failure.get();
		}
		if (response.getResults().isEmpty()) {
			throw withoutChoices(null);
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

	/**
	 * The failure a 2xx answer reported, if it did: an {@code error} object is classified as the HTTP
	 * status in its {@code code} (429 retried and, after three, flex → default; 5xx retried; 401/402/404
	 * and a 403 for a spent budget the configuration); an answer without {@code choices} and without an
	 * error is retried like a 5xx.
	 */
	private Optional<TransportException> failedAnswer(OpenRouterRawResponse.Observed answer,
			@Nullable Throwable cause) {
		if (!answer.answered()) {
			return Optional.empty();
		}
		if (answer.errorCode() != null) {
			String type = answer.errorType() != null ? " (" + answer.errorType() + ")" : "";
			return Optional.of(statusFailure(answer.errorCode(), answer.errorMessage(), answer.limitSource(),
					messages.clean("error in a 200 answer: " + answer.errorMessage() + type), null, cause));
		}
		return answer.choices() ? Optional.empty() : Optional.of(withoutChoices(cause));
	}

	/**
	 * {@link TransportException#ofStatus}, except that a 403 for a spent budget is the configuration.
	 *
	 * @param errorMessage the provider's {@code error.message}, as received
	 * @param limitSource the provider's {@code error.metadata.limit_source}
	 * @param message the cleaned text for the exception
	 */
	private static TransportException statusFailure(int status, @Nullable String errorMessage,
			@Nullable String limitSource, String message, @Nullable Duration retryAfter, @Nullable Throwable cause) {
		if (status == 403 && budgetSpent(errorMessage, limitSource)) {
			return new TransportException(TransportException.Kind.CONFIGURATION, "HTTP 403: " + message, retryAfter,
					cause);
		}
		return TransportException.ofStatus(status, message, retryAfter, cause);
	}

	private static boolean budgetSpent(@Nullable String errorMessage, @Nullable String limitSource) {
		return errorMessage != null && errorMessage.startsWith(KEY_LIMIT_EXCEEDED)
				|| limitSource != null && BUDGET_LIMIT_SOURCES.contains(limitSource);
	}

	/** The string at {@code path} in an HTTP error's {@code error} object, or {@code null}. */
	private static @Nullable String text(JsonValue error, String... path) {
		JsonValue value = error;
		for (String name : path) {
			value = value instanceof JsonObject object ? object.values().get(name) : null;
		}
		return value instanceof JsonString string ? string.value() : null;
	}

	private static TransportException withoutChoices(@Nullable Throwable cause) {
		return new TransportException(TransportException.Kind.SERVER_ERROR, "answer without choices", null, cause);
	}

}
