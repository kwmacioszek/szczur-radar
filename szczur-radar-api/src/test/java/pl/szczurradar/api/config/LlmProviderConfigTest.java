package pl.szczurradar.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * The provider mapping in application.properties and application-&lt;provider&gt;.properties, for
 * each of the three providers, on synthetic settings: no LLM call and no Spring application
 * context of the real app (only its configuration files are loaded). The selected settings are
 * passed as command-line arguments, which outrank any real LLM_* variables of the machine.
 */
class LlmProviderConfigTest {

	private static final String GEMINI = "com.embabel.agent.autoconfigure.models.googlegenai.AgentGoogleGenAiAutoConfiguration";

	private static final String DEEPSEEK = "com.embabel.agent.autoconfigure.models.deepseek.AgentDeepSeekAutoConfiguration";

	private static final String OPENROUTER = "com.embabel.agent.autoconfigure.models.openai.custom.AgentOpenAiCustomAutoConfiguration";

	@Test
	void geminiUsesTheGoogleAiStudioKeyAndOnlyGoogleGenAi() {
		ConfigurableEnvironment env = environment("gemini", "gemini-3.1-flash-lite", "synthetic-gemini-key");

		assertThat(env.getProperty("embabel.agent.platform.models.googlegenai.api-key")).isEqualTo("synthetic-gemini-key");
		assertThat(env.getProperty("embabel.models.default-llm")).isEqualTo("gemini-3.1-flash-lite");
		assertThat(excluded(env)).containsExactlyInAnyOrder(DEEPSEEK, OPENROUTER);
		assertThat(env.getActiveProfiles()).containsExactly("gemini");
	}

	@Test
	void deepseekUsesTheDeepSeekEndpointAndOnlyDeepSeek() {
		ConfigurableEnvironment env = environment("deepseek", "deepseek-v4-flash", "synthetic-deepseek-key");

		assertThat(env.getProperty("embabel.agent.platform.models.deepseek.api-key")).isEqualTo("synthetic-deepseek-key");
		assertThat(env.getProperty("embabel.agent.platform.models.deepseek.base-url")).isEqualTo("https://api.deepseek.com");
		assertThat(env.getProperty("embabel.models.default-llm")).isEqualTo("deepseek-v4-flash");
		assertThat(excluded(env)).containsExactlyInAnyOrder(GEMINI, OPENROUTER);
		assertThat(env.getActiveProfiles()).containsExactly("deepseek");
	}

	@Test
	void openrouterRegistersExactlyTheSelectedModelOnTheOpenRouterApi() {
		ConfigurableEnvironment env = environment("openrouter", "openai/gpt-6-luna", "synthetic-openrouter-key");

		assertThat(env.getProperty("embabel.agent.platform.models.openai.custom.api-key")).isEqualTo("synthetic-openrouter-key");
		assertThat(env.getProperty("embabel.agent.platform.models.openai.custom.base-url")).isEqualTo("https://openrouter.ai/api/v1");
		assertThat(env.getProperty("embabel.agent.platform.models.openai.custom.models")).isEqualTo("openai/gpt-6-luna");
		assertThat(env.getProperty("embabel.models.default-llm")).isEqualTo("openai/gpt-6-luna");
		assertThat(excluded(env)).containsExactlyInAnyOrder(GEMINI, DEEPSEEK);
		assertThat(env.getActiveProfiles()).containsExactly("openrouter");
	}

