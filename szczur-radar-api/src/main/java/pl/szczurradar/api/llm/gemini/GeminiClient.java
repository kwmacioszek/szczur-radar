package pl.szczurradar.api.llm.gemini;

import java.util.List;
import java.util.Optional;

import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;

import pl.szczurradar.api.llm.Channel;
import pl.szczurradar.api.llm.LlmClient;
import pl.szczurradar.api.llm.LlmProviderProperties;
import pl.szczurradar.api.llm.LlmRequest;
import pl.szczurradar.api.llm.LlmResponse;
import pl.szczurradar.api.llm.OutputMode;
import pl.szczurradar.api.llm.Provider;
import pl.szczurradar.api.llm.ServiceTier;
import pl.szczurradar.api.llm.transport.ChatResponses;
import pl.szczurradar.api.llm.transport.ProviderMessages;
import pl.szczurradar.api.llm.transport.ProviderSchemas;
import pl.szczurradar.api.llm.transport.TransportException;
import pl.szczurradar.api.llm.transport.TransportRetry;

/**
 * Gemini (Google AI Studio) through Spring AI's {@code GoogleGenAiChatModel} on our own GenAI client:
 * response JSON Schema without count limits, temperature 0, no thoughts in the answer. The GenAI
 * SDK does not expose {@code Retry-After}, so 429s back off exponentially.
 *
 * <p>Spring AI converts the schema to GenAI's {@code Schema} type, which silently drops
 * {@code additionalProperties} and {@code uniqueItems} (property order and {@code required} stay);
 * the raw answer is still checked against the full schema by the caller.
 */
public final class GeminiClient implements LlmClient {

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

	/**
	 * The client on Spring AI's {@code GoogleGenAiChatModel} over our own GenAI client: one HTTP attempt
	 * (google-genai would retry 408/429/5xx five times) with the {@code LLM_TIMEOUT} limit.
	 *
	 * @param baseUrl the API root instead of Google AI Studio's (tests), {@code null} = the real one
	 */
	public static LlmClient create(LlmProviderProperties properties, @Nullable String baseUrl, TransportRetry retry,
			ProviderMessages messages) {
		HttpOptions.Builder http = HttpOptions.builder()
			.timeout(Math.toIntExact(properties.timeout().toMillis()))
			.retryOptions(HttpRetryOptions.builder().attempts(1).build());
		if (baseUrl != null) {
			http.baseUrl(baseUrl);
		}
		Client client = Client.builder().apiKey(properties.apiKey()).vertexAI(false).httpOptions(http.build()).build();
		ChatModel chatModel = GoogleGenAiChatModel.builder()
			.genAiClient(client)
			.options(GoogleGenAiChatOptions.builder().model(properties.model()).build())
			.retryTemplate(TransportRetry.SPRING_AI_OFF)
			.build();
		return new GeminiClient(chatModel, properties.model(), retry, messages);
	}

	@Override
	public LlmResponse complete(LlmRequest request) {
		Prompt prompt = new Prompt(
				List.of(new SystemMessage(request.systemPrompt()), new UserMessage(request.userMessage())),
				GoogleGenAiChatOptions.builder()
					.model(model)
					.temperature(0.0)
					.maxOutputTokens(MAX_OUTPUT_TOKENS)
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
		ChatResponses.requireText(response);
		return response;
	}

	/**
	 * Spring AI wraps every GenAI error in {@code RuntimeException("Failed to generate content")}: the
	 * HTTP status of a GenAI {@code ApiException} in the chain, else a time-out or I/O error, else not a
	 * transport failure. The chain is attached without the key ({@link ProviderMessages#cleanCause}).
	 */
	private RuntimeException classify(RuntimeException error) {
		for (Throwable cause = error; cause != null; cause = cause.getCause()) {
			if (cause instanceof ApiException api) {
				return TransportException.ofStatus(api.code(), messages.clean(api.status() + " " + api.message()), null,
						messages.cleanCause(error));
			}
		}
		Optional<TransportException> classified = TransportException.ofTimeoutOrConnection(error, messages);
		if (classified.isPresent()) {
			return classified.get();
		}
		return TransportException.unclassified(error, messages);
	}

}
