package pl.szczurradar.api.llm.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import pl.szczurradar.api.agents.assessor.validation.OutputParser;
import pl.szczurradar.api.llm.CallFailure;
import pl.szczurradar.api.llm.Channel;
import pl.szczurradar.api.llm.LlmCallException;
import pl.szczurradar.api.llm.LlmClient;
import pl.szczurradar.api.llm.LlmClientFactory;
import pl.szczurradar.api.llm.LlmConfigurationException;
import pl.szczurradar.api.llm.LlmProviderProperties;
import pl.szczurradar.api.llm.LlmRequest;
import pl.szczurradar.api.llm.LlmResponse;
import pl.szczurradar.api.llm.LlmUnavailableException;
import pl.szczurradar.api.llm.OutputMode;
import pl.szczurradar.api.llm.Provider;
import pl.szczurradar.api.llm.ScriptedProviderServer;
import pl.szczurradar.api.llm.ScriptedProviderServer.Reply;
import pl.szczurradar.api.llm.ServiceTier;
import pl.szczurradar.api.llm.transport.ProviderMessages;
import pl.szczurradar.api.llm.transport.TransportRetry;

/**
 * The Gemini client against a local server replaying a real Google AI Studio response
 * ({@code fixtures/gemini/generate-content.json}).
 */
class GeminiClientTest {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private static final String SUCCESS = ScriptedProviderServer.fixture("gemini/generate-content.json");

	private static final LlmRequest REQUEST = new LlmRequest("System prompt.", "{\"item\":1}",
			OutputParser.schemaText());

	private final List<Duration> sleeps = new ArrayList<>();

	@Test
	void sendsTheSchemaWithoutCountLimitsAndReadsTextAndUsage() {
		try (ScriptedProviderServer server = new ScriptedProviderServer(Reply.ok(SUCCESS))) {
			LlmResponse response = client(server, Duration.ofSeconds(5)).complete(REQUEST);

			assertThat(response.rawText())
				.isEqualTo(MAPPER.readTree(SUCCESS).at("/candidates/0/content/parts/0/text").asString());
			assertThat(response.provider()).isEqualTo(Provider.GEMINI);
			assertThat(response.model()).isEqualTo("gemini-3.1-flash-lite");
			assertThat(response.channel()).isEqualTo(Channel.GEMINI);
			assertThat(response.outputMode()).isEqualTo(OutputMode.JSON_SCHEMA);
			assertThat(response.inputTokens()).isEqualTo(112);
			assertThat(response.outputTokens()).isEqualTo(278);
			// The recording reports no cachedContentTokenCount.
			assertThat(response.cachedTokens()).isNull();
			assertThat(response.attempts()).isEqualTo(1);

			assertThat(server.requests()).singleElement().satisfies(request -> {
				assertThat(request.path()).endsWith("/models/gemini-3.1-flash-lite:generateContent");
				JsonNode body = MAPPER.readTree(request.body());
				JsonNode config = body.get("generationConfig");
				assertThat(config.get("responseMimeType").asString()).isEqualTo("application/json");
				assertThat(config.get("temperature").asDouble()).isZero();
				assertThat(config.get("maxOutputTokens").asInt()).isEqualTo(LlmClient.MAX_OUTPUT_TOKENS);
				assertThat(config.at("/thinkingConfig/includeThoughts").asBoolean(true)).isFalse();
				String schema = config.get("responseJsonSchema").toString();
				// Spring AI maps the schema through GenAI's Schema type, which has no additionalProperties
				// or uniqueItems: Gemini never sees them (OutputParser still enforces both).
				assertThat(schema).doesNotContain("minItems", "maxItems", "$ref")
					.contains("\"maxLength\":100", "\"required\":[\"id\",\"kind\",\"anchor\",\"stance\",\"reference\"]");
				// The nullable enum reaches Gemini as anyOf with {"type": "null"} (ProviderSchemas).
				assertThat(config.at("/responseJsonSchema/properties/targets/items/properties/stance/properties/assertiveness")
					.toString()).isEqualTo("{\"anyOf\":[{\"enum\":[\"hedged\",\"unhedged\"],\"type\":\"string\"},{\"type\":\"null\"}]}");
				assertThat(new ArrayList<>(config.at("/responseJsonSchema/properties/targets/items/properties")
					.propertyNames())).containsExactly("id", "kind", "anchor", "stance", "reference");
				assertThat(body.at("/systemInstruction/parts/0/text").asString()).isEqualTo("System prompt.");
				assertThat(body.at("/contents/0/parts/0/text").asString()).isEqualTo("{\"item\":1}");
			});
		}
	}

	@Test
	void thoughtPartsAreNotPartOfTheText() {
		// The real payload with one thought part put in front of the answer.
		ObjectNode withThought = (ObjectNode) MAPPER.readTree(SUCCESS);
		ArrayNode parts = (ArrayNode) withThought.at("/candidates/0/content/parts");
		parts.insert(0, MAPPER.createObjectNode().put("text", "Let me think…").put("thought", true));
		try (ScriptedProviderServer server = new ScriptedProviderServer(Reply.ok(withThought.toString()))) {
			LlmResponse response = client(server, Duration.ofSeconds(5)).complete(REQUEST);

			assertThat(response.rawText()).doesNotContain("Let me think")
				.isEqualTo(MAPPER.readTree(SUCCESS).at("/candidates/0/content/parts/0/text").asString());
		}
	}

