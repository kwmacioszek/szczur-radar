package pl.szczurradar.api.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import pl.szczurradar.api.agents.assessor.validation.OutputParser;
import pl.szczurradar.api.config.LlmProviderProperties;
import pl.szczurradar.api.config.LlmProviderProperties.Provider;
import pl.szczurradar.api.config.LlmProviderProperties.ServiceTier;
import pl.szczurradar.api.llm.ScriptedProviderServer.Reply;

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
			assertThat(response.channel()).isEqualTo("gemini");
			assertThat(response.outputMode()).isEqualTo(OutputMode.JSON_SCHEMA);
			assertThat(response.inputTokens()).isEqualTo(112);
			assertThat(response.outputTokens()).isEqualTo(278);
			assertThat(response.attempts()).isEqualTo(1);

			assertThat(server.requests()).singleElement().satisfies(request -> {
				assertThat(request.path()).endsWith("/models/gemini-3.1-flash-lite:generateContent");
				JsonNode body = MAPPER.readTree(request.body());
				JsonNode config = body.get("generationConfig");
				assertThat(config.get("responseMimeType").asString()).isEqualTo("application/json");
				assertThat(config.get("temperature").asDouble()).isZero();
				assertThat(config.at("/thinkingConfig/includeThoughts").asBoolean(true)).isFalse();
				String schema = config.get("responseJsonSchema").toString();
				// Spring AI maps the schema through GenAI's Schema type, which has no additionalProperties
				// or uniqueItems: Gemini never sees them (OutputParser still enforces both).
				assertThat(schema).doesNotContain("minItems", "maxItems", "$ref")
					.contains("\"maxLength\":100", "\"required\":[\"id\",\"kind\",\"anchor\",\"stance\",\"reference\"]");
				assertThat(new ArrayList<>(config.at("/responseJsonSchema/properties/targets/items/properties")
					.propertyNames())).containsExactly("id", "kind", "anchor", "stance", "reference");
				assertThat(body.at("/systemInstruction/parts/0/text").asString()).isEqualTo("System prompt.");
				assertThat(body.at("/contents/0/parts/0/text").asString()).isEqualTo("{\"item\":1}");
			});
		}
	}

	@Test
	void thoughtPartsAreNotPartOfTheText() {
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
		String blocked = "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"},\"modelVersion\":\"gemini-3.1-flash-lite\","
				+ "\"usageMetadata\":{\"promptTokenCount\":112,\"totalTokenCount\":112}}";
		try (ScriptedProviderServer server = new ScriptedProviderServer(Reply.ok(blocked), Reply.ok(SUCCESS))) {
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

	private LlmClient client(ScriptedProviderServer server, Duration timeout) {
		return LlmClientFactory.create(new LlmProviderProperties(Provider.GEMINI, "gemini-3.1-flash-lite",
				"synthetic-key", ServiceTier.DEFAULT, null, timeout), server.baseUrl(), sleeps::add);
	}

}