	/**
	 * The OpenAI SDK used by Embabel's OpenAI-compatible models appends {@code chat/completions} to
	 * the base URL. Sent to a local server with the configured base path, the request must land on
	 * {@code /api/v1/chat/completions}, i.e. https://openrouter.ai/api/v1/chat/completions.
	 */
	@Test
	void openrouterCompletionsGoToApiV1ChatCompletionsWithoutASecondV1() throws Exception {
		URI configured = URI.create(environment("openrouter", "openai/gpt-6-luna", "synthetic-key")
			.getProperty("embabel.agent.platform.models.openai.custom.base-url"));
		List<String> paths = new CopyOnWriteArrayList<>();
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			paths.add(exchange.getRequestURI().getPath());
			exchange.sendResponseHeaders(418, -1);
			exchange.close();
		});
		server.start();
		try {
			OpenAIClient client = OpenAIOkHttpClient.builder()
				.baseUrl("http://127.0.0.1:" + server.getAddress().getPort() + configured.getPath())
				.apiKey("synthetic-key")
				.maxRetries(0)
				.build();
			try {
				client.chat()
					.completions()
					.create(ChatCompletionCreateParams.builder().model("openai/gpt-6-luna").addUserMessage("ping").build());
			}
			catch (RuntimeException expected) {
				// The local server answers 418; only the request path matters here.
			}
		}
		finally {
			server.stop(0);
		}

		assertThat(configured.getHost()).isEqualTo("openrouter.ai");
		assertThat(paths).containsExactly("/api/v1/chat/completions");
	}

	@Test
	void unknownProviderFailsTheStartWithoutRevealingTheKey() {
		assertThatIllegalStateException().isThrownBy(() -> environment("claude", "some-model", "very-secret-key"))
			.withMessageContaining("LLM_API_PROVIDER must be one of gemini, deepseek, openrouter (got 'claude')")
			.withMessageNotContaining("very-secret-key");
	}

	@Test
	void missingOrBlankKeyFailsTheStart() {
		assertThatIllegalStateException().isThrownBy(() -> environment("deepseek", "deepseek-v4-flash", " "))
			.withMessageContaining("LLM_API_KEY is not set");
		assertThatIllegalStateException().isThrownBy(() -> environment("deepseek", "deepseek-v4-flash", null))
			.withMessageContaining("LLM_API_KEY is not set");
	}

	@Test
	void missingOrBlankModelFailsTheStart() {
		assertThatIllegalStateException().isThrownBy(() -> environment("gemini", "", "synthetic-key"))
			.withMessageContaining("LLM_MODEL is not set");
		assertThatIllegalStateException().isThrownBy(() -> environment("gemini", null, "synthetic-key"))
			.withMessageContaining("LLM_MODEL is not set");
	}

	@Test
	void propertiesNeverPrintTheKey() {
		LlmProviderProperties properties = new LlmProviderProperties(LlmProviderProperties.Provider.DEEPSEEK,
				"deepseek-v4-flash", "very-secret-key");

		assertThat(properties.toString()).doesNotContain("very-secret-key").contains("<hidden>");
	}

	/**
	 * Starts only the configuration of the application (its property files and the startup check),
	 * with the given settings; {@code null} means "set to empty", overriding any real variable.
	 */
	private static ConfigurableEnvironment environment(String provider, String model, String apiKey) {
		List<String> args = new ArrayList<>();
		for (Map.Entry<String, String> setting : Map.of(LlmProviderProperties.PROVIDER, provider,
				LlmProviderProperties.MODEL, model == null ? "" : model, LlmProviderProperties.API_KEY,
				apiKey == null ? "" : apiKey).entrySet()) {
			args.add("--" + setting.getKey() + "=" + setting.getValue());
		}
		SpringApplication application = new SpringApplication(ConfigurationOnly.class);
		application.setWebApplicationType(WebApplicationType.NONE);
		try (ConfigurableApplicationContext context = application.run(args.toArray(String[]::new))) {
			return context.getEnvironment();
		}
	}

	private static List<String> excluded(ConfigurableEnvironment env) {
		return Arrays.stream(env.getProperty("spring.autoconfigure.exclude", String[].class)).map(String::strip).toList();
	}

	/** No beans, no auto-configuration: only the environment is of interest. */
	@Configuration(proxyBeanMethods = false)
	static class ConfigurationOnly {

	}

}
