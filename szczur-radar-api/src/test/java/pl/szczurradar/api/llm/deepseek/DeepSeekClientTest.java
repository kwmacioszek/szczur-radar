package pl.szczurradar.api.llm.deepseek;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
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
import pl.szczurradar.api.llm.ScriptedProviderServer;
import pl.szczurradar.api.llm.ScriptedProviderServer.Reply;
import pl.szczurradar.api.llm.ServiceTier;
import pl.szczurradar.api.llm.transport.CleanedProviderException;
import pl.szczurradar.api.llm.transport.ProviderMessages;
import pl.szczurradar.api.llm.transport.TransportRetry;

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
			assertThatExceptionOfType(LlmConfigurationException.class)
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
		// DeepSeek quotes the key in a 401 answer (above); here a 401 and a 503 in that shape — HTTP statuses
		// leave HttpStatusErrors without a cause — then an error without a status and a time-out from a
		// stand-in for the SDK.
		String quoting = "{\"error\":{\"message\":\"Authentication Fails, Your api key: synthetic-key is invalid\"}}";
		try (ScriptedProviderServer server = new ScriptedProviderServer(Reply.status(401, quoting))) {
			assertPrintedWithoutTheKey(catchThrowable(() -> client(server, Duration.ofSeconds(5)).complete(REQUEST)),
					"HTTP 401");
		}
		Reply unavailable = Reply.status(503, quoting);
		try (ScriptedProviderServer server = new ScriptedProviderServer(unavailable, unavailable, unavailable)) {
			assertPrintedWithoutTheKey(catchThrowable(() -> client(server, Duration.ofSeconds(5)).complete(REQUEST)),
					"HTTP 503");
		}
		assertPrintedWithoutTheKey(catchThrowable(() -> stubbed(_ -> {
			throw new RuntimeException("unexpected state for the key synthetic-key");
		}).complete(REQUEST)), CleanedProviderException.class.getName(), RuntimeException.class.getName());
		assertPrintedWithoutTheKey(catchThrowable(() -> stubbed(_ -> {
			throw new RuntimeException("call failed", new SocketTimeoutException("read timed out for synthetic-key"));
		}).complete(REQUEST)), CleanedProviderException.class.getName(), SocketTimeoutException.class.getName());
	}

	@Test
	void aServerCutOffTheSdkFailsOnCarriesNoKeyInItsCauses() {
		// The real payload as DeepSeek ends an answer it could not finish reaches the raw response; then the
		// stand-in for the SDK fails on it with a message quoting the key (synthetic).
		ObjectNode cutOff = (ObjectNode) MAPPER.readTree(SUCCESS);
		((ObjectNode) cutOff.at("/choices/0")).put("finish_reason", "insufficient_system_resource");
		DeepSeekRawResponse rawResponse = new DeepSeekRawResponse();
		LlmClient client = stubbed(rawResponse, _ -> {
			answered(rawResponse, cutOff.toString());
			throw new RuntimeException("no finish reason insufficient_system_resource for the key synthetic-key");
		});

		Throwable failure = catchThrowable(() -> client.complete(REQUEST));

		assertThat(failure).hasMessageContaining("answer cut off by the server");
		assertPrintedWithoutTheKey(failure, CleanedProviderException.class.getName(), RuntimeException.class.getName());
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
		return stubbed(new DeepSeekRawResponse(), chatModel);
	}

	private LlmClient stubbed(DeepSeekRawResponse rawResponse, ChatModel chatModel) {
		return new DeepSeekClient(chatModel, "deepseek-v4-flash", new TransportRetry(sleeps::add), rawResponse,
				new ProviderMessages("synthetic-key"));
	}

	/** {@code body} passed through {@code rawResponse} as a 200 answer on this thread. */
	private static void answered(DeepSeekRawResponse rawResponse, String body) {
		try {
			rawResponse.intercept(new MockClientHttpRequest(), new byte[0],
					(_, _) -> new MockClientHttpResponse(body.getBytes(StandardCharsets.UTF_8), HttpStatus.OK));
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private LlmClient client(String baseUrl, Duration timeout) {
		return LlmClientFactory.create(new LlmProviderProperties(Provider.DEEPSEEK, "deepseek-v4-flash",
				"synthetic-key", ServiceTier.DEFAULT, null, timeout), baseUrl, sleeps::add);
	}

}
