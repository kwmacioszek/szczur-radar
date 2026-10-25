package pl.szczurradar.api.llm.openrouter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.openai.client.okhttp.OkHttpClient;
import com.openai.core.RequestOptions;
import com.openai.core.http.Headers;
import com.openai.core.http.HttpClient;
import com.openai.core.http.HttpMethod;
import com.openai.core.http.HttpRequest;
import com.openai.core.http.HttpResponse;
import com.openai.errors.OpenAIInvalidDataException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
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
import pl.szczurradar.api.llm.ReasoningEffort;
import pl.szczurradar.api.llm.ScriptedProviderServer;
import pl.szczurradar.api.llm.ScriptedProviderServer.Reply;
import pl.szczurradar.api.llm.ServiceTier;
import pl.szczurradar.api.llm.transport.CleanedProviderException;
import pl.szczurradar.api.llm.transport.ProviderMessages;
import pl.szczurradar.api.llm.transport.TransportRetry;

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

	private static final String KEY_LIMIT_MESSAGE = "Key limit exceeded (monthly limit). $10.02 of $10.00 used; "
			+ "resets 2026-11-01T00:00:00.000Z.";

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

	/**
	 * The SDK appends {@code chat/completions} to the base URL, so the real base must end with a single
	 * {@code /v1}: on a local server with {@link OpenRouterClient#BASE_URL}'s path the request lands on
	 * {@code /api/v1/chat/completions}, i.e. https://openrouter.ai/api/v1/chat/completions.
	 */
	@Test
	void completionsGoToApiV1ChatCompletionsWithoutASecondV1() {
		URI real = URI.create(OpenRouterClient.BASE_URL);
		try (ScriptedProviderServer server = new ScriptedProviderServer(Reply.ok(SUCCESS))) {
			LlmClientFactory.create(new LlmProviderProperties(Provider.OPENROUTER, "openai/gpt-6-luna", "synthetic-key",
					ServiceTier.DEFAULT, null, Duration.ofSeconds(5)), server.baseUrl() + real.getPath(), sleeps::add)
				.complete(REQUEST);

			assertThat(real.getHost()).isEqualTo("openrouter.ai");
			assertThat(server.requests()).extracting(ScriptedProviderServer.Request::path)
				.containsExactly("/api/v1/chat/completions");
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
				.isThrownBy(() -> client(server, ServiceTier.DEFAULT, null).complete(REQUEST))
				.satisfies(ex -> {
					assertThat(ex.failure()).isEqualTo(CallFailure.PROVIDER_ERROR);
					assertThat(ex.attempts()).isEqualTo(1);
					assertThat(ex.getMessage()).contains("no text", "stop");
				});
			assertThat(server.requests()).hasSize(1);
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
			assertThatExceptionOfType(LlmConfigurationException.class)
				.isThrownBy(() -> client(server, ServiceTier.DEFAULT, null).complete(REQUEST))
				.satisfies(ex -> {
					assertThat(ex.attempts()).isEqualTo(1);
					assertThat(ex.getMessage()).contains("HTTP 401").doesNotContain("synthetic-key");
				});
		}
	}

	@Test
	void aSpentKeyLimitIsTheConfigurationAfterOneRequest() {
		// OpenRouter refuses a key whose credit limit is spent with 403 "Key limit exceeded"
		// (docs: capping-shared-agent-keys); synthetic, in that shape.
		try (ScriptedProviderServer server = new ScriptedProviderServer(
				Reply.status(403, forbidden(KEY_LIMIT_MESSAGE, null).toString()), Reply.ok(SUCCESS))) {
			assertThatExceptionOfType(LlmConfigurationException.class)
				.isThrownBy(() -> client(server, ServiceTier.DEFAULT, null).complete(REQUEST))
				.satisfies(ex -> {
					assertThat(ex.attempts()).isEqualTo(1);
					assertThat(ex.getMessage()).contains("HTTP 403", "Key limit exceeded");
				});
			assertThat(server.requests()).hasSize(1);
			assertThat(sleeps).isEmpty();
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "openrouter_key_limit", "openrouter_workspace_budget", "openrouter_guardrail_budget" })
	void aSpentBudgetNamedByItsLimitSourceIsTheConfigurationAfterOneRequest(String limitSource) {
		// A 403 whose metadata names a spent budget (docs: limits), whatever its message says.
		try (ScriptedProviderServer server = new ScriptedProviderServer(
				Reply.status(403, forbidden("Budget limit exceeded (monthly limit).", limitSource).toString()),
				Reply.ok(SUCCESS))) {
			assertThatExceptionOfType(LlmConfigurationException.class)
				.isThrownBy(() -> client(server, ServiceTier.DEFAULT, null).complete(REQUEST))
				.satisfies(ex -> {
					assertThat(ex.attempts()).isEqualTo(1);
					assertThat(ex.getMessage()).contains("HTTP 403");
				});
			assertThat(server.requests()).hasSize(1);
		}
	}

	@Test
	void aRateLimitNamingTheKeyLimitIsStillRetried() {
		// Only a 403 for a spent budget is the configuration: a 429 stays a rate limit whatever its
		// metadata says.
		try (ScriptedProviderServer server = new ScriptedProviderServer(
				Reply.status(429, errorBody(429, "Rate limit exceeded", Map.of("limit_source", "openrouter_key_limit"))
					.toString()),
				Reply.ok(SUCCESS))) {
			LlmResponse response = client(server, ServiceTier.DEFAULT, null).complete(REQUEST);

			assertThat(response.attempts()).isEqualTo(2);
			assertThat(server.requests()).hasSize(2);
		}
	}

	@Test
	void aModerationRefusalIsAProviderErrorAfterOneRequest() {
		// OpenRouter refuses flagged input with 403 and the moderation reasons (docs: errors-and-debugging).
		try (ScriptedProviderServer server = new ScriptedProviderServer(Reply.status(403,
				"{\"error\":{\"code\":403,\"message\":\"Your input was flagged by moderation\","
						+ "\"metadata\":{\"reasons\":[\"harassment\"],\"flagged_input\":\"...\"}}}"),
				Reply.ok(SUCCESS))) {
			assertThatExceptionOfType(LlmCallException.class)
				.isThrownBy(() -> client(server, ServiceTier.DEFAULT, null).complete(REQUEST))
				.satisfies(ex -> {
					assertThat(ex).isNotInstanceOf(LlmConfigurationException.class);
					assertThat(ex.failure()).isEqualTo(CallFailure.PROVIDER_ERROR);
					assertThat(ex.attempts()).isEqualTo(1);
					assertThat(ex.getMessage()).contains("HTTP 403", "moderation");
				});
			assertThat(server.requests()).hasSize(1);
		}
	}

	@Test
	void anEmptyChoicesListIsRetriedLikeAServerError() {
		// The real payload with an empty choices list.
		ObjectNode empty = (ObjectNode) MAPPER.readTree(SUCCESS);
		empty.putArray("choices");
		try (ScriptedProviderServer server = new ScriptedProviderServer(Reply.ok(empty.toString()), Reply.ok(SUCCESS))) {
			LlmResponse response = client(server, ServiceTier.DEFAULT, null).complete(REQUEST);

			assertThat(response.attempts()).isEqualTo(2);
			assertThat(server.requests()).hasSize(2);
			assertThat(sleeps).containsExactly(Duration.ofSeconds(1));
		}
	}

	@Test
	void anAnswerWithoutChoicesAndWithoutAnErrorIsRetriedLikeAServerError() {
		// The real payload with its choices field removed: openai-java fails with "`choices` is not set".
		ObjectNode missing = (ObjectNode) MAPPER.readTree(SUCCESS);
		missing.remove("choices");
		try (ScriptedProviderServer server = new ScriptedProviderServer(Reply.ok(missing.toString()), Reply.ok(SUCCESS))) {
			LlmResponse response = client(server, ServiceTier.DEFAULT, null).complete(REQUEST);

			assertThat(response.attempts()).isEqualTo(2);
			assertThat(server.requests()).hasSize(2);
		}
	}

	@Test
	void threeFlexRefusalsReportedInA200AnswerFallBackToTheDefaultTier() {
		// OpenRouter reports an error that occurs once the model has started in the body of an HTTP 200
		// (docs: errors-and-debugging); synthetic, in that shape.
		Reply refused = inBodyError(429, "Rate limit exceeded", "rate_limit_exceeded");
		try (ScriptedProviderServer server = new ScriptedProviderServer(refused, refused, refused, Reply.ok(SUCCESS))) {
			LlmResponse response = client(server, ServiceTier.FLEX, null).complete(REQUEST);

			assertThat(response.serviceTier()).isEqualTo(ServiceTier.DEFAULT);
			assertThat(response.flexFallback()).isTrue();
			assertThat(response.attempts()).isEqualTo(4);
			assertThat(server.requests()).extracting(request -> MAPPER.readTree(request.body()).at("/provider/only").toString())
				.containsExactly("[\"openai/flex\"]", "[\"openai/flex\"]", "[\"openai/flex\"]", "[\"openai\"]");
		}
	}

	@Test
	void aServerErrorReportedInA200AnswerStopsAfterThreeRequests() {
		Reply error = inBodyError(502, "Upstream error", "provider_error");
		try (ScriptedProviderServer server = new ScriptedProviderServer(error, error, error, error)) {
			assertThatExceptionOfType(LlmCallException.class)
				.isThrownBy(() -> client(server, ServiceTier.DEFAULT, null).complete(REQUEST))
				.satisfies(ex -> {
					assertThat(ex).isNotInstanceOf(LlmConfigurationException.class);
					assertThat(ex.failure()).isEqualTo(CallFailure.PROVIDER_ERROR);
					assertThat(ex.attempts()).isEqualTo(3);
					assertThat(ex.getMessage()).contains("HTTP 502", "Upstream error", "provider_error");
				});
			assertThat(server.requests()).hasSize(3);
		}
	}

	@Test
	void anAuthenticationErrorReportedInA200AnswerIsTheConfigurationWithoutTheKey() {
		try (ScriptedProviderServer server = new ScriptedProviderServer(
				inBodyError(401, "Invalid key synthetic-key", "authentication_error"), Reply.ok(SUCCESS))) {
			assertThatExceptionOfType(LlmConfigurationException.class)
				.isThrownBy(() -> client(server, ServiceTier.DEFAULT, null).complete(REQUEST))
				.satisfies(ex -> {
					assertThat(ex.attempts()).isEqualTo(1);
					assertThat(ex.getMessage()).contains("HTTP 401", "<hidden>").doesNotContain("synthetic-key");
				});
			assertThat(server.requests()).hasSize(1);
		}
	}

	@Test
	void aBadRequestReportedInA200AnswerFailsAfterOneRequest() {
		try (ScriptedProviderServer server = new ScriptedProviderServer(
				inBodyError(400, "Provider returned error", "invalid_request"), Reply.ok(SUCCESS))) {
			assertThatExceptionOfType(LlmCallException.class)
				.isThrownBy(() -> client(server, ServiceTier.DEFAULT, null).complete(REQUEST))
				.satisfies(ex -> {
					assertThat(ex).isNotInstanceOf(LlmConfigurationException.class);
					assertThat(ex.attempts()).isEqualTo(1);
					assertThat(ex.getMessage()).contains("HTTP 400");
				});
			assertThat(server.requests()).hasSize(1);
		}
	}

	@Test
	void aSpentKeyLimitReportedInA200AnswerIsTheConfiguration() {
		try (ScriptedProviderServer server = new ScriptedProviderServer(
				Reply.ok(inBody(forbidden(KEY_LIMIT_MESSAGE, null))), Reply.ok(SUCCESS))) {
			assertThatExceptionOfType(LlmConfigurationException.class)
				.isThrownBy(() -> client(server, ServiceTier.DEFAULT, null).complete(REQUEST))
				.satisfies(ex -> {
					assertThat(ex.attempts()).isEqualTo(1);
					assertThat(ex.getMessage()).contains("HTTP 403", "Key limit exceeded");
				});
			assertThat(server.requests()).hasSize(1);
		}
	}

	@Test
	void aSpentBudgetNamedByItsLimitSourceInA200AnswerIsTheConfiguration() {
		try (ScriptedProviderServer server = new ScriptedProviderServer(
				Reply.ok(inBody(forbidden("Budget limit exceeded (monthly limit).", "openrouter_workspace_budget"))),
				Reply.ok(SUCCESS))) {
			assertThatExceptionOfType(LlmConfigurationException.class)
				.isThrownBy(() -> client(server, ServiceTier.DEFAULT, null).complete(REQUEST))
				.satisfies(ex -> assertThat(ex.attempts()).isEqualTo(1));
			assertThat(server.requests()).hasSize(1);
		}
	}

	@Test
	void aServerErrorAfterPartialOutputIsRetriedWithoutAcceptingThatOutput() {
		try (ScriptedProviderServer server = new ScriptedProviderServer(
				partialAnswerError(502, "Provider disconnected mid-stream", "provider_unavailable"),
				Reply.ok(SUCCESS))) {
			LlmResponse response = client(server, ServiceTier.DEFAULT, null).complete(REQUEST);

			assertThat(response.rawText()).isEqualTo(MAPPER.readTree(SUCCESS).at("/choices/0/message/content").asString());
			assertThat(response.attempts()).isEqualTo(2);
			assertThat(server.requests()).hasSize(2);
			assertThat(sleeps).containsExactly(Duration.ofSeconds(1));
		}
	}

	@Test
	void serverErrorsAfterPartialOutputStopAfterThreeRequests() {
		Reply error = partialAnswerError(502, "Provider disconnected mid-stream", "provider_unavailable");
		try (ScriptedProviderServer server = new ScriptedProviderServer(error, error, error, Reply.ok(SUCCESS))) {
			assertThatExceptionOfType(LlmCallException.class)
				.isThrownBy(() -> client(server, ServiceTier.DEFAULT, null).complete(REQUEST))
				.satisfies(ex -> {
					assertThat(ex.failure()).isEqualTo(CallFailure.PROVIDER_ERROR);
					assertThat(ex.attempts()).isEqualTo(3);
					assertThat(ex.getMessage()).contains("HTTP 502", "Provider disconnected mid-stream", "provider_unavailable");
				});
			assertThat(server.requests()).hasSize(3);
			assertThat(sleeps).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2));
		}
	}

	@Test
	void anAuthenticationErrorAfterPartialOutputStopsWithoutTheKey() {
		try (ScriptedProviderServer server = new ScriptedProviderServer(
				partialAnswerError(401, "Invalid key synthetic-key", "authentication_error"), Reply.ok(SUCCESS))) {
			assertThatExceptionOfType(LlmConfigurationException.class)
				.isThrownBy(() -> client(server, ServiceTier.DEFAULT, null).complete(REQUEST))
				.satisfies(ex -> {
					assertThat(ex.attempts()).isEqualTo(1);
					assertThat(ex.getMessage()).contains("HTTP 401", "<hidden>").doesNotContain("synthetic-key");
				});
			assertThat(server.requests()).hasSize(1);
			assertThat(sleeps).isEmpty();
		}
	}

	@Test
	void aSpentKeyLimitAfterPartialOutputIsTheConfiguration() {
		try (ScriptedProviderServer server = new ScriptedProviderServer(
				partialAnswerError(403, KEY_LIMIT_MESSAGE, "provider_error"), Reply.ok(SUCCESS))) {
			assertThatExceptionOfType(LlmConfigurationException.class)
				.isThrownBy(() -> client(server, ServiceTier.DEFAULT, null).complete(REQUEST))
				.satisfies(ex -> {
					assertThat(ex.attempts()).isEqualTo(1);
					assertThat(ex.getMessage()).contains("HTTP 403", "Key limit exceeded");
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
				assertThat(ex.getCause()).isInstanceOf(CleanedProviderException.class)
					.hasMessageStartingWith(RuntimeException.class.getName() + ": unexpected state");
				assertThat(ex.getCause().getStackTrace()).isEqualTo(sdkError.getStackTrace());
				assertThat(ex.getMessage()).contains("unexpected state", "<hidden>").doesNotContain("synthetic-key");
			});
		assertThat(calls).hasSize(1);
		assertThat(sleeps).isEmpty();
	}

	@Test
	void noFailureLeavingTheClientCarriesTheKeyInItsCauses() {
		// OpenRouter quotes the key in the message of an error answer (the 401 above), which openai-java
		// puts into its exception's message; here a 401 and a 502 in that shape, then an error without a
		// status and a time-out from a stand-in for the SDK.
		String quoting = "{\"error\":{\"message\":\"Incorrect API key provided: synthetic-key\",\"code\":%d}}";
		try (ScriptedProviderServer server = new ScriptedProviderServer(Reply.status(401, quoting.formatted(401)))) {
			assertPrintedWithoutTheKey(catchThrowable(() -> client(server, ServiceTier.DEFAULT, null).complete(REQUEST)),
					CleanedProviderException.class.getName(), "com.openai.errors.UnauthorizedException");
		}
		Reply badGateway = Reply.status(502, quoting.formatted(502));
		try (ScriptedProviderServer server = new ScriptedProviderServer(badGateway, badGateway, badGateway)) {
			assertPrintedWithoutTheKey(catchThrowable(() -> client(server, ServiceTier.DEFAULT, null).complete(REQUEST)),
					CleanedProviderException.class.getName(), "com.openai.errors.InternalServerException");
		}
		assertPrintedWithoutTheKey(catchThrowable(() -> stubbed(_ -> {
			throw new RuntimeException("unexpected state for the key synthetic-key");
		}).complete(REQUEST)), CleanedProviderException.class.getName(), RuntimeException.class.getName());
		assertPrintedWithoutTheKey(catchThrowable(() -> stubbed(_ -> {
			throw new RuntimeException("call failed", new SocketTimeoutException("read timed out for synthetic-key"));
		}).complete(REQUEST)), CleanedProviderException.class.getName(), SocketTimeoutException.class.getName());
	}

	@Test
	void anErrorInA200AnswerTheSdkFailsOnCarriesNoKeyInItsCauses() {
		// A 502 reported in a 200 answer (above) reaches the raw response; then the stand-in for the SDK
		// fails on it as openai-java does, with a message quoting the key (synthetic).
		OpenRouterRawResponse rawResponse = new OpenRouterRawResponse(
				answering(inBody(errorBody(502, "Upstream error", Map.of("error_type", "provider_error")))));
		LlmClient client = stubbed(rawResponse, _ -> {
			rawResponse.execute(HttpRequest.builder().method(HttpMethod.POST).baseUrl("http://openrouter.invalid").build(),
					RequestOptions.none());
			throw new OpenAIInvalidDataException("`choices` is not set in the answer for synthetic-key");
		});

		Throwable failure = catchThrowable(() -> client.complete(REQUEST));

		assertThat(failure).hasMessageContaining("HTTP 502");
		assertPrintedWithoutTheKey(failure, CleanedProviderException.class.getName(),
				OpenAIInvalidDataException.class.getName());
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

	/**
	 * {@code failure} printed in full, as a log prints it: the key appears in none of its causes, the
	 * original exceptions' classes do.
	 */
	private static void assertPrintedWithoutTheKey(Throwable failure, String... originalClasses) {
		StringWriter printed = new StringWriter();
		failure.printStackTrace(new PrintWriter(printed));
		assertThat(printed.toString()).doesNotContain("synthetic-key").contains(originalClasses);
	}

	/** The client on a stand-in for the SDK's chat model, with the test's key and recorded waits. */
	private LlmClient stubbed(ChatModel chatModel) {
		return stubbed(new OpenRouterRawResponse(OkHttpClient.builder().build()), chatModel);
	}

	private LlmClient stubbed(OpenRouterRawResponse rawResponse, ChatModel chatModel) {
		return new OpenRouterClient(chatModel, "openai/gpt-6-luna", ServiceTier.DEFAULT, null, Duration.ofSeconds(5),
				new TransportRetry(sleeps::add), rawResponse, new ProviderMessages("synthetic-key"));
	}

	/** A stand-in for openai-java's HTTP client answering every request with {@code body} as an HTTP 200. */
	private static HttpClient answering(String body) {
		return new HttpClient() {

			@Override
			public HttpResponse execute(HttpRequest request, RequestOptions requestOptions) {
				return new HttpResponse() {

					@Override
					public int statusCode() {
						return 200;
					}

					@Override
					public Headers headers() {
						return Headers.builder().build();
					}

					@Override
					public InputStream body() {
						return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
					}

					@Override
					public void close() {
					}

				};
			}

			@Override
			public CompletableFuture<HttpResponse> executeAsync(HttpRequest request, RequestOptions requestOptions) {
				throw new UnsupportedOperationException();
			}

			@Override
			public void close() {
			}

		};
	}

	/** The real payload changed to OpenRouter's documented error after partial output. */
	private static Reply partialAnswerError(int code, String message, String errorType) {
		ObjectNode partial = (ObjectNode) MAPPER.readTree(SUCCESS);
		ObjectNode choice = (ObjectNode) partial.at("/choices/0");
		choice.put("finish_reason", "error");
		((ObjectNode) choice.path("message")).put("content", "partial output...");
		ObjectNode error = choice.putObject("error");
		error.put("code", code).put("message", message);
		error.putObject("metadata").put("error_type", errorType);
		return Reply.ok(partial.toString());
	}

	/** An error reported in the body of an HTTP 200, in OpenRouter's documented shape. */
	private static Reply inBodyError(int code, String message, String errorType) {
		return Reply.ok(inBody(errorBody(code, message, Map.of("error_type", errorType))));
	}

	/**
	 * OpenRouter's 403 for a spent budget (docs: limits), synthetic; {@code limitSource} {@code null} =
	 * without metadata.
	 */
	private static ObjectNode forbidden(String message, String limitSource) {
		return errorBody(403, message, limitSource == null ? Map.of() : Map.of("limit_source", limitSource));
	}

	/** {@code {"error":{"code","message","metadata"}}}, OpenRouter's error shape; no metadata when empty. */
	private static ObjectNode errorBody(int code, String message, Map<String, String> metadata) {
		ObjectNode body = MAPPER.createObjectNode();
		ObjectNode error = body.putObject("error").put("code", code).put("message", message);
		if (!metadata.isEmpty()) {
			ObjectNode meta = error.putObject("metadata");
			metadata.forEach(meta::put);
		}
		return body;
	}

	/** An error body as reported in an HTTP 200, with the completion's id and object. */
	private static String inBody(ObjectNode errorBody) {
		return errorBody.put("id", "gen-synthetic").put("object", "chat.completion").toString();
	}

	private LlmClient client(ScriptedProviderServer server, ServiceTier tier, ReasoningEffort effort) {
		return LlmClientFactory.create(new LlmProviderProperties(Provider.OPENROUTER, "openai/gpt-6-luna",
				"synthetic-key", tier, effort, Duration.ofSeconds(5)), server.baseUrl(), sleeps::add);
	}

}
