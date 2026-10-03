package pl.szczurradar.api.llm;

import org.jspecify.annotations.Nullable;

import pl.szczurradar.api.config.LlmProviderProperties.Provider;
import pl.szczurradar.api.config.LlmProviderProperties.ReasoningEffort;
import pl.szczurradar.api.config.LlmProviderProperties.ServiceTier;

/**
 * A completed model call: the raw text exactly as the model returned it, and where and how it was
 * produced.
 *
 * @param rawText the model's text, unparsed (thoughts excluded)
 * @param provider the API called ({@code LLM_API_PROVIDER})
 * @param model the model id the provider reports, or the requested one if it reports none
 * @param channel the route inside the provider: the provider id, or for OpenRouter with flex the
 *     upstream used ({@code openai/flex} or {@code openai})
 * @param serviceTier the tier of the successful attempt
 * @param flexFallback whether flex was refused and the call fell back to the default tier
 * @param reasoningEffort the requested reasoning effort, {@code null} = provider default
 * @param outputMode how the output shape was requested
 * @param inputTokens prompt tokens, {@code null} if not reported
 * @param outputTokens completion tokens, {@code null} if not reported
 * @param cachedTokens prompt tokens served from the provider's cache, {@code null} if not reported
 * @param attempts HTTP attempts made, including the successful one
 */
public record LlmResponse(String rawText, Provider provider, String model, String channel, ServiceTier serviceTier,
		boolean flexFallback, @Nullable ReasoningEffort reasoningEffort, OutputMode outputMode,
		@Nullable Integer inputTokens, @Nullable Integer outputTokens, @Nullable Integer cachedTokens, int attempts) {
}
