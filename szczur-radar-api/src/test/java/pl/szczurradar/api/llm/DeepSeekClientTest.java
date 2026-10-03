package pl.szczurradar.api.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import pl.szczurradar.api.agents.assessor.validation.OutputParser;
import pl.szczurradar.api.config.LlmProviderProperties;
import pl.szczurradar.api.config.LlmProviderProperties.Provider;
import pl.szczurradar.api.config.LlmProviderProperties.ServiceTier;
import pl.szczurradar.api.llm.ScriptedProviderServer.Reply;

/**
 * The DeepSeek client against a local server replaying a real DeepSeek response
 * ({@code fixtures/deepseek/chat-completion.json}): what is sent, what is read, and that only our
 * loop retries.
 */
class DeepSeekClientTest {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private static final String SUCCESS = ScriptedProviderServer.fixture("deepseek/chat-completion.json");

	private static final LlmRequest REQUEST = new LlmRequest("System prompt.", "{\"item\":1}",
			OutputParser.schemaText());

	private final List<Duration> sleeps = new ArrayList<>();

	@Test
	void sendsJsonModeWithoutThinkingAndReadsTextAndUsage() {
		try (ScriptedProviderServer server = new ScriptedProviderServer(Reply.ok(SUCCESS))) {
			LlmResponse response = client(server, Duration.ofSeconds(5)).complete(REQUEST);

			assertThat(response.rawText()).isEqualTo(MAPPER.readTree(SUCCESS).at("/choices/0/message/content").asString());
			assertThat(response.provider()).isEqualTo(Provider.DEEPSEEK);
			assertThat(response.model()).isEqualTo("deepseek-flash");
			assertThat(response.channel()).isEqualTo("deepseek");
			assertThat(response.outputMode()).isEqualTo(OutputMode.JSON_MODE);
			assertThat(response.serviceTier()).isEqualTo(ServiceTier.DEFAULT);
			assertThat(response.inputTokens()).isEqualTo(825);
			assertThat(response.outputTokens()).isEqualTo(152);
			assertThat(response.cachedTokens()).isEqualTo(640);
			assertThat(response.attempts()).isEqualTo(1);

			assertThat(server.requests()).singleElement().satisfies(request -> {
				assertThat(request.path()).isEqualTo("/chat/completions");
				JsonNode body = MAPPER.readTree(request.body());
				assertThat(body.at("/model").asString()).isEqualTo("deepseek-v4-flash");
				assertThat(body.at("/response_format/type").asString()).isEqualTo("json_object");
				assertThat(body.at("/thinking/type").asString()).isEqualTo("disabled");
				assertThat(body.at("/temperature").asDouble()).isZero();
				String system = body.at("/messages/0/content").asString();
				assertThat(system).startsWith("System prompt." + DeepSeekClient.SCHEMA_INSTRUCTION)
					.contains("\"additionalProperties\":false")
					.doesNotContain("minLength", "maxLength", "minItems", "maxItems", "$ref");
				assertThat(body.at("/messages/1/content").asString()).isEqualTo("{\"item\":1}");
			});
		}
	}

	@Test
	void rateLimitWithRetryAfterIsRetriedOnce() {
		try (ScriptedProviderServer server = new ScriptedProviderServer(
				Reply.status(429, "{\"error\":{\"message\":\"rate limited\"}}").withHeader("Retry-After", "3"),
				Reply.ok(SUCCESS))) {
			LlmResponse response = client(server, Duration.ofSeconds(5)).complete(REQUEST);

			assertThat(response.attempts()).isEqualTo(2);
			assertThat(server.requests()).hasSize(2);
			assertThat(sleeps).containsExactly(Duration.ofSeconds(3));
		}
	}

	@Test
	void serverErrorsStopAfterThreeRequests() {
		Reply error = Reply.status(503, "{\"error\":\"busy\"}");
		try (ScriptedProviderServer server = new ScriptedProviderServer(error, error, error, error)) {
			assertThatExceptionOfType(LlmCallException.class)
				.isThrownBy(() -> client(server, Duration.ofSeconds(5)).complete(REQUEST))
				.satisfies(ex -> {
					assertThat(ex.failure()).isEqualTo(CallFailure.PROVIDER_ERROR);
					assertThat(ex.attempts()).isEqualTo(3);
				});
			assertThat(server.requests()).hasSize(3);
		}
	}

	@Test
	void badRequestFailsAfterOneRequest() {
		try (ScriptedProviderServer server = new ScriptedProviderServer(Reply.status(400, "{\"error\":\"bad\"}"),
				Reply.ok(SUCCESS))) {
			assertThatExceptionOfType(LlmCallException.class)
				.isThrownBy(() -> client(server, Duration.ofSeconds(5)).complete(REQUEST))
				.satisfies(ex -> {
					assertThat(ex.failure()).isEqualTo(CallFailure.PROVIDER_ERROR);
					assertThat(ex.attempts()).isEqualTo(1);
					assertThat(ex.getMessage()).contains("HTTP 400");
				});
			assertThat(server.requests()).hasSize(1);
		}
	}

	@Test
	void slowAnswersAreATimeoutAfterThreeRequests() {
		Reply slow = Reply.ok(SUCCESS).after(Duration.ofSeconds(2));
		try (ScriptedProviderServer server = new ScriptedProviderServer(slow, slow, slow, slow)) {
			assertThatExceptionOfType(LlmCallException.class)
				.isThrownBy(() -> client(server, Duration.ofMillis(300)).complete(REQUEST))
				.satisfies(ex -> {
					assertThat(ex.failure()).isEqualTo(CallFailure.TIMEOUT);
					assertThat(ex.attempts()).isEqualTo(3);
				});
			assertThat(server.requests()).hasSize(3);
		}
	}

	private LlmClient client(ScriptedProviderServer server, Duration timeout) {
		return LlmClientFactory.create(new LlmProviderProperties(Provider.DEEPSEEK, "deepseek-v4-flash",
				"synthetic-key", ServiceTier.DEFAULT, null, timeout), server.baseUrl(), sleeps::add);
	}

}
