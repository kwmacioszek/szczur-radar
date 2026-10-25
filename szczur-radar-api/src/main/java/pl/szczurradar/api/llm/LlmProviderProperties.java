package pl.szczurradar.api.llm;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.core.env.PropertyResolver;

import pl.szczurradar.api.settings.DurationSetting;
import pl.szczurradar.api.settings.InvalidSettingsException;
import pl.szczurradar.api.settings.SettingText;

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
 * <p>Optional call settings of the assessment client ({@code llm/}), never secrets:
 *
 * <ul>
 *   <li>{@code LLM_SERVICE_TIER}: {@code default} or {@code flex} (cheaper, may be refused with 429;
 *       only {@code openrouter}, which routes it to OpenAI's flex tier);
 *   <li>{@code LLM_REASONING_EFFORT}: {@code none}, {@code minimal}, {@code low}, {@code medium},
 *       {@code high} or {@code xhigh} (only {@code openrouter}); unset = the provider's default;
 *   <li>{@code LLM_TIMEOUT}: time limit of one HTTP attempt, by the one duration rule
 *       ({@link DurationSetting}: a whole number with a lower-case unit, e.g. {@code 120s} or {@code 5m};
 *       a bare number or ISO-8601 is refused), from {@code 10s} to {@code 30m} (default {@code 120s};
 *       flex is slower, give it more).
 * </ul>
 *
 * <p>An invalid value fails the start of the server and of every operator command (also the pre-deploy
 * {@code migrate}) with an {@link InvalidSettingsException}: Spring Boot's
 * {@code APPLICATION FAILED TO START} block, exit code 2 for a command.
 */
public record LlmProviderProperties(Provider provider, String model, String apiKey, ServiceTier serviceTier,
		@Nullable ReasoningEffort reasoningEffort, Duration timeout) {

	public static final String PROVIDER = "LLM_API_PROVIDER";

	public static final String MODEL = "LLM_MODEL";

	public static final String API_KEY = "LLM_API_KEY";

	public static final String SERVICE_TIER = "LLM_SERVICE_TIER";

	public static final String REASONING_EFFORT = "LLM_REASONING_EFFORT";

	public static final String TIMEOUT = "LLM_TIMEOUT";

	public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(120);

	/** The shortest accepted {@code LLM_TIMEOUT}: a shorter one is a slip (e.g. a forgotten unit), not a limit. */
	public static final Duration MIN_TIMEOUT = Duration.ofSeconds(10);

	/** The longest accepted {@code LLM_TIMEOUT}; the Gemini SDK takes it as {@code int} milliseconds. */
	public static final Duration MAX_TIMEOUT = Duration.ofMinutes(30);

	/** The three required settings with default call settings. */
	public LlmProviderProperties(Provider provider, String model, String apiKey) {
		this(provider, model, apiKey, ServiceTier.DEFAULT, null, DEFAULT_TIMEOUT);
	}

	/** Never prints the key. */
	@Override
	public String toString() {
		return "LlmProviderProperties[provider=" + provider.id() + ", model=" + model + ", apiKey=<hidden>, serviceTier="
				+ serviceTier.id() + ", reasoningEffort=" + (reasoningEffort == null ? "<default>" : reasoningEffort.id())
				+ ", timeout=" + timeout + "]";
	}

	/**
	 * Reads and validates the settings.
	 *
	 * @throws InvalidSettingsException listing every problem; the message never contains the key
	 */
	public static LlmProviderProperties from(PropertyResolver environment) {
		String providerId = SettingText.trimmed(environment.getProperty(PROVIDER));
		String model = SettingText.trimmed(environment.getProperty(MODEL));
		String apiKey = SettingText.trimmed(environment.getProperty(API_KEY));
		String serviceTierId = SettingText.trimmed(environment.getProperty(SERVICE_TIER));
		String reasoningEffortId = SettingText.trimmed(environment.getProperty(REASONING_EFFORT));
		String timeoutText = SettingText.trimmed(environment.getProperty(TIMEOUT));

		List<String> problems = new ArrayList<>();
		Provider provider = oneOf(Provider.values(), Provider::id, PROVIDER, providerId, problems);
		if (model.isEmpty()) {
			problems.add(MODEL + " is not set (the exact model id of the selected provider)");
		}
		if (apiKey.isEmpty()) {
			problems.add(API_KEY + " is not set (a real key of the selected provider)");
		}
		ServiceTier serviceTier = serviceTierId.isEmpty() ? ServiceTier.DEFAULT
				: oneOf(ServiceTier.values(), ServiceTier::id, SERVICE_TIER, serviceTierId, problems);
		if (serviceTier == ServiceTier.FLEX && provider != null && provider != Provider.OPENROUTER) {
			problems.add(SERVICE_TIER + "=flex is only supported for " + PROVIDER + "=openrouter (got '" + provider.id()
					+ "')");
		}
		ReasoningEffort reasoningEffort = reasoningEffortId.isEmpty() ? null
				: oneOf(ReasoningEffort.values(), ReasoningEffort::id, REASONING_EFFORT, reasoningEffortId, problems);
		if (reasoningEffort != null && provider != null && provider != Provider.OPENROUTER) {
			problems.add(REASONING_EFFORT + " is only supported for " + PROVIDER + "=openrouter (got '" + provider.id()
					+ "')");
		}
		Duration timeout = timeoutText.isEmpty() ? DEFAULT_TIMEOUT
				: DurationSetting.parse(TIMEOUT, timeoutText, MIN_TIMEOUT, MAX_TIMEOUT, problems);
		if (!problems.isEmpty()) {
			throw new InvalidSettingsException("Invalid LLM configuration: " + String.join("; ", problems)
					+ ". See szczur-radar-api/README.md (LLM provider).");
		}
		return new LlmProviderProperties(provider, model, apiKey, serviceTier, reasoningEffort, timeout);
	}

	private static <E extends Enum<E>> @Nullable E oneOf(E[] values, Function<E, String> id,
			String setting, String value, List<String> problems) {
		for (E candidate : values) {
			if (id.apply(candidate).equals(value)) {
				return candidate;
			}
		}
		String allowed = Arrays.stream(values).map(id).collect(Collectors.joining(", "));
		problems.add(setting + " must be one of " + allowed
				+ (value.isEmpty() ? " (not set)" : " (got '" + SettingText.echoed(value) + "')"));
		return null;
	}

}
