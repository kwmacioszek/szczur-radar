package pl.szczurradar.api.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

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
			assertThat(response.channel()).isEqualTo(Channel.DEEPSEEK);
			assertThat(response.outputMode()).isEqualTo(OutputMode.JSON_MODE);
			assertThat(response.serviceTier()).isEqualTo(ServiceTier.DEFAULT);
			assertThat(response.inputTokens()).isEqualTo(696);
			assertThat(response.outputTokens()).isEqualTo(141);
			assertThat(response.cachedTokens()).isEqualTo(512);
			assertThat(response.attempts()).isEqualTo(1);

			assertThat(server.requests()).singleElement().satisfies(request -> {
				assertThat(request.path()).isEqualTo("/chat/completions");
				JsonNode body = MAPPER.readTree(request.body());
				assertThat(body.at("/model").asString()).isEqualTo("deepseek-v4-flash");
				assertThat(body.at("/response_format/type").asString()).isEqualTo("json_object");
				assertThat(body.at("/thinking/type").asString()).isEqualTo("disabled");
				assertThat(body.at("/temperature").asDouble()).isZero();
				assertThat(body.at("/max_tokens").asInt()).isEqualTo(LlmClient.MAX_OUTPUT_TOKENS);
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

	@Test
	void unauthorizedFailsAfterOneRequestWithoutTheKeyInTheMessage() {
		try (ScriptedProviderServer server = new ScriptedProviderServer(Reply.status(401,
				"{\"error\":{\"message\":\"Authentication Fails, Your api key: synthetic-key is invalid\"}}"))) {
			assertThatExceptionOfType(LlmCallException.class)
				.isThrownBy(() -> client(server, Duration.ofSeconds(5)).complete(REQUEST))
				.satisfies(ex -> {
					assertThat(ex.attempts()).isEqualTo(1);
					assertThat(ex.getMessage()).contains("HTTP 401", "<hidden>").doesNotContain("synthetic-key");
				});
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "", " \n", "\u00a0\u2007" })
	void blankTextWithFinishReasonStopIsAProviderErrorWithoutRetry(String blank) {
		// The real payload (finish reason stop) with its answer text replaced by white space of the
		// contract's set — U+00A0 and U+2007 are not blank for String.isBlank.
		ObjectNode empty = (ObjectNode) MAPPER.readTree(SUCCESS);
		((ObjectNode) empty.at("/choices/0/message")).put("content", blank);
		try (ScriptedProviderServer server = new ScriptedProviderServer(Reply.ok(empty.toString()),
				Reply.ok(SUCCESS))) {
			assertThatExceptionOfType(LlmCallException.class)
				.isThrownBy(() -> client(server, Duration.ofSeconds(5)).complete(REQUEST))
				.satisfies(ex -> {
					assertThat(ex.failure()).isEqualTo(CallFailure.PROVIDER_ERROR);
					assertThat(ex.attempts()).isEqualTo(1);
					assertThat(ex.getMessage()).contains("no text", "stop");
				});
			assertThat(server.requests()).hasSize(1);
		}
	}

	@Test
	void answerWithoutChoicesIsAProviderErrorWithoutRetry() {
		// The real payload with its choices removed.
		ObjectNode empty = (ObjectNode) MAPPER.readTree(SUCCESS);
		empty.putArray("choices");
		try (ScriptedProviderServer server = new ScriptedProviderServer(Reply.ok(empty.toString()), Reply.ok(SUCCESS))) {
			assertThatExceptionOfType(LlmCallException.class)
				.isThrownBy(() -> client(server, Duration.ofSeconds(5)).complete(REQUEST))
				.satisfies(ex -> {
					assertThat(ex.failure()).isEqualTo(CallFailure.PROVIDER_ERROR);
					assertThat(ex.attempts()).isEqualTo(1);
				});
			assertThat(server.requests()).hasSize(1);
		}
	}

	@Test
	void answerCutOffByTheServerIsRetried() {
		for (String reason : List.of("insufficient_system_resource", "aborted")) {
			// The real payload as DeepSeek ends an answer it could not finish (api-docs.deepseek.com).
			ObjectNode cutOff = (ObjectNode) MAPPER.readTree(SUCCESS);
			((ObjectNode) cutOff.at("/choices/0")).put("finish_reason", reason);
			sleeps.clear();
			try (ScriptedProviderServer server = new ScriptedProviderServer(Reply.ok(cutOff.toString()),
					Reply.ok(SUCCESS))) {
				LlmResponse response = client(server, Duration.ofSeconds(5)).complete(REQUEST);

				assertThat(response.attempts()).as(reason).isEqualTo(2);
				assertThat(server.requests()).as(reason).hasSize(2);
				assertThat(sleeps).as(reason).containsExactly(Duration.ofSeconds(1));
			}
		}
	}

	private LlmClient client(ScriptedProviderServer server, Duration timeout) {
		return client(server.baseUrl(), timeout);
	}

	@Test
	void refusedConnectionIsRetriedThreeTimes() {
		assertThatExceptionOfType(LlmCallException.class)
			.isThrownBy(() -> client(ScriptedProviderServer.refusingBaseUrl(), Duration.ofSeconds(5)).complete(REQUEST))
			.satisfies(ex -> {
				assertThat(ex.failure()).isEqualTo(CallFailure.PROVIDER_ERROR);
				assertThat(ex.attempts()).isEqualTo(3);
			});
		assertThat(sleeps).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2));
	}

	private LlmClient client(String baseUrl, Duration timeout) {
		return LlmClientFactory.create(new LlmProviderProperties(Provider.DEEPSEEK, "deepseek-v4-flash",
				"synthetic-key", ServiceTier.DEFAULT, null, timeout), baseUrl, sleeps::add);
	}

}
