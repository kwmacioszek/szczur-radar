package pl.szczurradar.api.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import pl.szczurradar.api.agents.assessor.validation.OutputParser;
import pl.szczurradar.api.config.LlmProviderProperties;
import pl.szczurradar.api.config.LlmProviderProperties.Provider;
import pl.szczurradar.api.config.LlmProviderProperties.ReasoningEffort;
import pl.szczurradar.api.config.LlmProviderProperties.ServiceTier;
import pl.szczurradar.api.llm.ScriptedProviderServer.Reply;

/**
 * The OpenRouter client against a local server replaying a real OpenRouter response for Luna on the
 * flex tier ({@code fixtures/openrouter/chat-completion.json}).
 */
class OpenRouterClientTest {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private static final String SUCCESS = ScriptedProviderServer.fixture("openrouter/chat-completion.json");

	private static final LlmRequest REQUEST = new LlmRequest("System prompt.", "{\"item\":1}",
			OutputParser.schemaText());

	private static final Reply RATE_LIMITED = Reply.status(429, "{\"error\":{\"message\":\"Rate limit exceeded\",\"code\":429}}");

	private final List<Duration> sleeps = new ArrayList<>();

	@Test
	void sendsStrictSchemaFlexRoutingAndReasoningEffortAndReadsTextAndUsage() {
		try (ScriptedProviderServer server = new ScriptedProviderServer(Reply.ok(SUCCESS))) {
			LlmResponse response = client(server, ServiceTier.FLEX, ReasoningEffort.LOW).complete(REQUEST);

			assertThat(response.rawText()).isEqualTo(MAPPER.readTree(SUCCESS).at("/choices/0/message/content").asString());
			assertThat(response.provider()).isEqualTo(Provider.OPENROUTER);
			assertThat(response.model()).isEqualTo("openai/gpt-6-luna");
			assertThat(response.channel()).isEqualTo(Channel.OPENAI_FLEX);
			assertThat(response.serviceTier()).isEqualTo(ServiceTier.FLEX);
			assertThat(response.flexFallback()).isFalse();
			assertThat(response.reasoningEffort()).isEqualTo(ReasoningEffort.LOW);
			assertThat(response.outputMode()).isEqualTo(OutputMode.JSON_SCHEMA);
			assertThat(response.inputTokens()).isEqualTo(516);
			assertThat(response.outputTokens()).isEqualTo(560);
			assertThat(response.cachedTokens()).isZero();
			assertThat(response.attempts()).isEqualTo(1);

			assertThat(server.requests()).singleElement().satisfies(request -> {
				assertThat(request.path()).isEqualTo("/chat/completions");
				JsonNode body = MAPPER.readTree(request.body());
				assertThat(body.at("/model").asString()).isEqualTo("openai/gpt-6-luna");
				assertThat(body.at("/temperature").asDouble()).isZero();
				assertThat(body.at("/max_tokens").asInt()).isEqualTo(LlmClient.MAX_OUTPUT_TOKENS);
				assertThat(body.at("/reasoning_effort").asString()).isEqualTo("low");
				assertThat(body.at("/provider/only").toString()).isEqualTo("[\"openai/flex\"]");
				assertThat(body.at("/provider/allow_fallbacks").asBoolean(true)).isFalse();
				assertThat(body.at("/response_format/type").asString()).isEqualTo("json_schema");
				assertThat(body.at("/response_format/json_schema/strict").asBoolean()).isTrue();
				String schema = body.at("/response_format/json_schema/schema").toString();
				assertThat(schema).doesNotContain("minItems", "maxItems", "minLength", "maxLength", "uniqueItems", "$ref")
					.contains("\"additionalProperties\":false");
				assertThat(new ArrayList<>(body.at("/response_format/json_schema/schema/properties/claims/items/properties")
					.propertyNames())).containsExactly("id", "kind", "attribution", "target_ids", "evidence", "statement");
			});
		}
	}

	@Test
	void defaultTierSendsNoRoutingAndNoReasoningEffort() {
		try (ScriptedProviderServer server = new ScriptedProviderServer(Reply.ok(SUCCESS))) {
			LlmResponse response = client(server, ServiceTier.DEFAULT, null).complete(REQUEST);

			assertThat(response.channel()).isEqualTo(Channel.OPENROUTER);
			JsonNode body = MAPPER.readTree(server.requests().getFirst().body());
			assertThat(body.has("provider")).isFalse();
			assertThat(body.has("reasoning_effort")).isFalse();
		}
	}

	@Test
	void threeFlexRefusalsFallBackToTheDefaultTier() {
		try (ScriptedProviderServer server = new ScriptedProviderServer(RATE_LIMITED, RATE_LIMITED, RATE_LIMITED,
				Reply.ok(SUCCESS))) {
			LlmResponse response = client(server, ServiceTier.FLEX, null).complete(REQUEST);

			assertThat(response.serviceTier()).isEqualTo(ServiceTier.DEFAULT);
			assertThat(response.flexFallback()).isTrue();
			assertThat(response.channel()).isEqualTo(Channel.OPENAI);
			assertThat(response.attempts()).isEqualTo(4);
			assertThat(server.requests()).extracting(request -> MAPPER.readTree(request.body()).at("/provider/only").toString())
				.containsExactly("[\"openai/flex\"]", "[\"openai/flex\"]", "[\"openai/flex\"]", "[\"openai\"]");
		}
	}

