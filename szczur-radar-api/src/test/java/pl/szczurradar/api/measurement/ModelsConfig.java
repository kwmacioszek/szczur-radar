package pl.szczurradar.api.measurement;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import org.jspecify.annotations.Nullable;

import pl.szczurradar.api.agents.assessor.contract.ContractJson;
import pl.szczurradar.api.llm.LlmProviderProperties;

/**
 * {@code fixtures/llm/models.json}: the measured model configurations, their prices and the default
 * one chosen from the gold report. A run with {@code LLM_*} belongs to the configuration with the same
 * provider, model, tier and reasoning effort.
 *
 * @param defaultId the configuration S-02 uses ({@code null} until the user chooses it)
 * @param pricesCheckedAt when the prices were read from the sources
 * @param priceSources where the prices come from
 */
public record ModelsConfig(@JsonProperty("default") @Nullable String defaultId, String pricesCheckedAt,
		List<String> priceSources, List<Model> models) {

	/** The file, relative to {@code szczur-radar-api/}. */
	public static final Path FILE = Path.of("src/test/resources/fixtures/llm/models.json");

	public ModelsConfig {
		models = List.copyOf(models);
		if (defaultId != null && models.stream().noneMatch(model -> model.id().equals(defaultId))) {
			throw new IllegalArgumentException("default '" + defaultId + "' is not a configured model");
		}
	}

	/** What a configuration is measured for. */
	public enum Role {

		/** First choice for S-02, if it meets the thresholds. */
		PREFERRED,

		/** Chosen if the preferred one does not meet the thresholds. */
		FALLBACK,

		/** Point of reference, not a candidate. */
		REFERENCE,

		/** An exploratory variant, measured on a sample first; may become the default after a full run. */
		CANDIDATE;

		@JsonValue
		public String value() {
			return name().toLowerCase(Locale.ROOT);
		}

	}

	/**
	 * One measured configuration.
	 *
	 * @param prices USD per 1M tokens by billing channel: the recorded {@code channel} for OpenRouter
	 *     ({@code openai/flex}, {@code openai}) and Gemini ({@code gemini}); DeepSeek bills by time of day
	 *     ({@code peak}, {@code off_peak}), so its report shows both
	 */
	public record Model(String id, Role role, String provider, String model, String serviceTier,
			@Nullable String reasoningEffort, Map<String, Price> prices) {

		public Model {
			Objects.requireNonNull(id, "id");
			prices = Map.copyOf(prices);
		}

		/** Whether a run with these {@code LLM_*} settings belongs to this configuration. */
		public boolean matches(LlmProviderProperties properties) {
			String effort = properties.reasoningEffort() == null ? null : properties.reasoningEffort().id();
			return provider.equals(properties.provider().id()) && model.equals(properties.model())
					&& serviceTier.equals(properties.serviceTier().id()) && Objects.equals(reasoningEffort, effort);
		}

	}

	/** USD per 1M tokens; {@code input} is charged on the prompt tokens not served from the cache. */
	public record Price(double input, double cachedInput, double output) {

		/** Cost in USD of one call; missing counts are 0 (no cache reported = no cached tokens). */
		public double cost(@Nullable Integer inputTokens, @Nullable Integer cachedTokens, @Nullable Integer outputTokens) {
			int all = inputTokens == null ? 0 : inputTokens;
			int cached = cachedTokens == null ? 0 : Math.min(cachedTokens, all);
			int out = outputTokens == null ? 0 : outputTokens;
			return ((all - cached) * input + cached * cachedInput + out * output) / 1_000_000;
		}

	}

	public Optional<Model> model(String id) {
		return models.stream().filter(model -> model.id().equals(id)).findFirst();
	}

	/** The configuration of a run with these settings; empty when none is configured. */
	public Optional<Model> matching(LlmProviderProperties properties) {
		return models.stream().filter(model -> model.matches(properties)).findFirst();
	}

	public static ModelsConfig read(Path path) throws IOException {
		return ContractJson.MAPPER.readValue(Files.readString(path), ModelsConfig.class);
	}

}
