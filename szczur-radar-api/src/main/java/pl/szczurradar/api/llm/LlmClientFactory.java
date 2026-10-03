package pl.szczurradar.api.llm;

import java.net.http.HttpClient;
import java.time.Duration;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.client.okhttp.OpenAIOkHttpClientAsync;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import pl.szczurradar.api.config.LlmProviderProperties;

/**
 * Builds the {@link LlmClient} of the configured provider from {@code LLM_*}, outside Embabel's
 * model beans: every retry below {@link TransportRetry} is switched off (Spring AI
 * {@code RetryTemplate}: none; google-genai: 1 attempt; openai-java: 0 retries; DeepSeek's
 * {@code RestClient}: no retry) and every HTTP attempt has the {@code LLM_TIMEOUT} limit.
 */
public final class LlmClientFactory {

	static final String DEEPSEEK_BASE_URL = "https://api.deepseek.com";

	static final String OPENROUTER_BASE_URL = "https://openrouter.ai/api/v1";

	/** Spring AI's own retry layer, off. */
	private static final RetryTemplate NO_RETRY = new RetryTemplate(RetryPolicy.withMaxRetries(0));

	private LlmClientFactory() {
	}

	public static LlmClient create(LlmProviderProperties properties) {
		return create(properties, null, TransportRetry.Sleeper.REAL);
	}

	/**
	 * @param baseUrl the provider API root instead of the real one (tests), {@code null} = real
	 * @param sleeper waits between attempts (tests replace it)
	 */
	static LlmClient create(LlmProviderProperties properties, @Nullable String baseUrl,
			TransportRetry.Sleeper sleeper) {
		TransportRetry retry = new TransportRetry(sleeper);
		return switch (properties.provider()) {
			case GEMINI -> new GeminiClient(gemini(properties, baseUrl), properties.model(), retry);
			case DEEPSEEK -> {
				DeepSeekCacheUsage cacheUsage = new DeepSeekCacheUsage();
				yield new DeepSeekClient(deepSeek(properties, baseUrl, cacheUsage), properties.model(), retry, cacheUsage);
			}
			case OPENROUTER -> new OpenRouterClient(openRouter(properties, baseUrl), properties.model(),
					properties.serviceTier(), properties.reasoningEffort(), properties.timeout(), retry);
		};
	}

	private static GoogleGenAiChatModel gemini(LlmProviderProperties properties, @Nullable String baseUrl) {
		HttpOptions.Builder http = HttpOptions.builder()
			.timeout(Math.toIntExact(properties.timeout().toMillis()))
			// google-genai retries 408/429/5xx 5 times by default; 1 attempt = no retry.
			.retryOptions(HttpRetryOptions.builder().attempts(1).build());
		if (baseUrl != null) {
			http.baseUrl(baseUrl);
		}
		Client client = Client.builder().apiKey(properties.apiKey()).vertexAI(false).httpOptions(http.build()).build();
		return GoogleGenAiChatModel.builder()
			.genAiClient(client)
			.options(GoogleGenAiChatOptions.builder().model(properties.model()).build())
			.retryTemplate(NO_RETRY)
			.build();
	}

	private static DeepSeekChatModel deepSeek(LlmProviderProperties properties, @Nullable String baseUrl,
			DeepSeekCacheUsage cacheUsage) {
		HttpClient http = HttpClient.newBuilder().connectTimeout(connectTimeout(properties.timeout())).build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(http);
		requestFactory.setReadTimeout(properties.timeout());
		RestClient.Builder restClient = RestClient.builder()
			.requestFactory(new BufferingClientHttpRequestFactory(requestFactory))
			.requestInterceptor(cacheUsage);
		DeepSeekApi api = DeepSeekApi.builder()
			.baseUrl(baseUrl != null ? baseUrl : DEEPSEEK_BASE_URL)
			.apiKey(properties.apiKey())
			.restClientBuilder(restClient)
			.responseErrorHandler(new HttpStatusErrors())
			.build();
		return DeepSeekChatModel.builder()
			.deepSeekApi(api)
			.options(DeepSeekChatOptions.builder().model(properties.model()).build())
			.retryTemplate(NO_RETRY)
			.build();
	}

	private static OpenAiChatModel openRouter(LlmProviderProperties properties, @Nullable String baseUrl) {
		String url = baseUrl != null ? baseUrl : OPENROUTER_BASE_URL;
		OpenAIClient client = OpenAIOkHttpClient.builder()
			.baseUrl(url)
			.apiKey(properties.apiKey())
			.maxRetries(0)
			.timeout(properties.timeout())
			.build();
		OpenAIClientAsync clientAsync = OpenAIOkHttpClientAsync.builder()
			.baseUrl(url)
			.apiKey(properties.apiKey())
			.maxRetries(0)
			.timeout(properties.timeout())
			.build();
		return OpenAiChatModel.builder()
			.openAiClient(client)
			.openAiClientAsync(clientAsync)
			.options(OpenAiChatOptions.builder().model(properties.model()).build())
			.build();
	}

	private static Duration connectTimeout(Duration timeout) {
		Duration max = Duration.ofSeconds(30);
		return timeout.compareTo(max) < 0 ? timeout : max;
	}

}