	@Test
	void retryAfterIsHonoured() {
		try (ScriptedProviderServer server = new ScriptedProviderServer(RATE_LIMITED.withHeader("Retry-After", "4"),
				Reply.ok(SUCCESS))) {
			LlmResponse response = client(server, ServiceTier.DEFAULT, null).complete(REQUEST);

			assertThat(response.attempts()).isEqualTo(2);
			assertThat(sleeps).containsExactly(Duration.ofSeconds(4));
		}
	}

	@Test
	void refusalIsAProviderErrorWithoutRetry() {
		// The real payload as a refusal: the answer replaced by the model's refusal text.
		ObjectNode refused = (ObjectNode) MAPPER.readTree(SUCCESS);
		((ObjectNode) refused.at("/choices/0/message")).put("refusal", "I can't help with that.").putNull("content");
		try (ScriptedProviderServer server = new ScriptedProviderServer(Reply.ok(refused.toString()), Reply.ok(SUCCESS))) {
			assertThatExceptionOfType(LlmCallException.class)
				.isThrownBy(() -> client(server, ServiceTier.DEFAULT, null).complete(REQUEST))
				.satisfies(ex -> {
					assertThat(ex.failure()).isEqualTo(CallFailure.PROVIDER_ERROR);
					assertThat(ex.attempts()).isEqualTo(1);
					assertThat(ex.getMessage()).contains("refusal");
				});
			assertThat(server.requests()).hasSize(1);
		}
	}

	@Test
	void serverErrorsStopAfterThreeRequests() {
		Reply error = Reply.status(502, "{\"error\":{\"message\":\"bad gateway\",\"code\":502}}");
		try (ScriptedProviderServer server = new ScriptedProviderServer(error, error, error, error)) {
			assertThatExceptionOfType(LlmCallException.class)
				.isThrownBy(() -> client(server, ServiceTier.DEFAULT, null).complete(REQUEST))
				.satisfies(ex -> assertThat(ex.attempts()).isEqualTo(3));
			assertThat(server.requests()).hasSize(3);
		}
	}

	@Test
	void badRequestFailsAfterOneRequest() {
		try (ScriptedProviderServer server = new ScriptedProviderServer(
				Reply.status(400, "{\"error\":{\"message\":\"Provider returned error\",\"code\":400}}"), Reply.ok(SUCCESS))) {
			assertThatExceptionOfType(LlmCallException.class)
				.isThrownBy(() -> client(server, ServiceTier.DEFAULT, null).complete(REQUEST))
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
				.isThrownBy(() -> LlmClientFactory.create(new LlmProviderProperties(Provider.OPENROUTER,
						"openai/gpt-6-luna", "synthetic-key", ServiceTier.DEFAULT, null, Duration.ofMillis(300)),
						server.baseUrl(), sleeps::add).complete(REQUEST))
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
				"{\"error\":{\"message\":\"Incorrect API key provided: synthetic-key\",\"code\":401}}"))) {
			assertThatExceptionOfType(LlmCallException.class)
				.isThrownBy(() -> client(server, ServiceTier.DEFAULT, null).complete(REQUEST))
				.satisfies(ex -> {
					assertThat(ex.attempts()).isEqualTo(1);
					assertThat(ex.getMessage()).contains("HTTP 401").doesNotContain("synthetic-key");
				});
		}
	}

	@Test
	void answerWithoutChoicesIsAProviderErrorWithoutRetry() {
		// The real payload with its choices removed.
		ObjectNode empty = (ObjectNode) MAPPER.readTree(SUCCESS);
		empty.putArray("choices");
		try (ScriptedProviderServer server = new ScriptedProviderServer(Reply.ok(empty.toString()), Reply.ok(SUCCESS))) {
			assertThatExceptionOfType(LlmCallException.class)
				.isThrownBy(() -> client(server, ServiceTier.DEFAULT, null).complete(REQUEST))
				.satisfies(ex -> {
					assertThat(ex.failure()).isEqualTo(CallFailure.PROVIDER_ERROR);
					assertThat(ex.attempts()).isEqualTo(1);
				});
			assertThat(server.requests()).hasSize(1);
		}
	}

	@Test
	void refusedConnectionIsRetriedThreeTimes() {
		assertThatExceptionOfType(LlmCallException.class)
			.isThrownBy(() -> LlmClientFactory.create(new LlmProviderProperties(Provider.OPENROUTER, "openai/gpt-6-luna",
					"synthetic-key", ServiceTier.DEFAULT, null, Duration.ofSeconds(5)),
					ScriptedProviderServer.refusingBaseUrl(), sleeps::add).complete(REQUEST))
			.satisfies(ex -> {
				assertThat(ex.failure()).isEqualTo(CallFailure.PROVIDER_ERROR);
				assertThat(ex.attempts()).isEqualTo(3);
			});
		assertThat(sleeps).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2));
	}

	private LlmClient client(ScriptedProviderServer server, ServiceTier tier, ReasoningEffort effort) {
		return LlmClientFactory.create(new LlmProviderProperties(Provider.OPENROUTER, "openai/gpt-6-luna",
				"synthetic-key", tier, effort, Duration.ofSeconds(5)), server.baseUrl(), sleeps::add);
	}

}