	@Test
	void blockedAnswerIsAProviderErrorWithoutRetry() {
		// The real payload as Gemini returns a blocked prompt: no candidates, a block reason instead.
		ObjectNode blocked = (ObjectNode) MAPPER.readTree(SUCCESS);
		blocked.remove("candidates");
		blocked.putObject("promptFeedback").put("blockReason", "SAFETY");
		try (ScriptedProviderServer server = new ScriptedProviderServer(Reply.ok(blocked.toString()),
				Reply.ok(SUCCESS))) {
			assertThatExceptionOfType(LlmCallException.class)
				.isThrownBy(() -> client(server, Duration.ofSeconds(5)).complete(REQUEST))
				.satisfies(ex -> {
					assertThat(ex.failure()).isEqualTo(CallFailure.PROVIDER_ERROR);
					assertThat(ex.attempts()).isEqualTo(1);
				});
			assertThat(server.requests()).hasSize(1);
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "", " \n", "  " })
	void blankTextWithFinishReasonStopIsAProviderErrorWithoutRetry(String blank) {
		// The real payload (finish reason STOP) with its answer text replaced by white space of the
		// contract's set — U+00A0 and U+2007 are not blank for String.isBlank.
		ObjectNode empty = (ObjectNode) MAPPER.readTree(SUCCESS);
		((ObjectNode) empty.at("/candidates/0/content/parts/0")).put("text", blank);
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
	void rateLimitIsRetriedWithBackoff() {
		try (ScriptedProviderServer server = new ScriptedProviderServer(Reply.status(429, error(429, "RESOURCE_EXHAUSTED")),
				Reply.ok(SUCCESS))) {
			LlmResponse response = client(server, Duration.ofSeconds(5)).complete(REQUEST);

			assertThat(response.attempts()).isEqualTo(2);
			assertThat(server.requests()).hasSize(2);
			assertThat(sleeps).containsExactly(Duration.ofSeconds(1));
		}
	}

	@Test
	void serverErrorsStopAfterThreeRequests() {
		Reply error = Reply.status(503, error(503, "UNAVAILABLE"));
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
		try (ScriptedProviderServer server = new ScriptedProviderServer(Reply.status(400, error(400, "INVALID_ARGUMENT")),
				Reply.ok(SUCCESS))) {
			assertThatExceptionOfType(LlmCallException.class)
				.isThrownBy(() -> client(server, Duration.ofSeconds(5)).complete(REQUEST))
				.satisfies(ex -> {
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

	private static String error(int code, String status) {
		return "{\"error\":{\"code\":" + code + ",\"message\":\"test\",\"status\":\"" + status + "\"}}";
	}

	@Test
	void unauthorizedFailsAfterOneRequestWithoutTheKeyInTheMessage() {
		try (ScriptedProviderServer server = new ScriptedProviderServer(Reply.status(401,
				"{\"error\":{\"code\":401,\"message\":\"API key synthetic-key not valid\",\"status\":\"UNAUTHENTICATED\"}}"))) {
			assertThatExceptionOfType(LlmConfigurationException.class)
				.isThrownBy(() -> client(server, Duration.ofSeconds(5)).complete(REQUEST))
				.satisfies(ex -> {
					assertThat(ex.attempts()).isEqualTo(1);
					assertThat(ex.getMessage()).contains("HTTP 401", "<hidden>").doesNotContain("synthetic-key");
				});
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

	@Test
	void anUnknownSdkErrorLeavesTheClientUnclassifiedAfterOneCall() {
		// An SDK exception without I/O in its cause chain (synthetic; its message quotes the key): not a
		// transport failure, so neither a refusal nor retried, and the key does not reach the message.
		RuntimeException sdkError = new RuntimeException("unexpected state for the key synthetic-key");
		List<Prompt> calls = new ArrayList<>();
		LlmClient client = stubbed(prompt -> {
			calls.add(prompt);
			throw sdkError;
		});

		assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> client.complete(REQUEST))
			.satisfies(ex -> {
				assertThat(ex.getCause()).isSameAs(sdkError);
				assertThat(ex.getMessage()).contains("unexpected state", "<hidden>").doesNotContain("synthetic-key");
			});
		assertThat(calls).hasSize(1);
		assertThat(sleeps).isEmpty();
	}

	@Test
	void anSdkErrorCausedByIoIsAConnectionErrorAndRetried() {
		List<Prompt> calls = new ArrayList<>();
		LlmClient client = stubbed(prompt -> {
			calls.add(prompt);
			throw new RuntimeException("request failed", new IOException("connection reset"));
		});

		assertThatExceptionOfType(LlmUnavailableException.class).isThrownBy(() -> client.complete(REQUEST))
			.satisfies(ex -> {
				assertThat(ex.failure()).isEqualTo(CallFailure.PROVIDER_ERROR);
				assertThat(ex.attempts()).isEqualTo(3);
				assertThat(ex.getMessage()).contains("I/O error", "connection reset");
			});
		assertThat(calls).hasSize(3);
		assertThat(sleeps).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2));
	}

	/** The client on a stand-in for the SDK's chat model, with the test's key and recorded waits. */
	private LlmClient stubbed(ChatModel chatModel) {
		return new GeminiClient(chatModel, "gemini-3.1-flash-lite", new TransportRetry(sleeps::add),
				new ProviderMessages("synthetic-key"));
	}

	private LlmClient client(String baseUrl, Duration timeout) {
		return LlmClientFactory.create(new LlmProviderProperties(Provider.GEMINI, "gemini-3.1-flash-lite",
				"synthetic-key", ServiceTier.DEFAULT, null, timeout), baseUrl, sleeps::add);
	}

}
