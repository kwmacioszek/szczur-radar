package pl.szczurradar.api.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.core.env.PropertyResolver;

/**
 * The application's LLM configuration contract: exactly one provider per run, chosen by three
 * environment settings that are always changed together.
 *
 * <ul>
 *   <li>{@code LLM_API_PROVIDER}: {@code gemini} (Google AI Studio, native Google GenAI),
 *       {@code deepseek} (DeepSeek directly) or {@code openrouter} (OpenAI-compatible API);
 *   <li>{@code LLM_MODEL}: the exact model id of that provider;
 *   <li>{@code LLM_API_KEY}: a real key of that provider (a secret: never logged or echoed).
 * </ul>
 *
 * <p>The provider name also activates the Spring profile {@code application-<provider>.properties},
 * which maps these settings onto Embabel's properties and switches the other providers off.
 */
public record LlmProviderProperties(Provider provider, String model, String apiKey) {

	public static final String PROVIDER = "LLM_API_PROVIDER";

	public static final String MODEL = "LLM_MODEL";

	public static final String API_KEY = "LLM_API_KEY";

	/** The supported providers; {@link #id()} is the {@code LLM_API_PROVIDER} value and profile name. */
	public enum Provider {

		GEMINI, DEEPSEEK, OPENROUTER;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		static @Nullable Provider fromId(String id) {
			return Arrays.stream(values()).filter(provider -> provider.id().equals(id)).findFirst().orElse(null);
		}

	}

	/** Never prints the key. */
	@Override
	public String toString() {
		return "LlmProviderProperties[provider=" + provider.id() + ", model=" + model + ", apiKey=<hidden>]";
	}

	/**
	 * Reads and validates the three settings.
	 *
	 * @throws IllegalStateException listing every problem; the message never contains the key
	 */
	public static LlmProviderProperties from(PropertyResolver environment) {
		String providerId = trimmed(environment.getProperty(PROVIDER));
		String model = trimmed(environment.getProperty(MODEL));
		String apiKey = trimmed(environment.getProperty(API_KEY));

		List<String> problems = new ArrayList<>();
		Provider provider = Provider.fromId(providerId);
		if (provider == null) {
			String allowed = Arrays.stream(Provider.values()).map(Provider::id).collect(Collectors.joining(", "));
			problems.add(PROVIDER + " must be one of " + allowed
					+ (providerId.isEmpty() ? " (not set)" : " (got '" + providerId + "')"));
		}
		if (model.isEmpty()) {
			problems.add(MODEL + " is not set (the exact model id of the selected provider)");
		}
		if (apiKey.isEmpty()) {
			problems.add(API_KEY + " is not set (a real key of the selected provider)");
		}
		if (!problems.isEmpty()) {
			throw new IllegalStateException("Invalid LLM configuration: " + String.join("; ", problems)
					+ ". See szczur-radar-api/README.md (LLM provider).");
		}
		return new LlmProviderProperties(provider, model, apiKey);
	}

	private static String trimmed(@Nullable String value) {
		return value == null ? "" : value.strip();
	}

}
