package pl.szczurradar.api.llm;

import org.jspecify.annotations.Nullable;

import pl.szczurradar.api.llm.deepseek.DeepSeekClient;
import pl.szczurradar.api.llm.gemini.GeminiClient;
import pl.szczurradar.api.llm.openrouter.OpenRouterClient;
import pl.szczurradar.api.llm.transport.ProviderMessages;
import pl.szczurradar.api.llm.transport.TransportRetry;

/**
 * Builds the {@link LlmClient} of the configured provider from {@code LLM_*}, outside Embabel's model
 * beans. Each provider's package builds its own SDK client with every retry below
 * {@link TransportRetry} switched off and the {@code LLM_TIMEOUT} limit on every HTTP attempt.
 */
public final class LlmClientFactory {

	private LlmClientFactory() {
	}

	public static LlmClient create(LlmProviderProperties properties) {
		return create(properties, null, TransportRetry.Sleeper.REAL);
	}

	/**
	 * The test seam of {@link #create(LlmProviderProperties)}.
	 *
	 * @param baseUrl the provider API root instead of the real one (a local server), {@code null} = real
	 * @param sleeper waits between attempts (tests record the waits instead)
	 */
	public static LlmClient create(LlmProviderProperties properties, @Nullable String baseUrl,
			TransportRetry.Sleeper sleeper) {
		TransportRetry retry = new TransportRetry(sleeper);
		ProviderMessages messages = new ProviderMessages(properties.apiKey());
		return switch (properties.provider()) {
			case GEMINI -> GeminiClient.create(properties, baseUrl, retry, messages);
			case DEEPSEEK -> DeepSeekClient.create(properties, baseUrl, retry, messages);
			case OPENROUTER -> OpenRouterClient.create(properties, baseUrl, retry, messages);
		};
	}

}
