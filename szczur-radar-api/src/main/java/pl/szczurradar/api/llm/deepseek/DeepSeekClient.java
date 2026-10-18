package pl.szczurradar.api.llm.deepseek;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.deepseek.api.ResponseFormat;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import pl.szczurradar.api.llm.Channel;
import pl.szczurradar.api.llm.LlmClient;
import pl.szczurradar.api.llm.LlmProviderProperties;
import pl.szczurradar.api.llm.LlmRequest;
import pl.szczurradar.api.llm.LlmResponse;
import pl.szczurradar.api.llm.OutputMode;
import pl.szczurradar.api.llm.Provider;
import pl.szczurradar.api.llm.ServiceTier;
import pl.szczurradar.api.llm.transport.ChatResponses;
import pl.szczurradar.api.llm.transport.HttpStatusErrors;
import pl.szczurradar.api.llm.transport.ProviderMessages;
import pl.szczurradar.api.llm.transport.ProviderSchemas;
import pl.szczurradar.api.llm.transport.TransportException;
import pl.szczurradar.api.llm.transport.TransportRetry;

/**
 * DeepSeek directly through Spring AI's {@code DeepSeekChatModel}: JSON mode (the API takes no
 * schema), thinking off, temperature 0, and the schema appended to the system prompt. JSON mode beat
 * strict tool calling in the 2026-10-01 smoke (96.7% vs 83.3% contract compliance).
 */
public final class DeepSeekClient implements LlmClient {

	/** Fixed paragraph before the schema in the system prompt. */
	static final String BASE_URL = "https://api.deepseek.com";

	static final String SCHEMA_INSTRUCTION = "\n\nReturn exactly one JSON object, with no other text, that conforms to this JSON Schema:\n";

	private final ChatModel chatModel;

	private final String model;

	private final TransportRetry retry;

	private final DeepSeekRawResponse rawResponse;

	private final ProviderMessages messages;

	DeepSeekClient(ChatModel chatModel, String model, TransportRetry retry, DeepSeekRawResponse rawResponse,
			ProviderMessages messages) {
		this.chatModel = chatModel;
		this.model = model;
		this.retry = retry;
		this.rawResponse = rawResponse;
		this.messages = messages;
	}

	/**
	 * The client on Spring AI's {@code DeepSeekChatModel} over a JDK {@code RestClient} without retry: the
	 * connect phase at most 30 s, every read the {@code LLM_TIMEOUT} limit; the raw answer is read too
	 * ({@link DeepSeekRawResponse}).
	 *
	 * @param baseUrl the API root instead of DeepSeek's (tests), {@code null} = the real one
	 */
	public static LlmClient create(LlmProviderProperties properties, @Nullable String baseUrl, TransportRetry retry,
			ProviderMessages messages) {
		DeepSeekRawResponse rawResponse = new DeepSeekRawResponse();
		HttpClient http = HttpClient.newBuilder().connectTimeout(connectTimeout(properties.timeout())).build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(http);
		requestFactory.setReadTimeout(properties.timeout());
		RestClient.Builder restClient = RestClient.builder()
			.requestFactory(new BufferingClientHttpRequestFactory(requestFactory))
			.requestInterceptor(rawResponse);
		DeepSeekApi api = DeepSeekApi.builder()
			.baseUrl(baseUrl != null ? baseUrl : BASE_URL)
			.apiKey(properties.apiKey())
			.restClientBuilder(restClient)
			.responseErrorHandler(new HttpStatusErrors(messages))
			.build();
		ChatModel chatModel = DeepSeekChatModel.builder()
			.deepSeekApi(api)
			.options(DeepSeekChatOptions.builder().model(properties.model()).build())
			.retryTemplate(TransportRetry.SPRING_AI_OFF)
			.build();
		return new DeepSeekClient(chatModel, properties.model(), retry, rawResponse, messages);
	}

	private static Duration connectTimeout(Duration timeout) {
		Duration max = Duration.ofSeconds(30);
		return timeout.compareTo(max) < 0 ? timeout : max;
	}

	@Override
	public LlmResponse complete(LlmRequest request) {
		String systemPrompt = request.systemPrompt() + SCHEMA_INSTRUCTION
				+ ProviderSchemas.forProvider(Provider.DEEPSEEK, request.outputSchema());
		Prompt prompt = new Prompt(List.of(new SystemMessage(systemPrompt), new UserMessage(request.userMessage())),
				DeepSeekChatOptions.builder()
					.model(model)
					.temperature(0.0)
					.maxTokens(MAX_OUTPUT_TOKENS)
					.responseFormat(ResponseFormat.builder().type(ResponseFormat.Type.JSON_OBJECT).build())
					.disableThinking()
					.build());
		TransportRetry.Outcome<Call> outcome = retry.run(ServiceTier.DEFAULT, _ -> call(prompt));
		ChatResponse response = outcome.value().response();
		Integer cached = outcome.value().cacheHitTokens() != null ? outcome.value().cacheHitTokens()
				: ChatResponses.cachedTokens(response);
		String reportedModel = ChatResponses.model(response);
		return new LlmResponse(ChatResponses.text(response), Provider.DEEPSEEK,
				reportedModel != null ? reportedModel : model, Channel.DEEPSEEK, outcome.serviceTier(), false,
				null, OutputMode.JSON_MODE, ChatResponses.inputTokens(response), ChatResponses.outputTokens(response),
				cached, outcome.attempts());
	}

	private record Call(ChatResponse response, @Nullable Integer cacheHitTokens) {
	}

	private Call call(Prompt prompt) {
		rawResponse.clear();
		ChatResponse response;
		try {
			response = chatModel.call(prompt);
		}
		catch (TransportException ex) {
			// an HTTP error status, already classified and cleaned by HttpStatusErrors
			throw ex;
		}
		catch (RuntimeException ex) {
			// Spring AI cannot map DeepSeek's newer finish reasons; the raw answer says why it ended.
			DeepSeekRawResponse.Observed observed = rawResponse.take();
			if (observed.cutOffByServer()) {
				throw serverCutOff(observed, ex);
			}
			throw TransportException.ofThrowable(ex, messages);
		}
		DeepSeekRawResponse.Observed observed = rawResponse.take();
		if (observed.cutOffByServer()) {
			throw serverCutOff(observed, null);
		}
		if (response.getResults().isEmpty()) {
			throw new TransportException(TransportException.Kind.REJECTED, "no choices in the answer", null, null);
		}
		if (ChatResponses.finishReasons(response).contains("content_filter")) {
			throw new TransportException(TransportException.Kind.REJECTED, "answer blocked by the content filter", null,
					null);
		}
		ChatResponses.requireText(response);
		return new Call(response, observed.cacheHitTokens());
	}

	private static TransportException serverCutOff(DeepSeekRawResponse.Observed observed, @Nullable Throwable cause) {
		return new TransportException(TransportException.Kind.SERVER_ERROR,
				"answer cut off by the server, finish reason " + observed.finishReason(), null, cause);
	}

}
