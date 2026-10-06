package pl.szczurradar.api.agents.assessor;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonValue;
import org.jspecify.annotations.Nullable;

import pl.szczurradar.api.config.LlmProviderProperties.Provider;
import pl.szczurradar.api.config.LlmProviderProperties.ReasoningEffort;
import pl.szczurradar.api.config.LlmProviderProperties.ServiceTier;
import pl.szczurradar.api.llm.Channel;
import pl.szczurradar.api.llm.OutputMode;

/**
 * Where an assessment came from (contract-v2.md §15), filled by the backend and stored next to the
 * model's output — never part of it.
 *
 * <p>For {@link Source#PREFILTER} (§11) there is no call: no provider, model, channel, tier, tokens or
 * prompt, {@code attempts = 0}. For a model call that produced no text the call fields the client
 * reports ({@code serviceTier}, {@code flexFallback}, {@code attempts}) are kept and the fields that
 * come with a response ({@code provider}, {@code model}, {@code channel}, {@code reasoningEffort},
 * {@code outputMode}, tokens) are {@code null}.
 *
 * @param source {@code model} or {@code prefilter}
 * @param provider the API called
 * @param model the model id the provider reported
 * @param channel the route inside the provider
 * @param serviceTier the tier of the last attempt
 * @param flexFallback whether flex was refused and the call fell back to the default tier
 * @param reasoningEffort the requested reasoning effort, {@code null} = provider default
 * @param outputMode how the output shape was requested
 * @param promptVersion the prompt's version, {@code null} for the prefilter
 * @param contractVersion always {@code V2}
 * @param assessedAt when the assessment was finished
 * @param inputTokens prompt tokens, {@code null} if not reported
 * @param outputTokens completion tokens, {@code null} if not reported
 * @param cachedTokens prompt tokens served from the provider's cache, {@code null} if not reported
 * @param durationMs wall time of the call (0 for the prefilter)
 * @param attempts HTTP attempts made
 */
public record Provenance(Source source, @Nullable Provider provider, @Nullable String model,
		@Nullable Channel channel, @Nullable ServiceTier serviceTier, boolean flexFallback,
		@Nullable ReasoningEffort reasoningEffort, @Nullable OutputMode outputMode, @Nullable String promptVersion,
		String contractVersion, Instant assessedAt, @Nullable Integer inputTokens, @Nullable Integer outputTokens,
		@Nullable Integer cachedTokens, long durationMs, int attempts) {

	/** What produced the output. */
	public enum Source {

		/** One model call. */
		MODEL,

		/** The no-content prefilter (§11), without a model call. */
		PREFILTER;

		@JsonValue
		public String value() {
			return name().toLowerCase(Locale.ROOT);
		}

	}

	public Provenance {
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(contractVersion, "contractVersion");
		Objects.requireNonNull(assessedAt, "assessedAt");
		if (source == Source.PREFILTER && (provider != null || model != null || channel != null || serviceTier != null
				|| flexFallback || reasoningEffort != null || outputMode != null || promptVersion != null
				|| inputTokens != null || outputTokens != null || cachedTokens != null || durationMs != 0
				|| attempts != 0)) {
			throw new IllegalArgumentException("a prefilter result has no call details");
		}
		if (source == Source.MODEL && (promptVersion == null || serviceTier == null || attempts < 1)) {
			throw new IllegalArgumentException("a model result needs a prompt version, a tier and an attempt");
		}
	}

	/** The provenance of the no-content prefilter's output (§11). */
	public static Provenance prefilter(String contractVersion, Instant assessedAt) {
		return new Provenance(Source.PREFILTER, null, null, null, null, false, null, null, null, contractVersion,
				assessedAt, null, null, null, 0, 0);
	}

}
