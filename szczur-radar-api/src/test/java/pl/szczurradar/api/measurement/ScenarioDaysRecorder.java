package pl.szczurradar.api.measurement;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;
import tools.jackson.databind.json.JsonMapper;

import pl.szczurradar.api.agents.assessor.AssessmentResult;
import pl.szczurradar.api.agents.assessor.Assessor;
import pl.szczurradar.api.agents.assessor.Preparation;
import pl.szczurradar.api.llm.LlmCallException;
import pl.szczurradar.api.llm.LlmClient;
import pl.szczurradar.api.llm.LlmClientException;
import pl.szczurradar.api.llm.LlmClientFactory;
import pl.szczurradar.api.llm.LlmConfigurationException;
import pl.szczurradar.api.llm.LlmProviderProperties;
import pl.szczurradar.api.llm.Provider;
import pl.szczurradar.api.llm.ReasoningEffort;
import pl.szczurradar.api.llm.ServiceTier;
import pl.szczurradar.api.llm.transport.TransportRetry;
import pl.szczurradar.api.measurement.ModelsConfig.Model;
import pl.szczurradar.api.scenario.RecordedDays;
import pl.szczurradar.api.scenario.RecordedDays.Post;
import pl.szczurradar.api.scenario.RecordedProvider;
import pl.szczurradar.api.sources.LocalApiServer;
import pl.szczurradar.api.sources.LocalApiServer.Reply;
import pl.szczurradar.api.sources.LocalApiServer.Request;

/**
 * Records the provider's answers for the posts of the sources' fixture days ({@link RecordedDays}), which the
 * scenarios replay ({@link RecordedProvider}): production's model in the configuration {@value #CONFIG} of
 * {@code models.json}, only the key (and {@code LLM_TIMEOUT}) taken from {@code LLM_*}.
 *
 * <p>The production OpenRouter client sends each post's request to a local server that passes it on to
 * OpenRouter and keeps the answer's raw body. The body is written unchanged to {@code <post>.json} in
 * {@link RecordedDays#DIR} only when the client returned the model's text from it: an answer the validator
 * rejects is a good recording, but a call the client fails (a refusal, the content filter, no text, no
 * {@code choices}, an {@code error} in a 200, any other status) is tried again, up to {@value #TRIES} times.
 * Such a recording would be a failed call in every scenario, counted by the refusals' breaker in the order the
 * parallel calls end. Posts still without a recording are listed at the end and fail the run: the owner
 * decides. A post with a recording is skipped, so a run resumes. Last, every recording is replayed through the
 * client and the validator and printed with its verdict ({@code ok} or the failure class).
 *
 * <pre>
 * ( source ~/.config/szczur-radar/openrouter.env &amp;&amp; ./mvnw -Pmeasurement test -Dtest=ScenarioDaysRecorder )
 * </pre>
 */
@Tag("measurement")
class ScenarioDaysRecorder {

	static final String CONFIG = "luna";

	static final int TRIES = 3;

	private static final URI OPENROUTER = URI.create("https://openrouter.ai");

	private static final JsonMapper JSON = JsonMapper.builder().build();

	/** One answer of OpenRouter as received. */
	private record Answer(int status, String body) {
	}

	@Test
	void recordsTheFixtureDays() throws IOException {
		LlmProviderProperties properties = properties();
		List<Post> posts = RecordedDays.posts();
		Files.createDirectories(RecordedDays.DIR);
		System.out.println("Recording " + posts.size() + " posts of the fixture days with " + properties);

		AtomicReference<@Nullable Answer> last = new AtomicReference<>();
		HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
		List<String> refused = new ArrayList<>();
		try (LocalApiServer server = new LocalApiServer(RecordedProvider.BASE_PATH,
				request -> relay(http, request, properties, last))) {
			LlmClient client = LlmClientFactory.create(properties, RecordedProvider.baseUrl(server),
					TransportRetry.Sleeper.REAL);
			Assessor assessor = new Assessor(client, properties.provider(), properties.model(), Clock.systemUTC());
			for (Post post : posts) {
				if (Files.exists(post.recording())) {
					continue;
				}
				if (!record(post, assessor, client, properties, last)) {
					refused.add(post.file());
				}
			}
		}
		assertThat(refused).as("posts whose every try was a failed call (the owner decides)").isEmpty();
		printVerdicts(properties);
	}

	/** Tries {@code post} up to {@link #TRIES} times; whether its recording was written. */
	private static boolean record(Post post, Assessor assessor, LlmClient client, LlmProviderProperties properties,
			AtomicReference<@Nullable Answer> last) throws IOException {
		Preparation.Prepared prepared = (Preparation.Prepared) assessor.prepare(post.input());
		for (int attempt = 1; attempt <= TRIES; attempt++) {
			last.set(null);
			try {
				client.complete(prepared.request().request());
			}
			catch (LlmConfigurationException ex) {
				throw ex;
			}
			catch (LlmCallException | LlmClientException ex) {
				System.out.printf("%s try %d: %s %s%n", post.file(), attempt, ex.getClass().getSimpleName(),
						ex.getMessage());
				continue;
			}
			Answer answer = last.get();
			if (answer == null || answer.status() != 200 || JSON.readTree(answer.body()).has("error")) {
				System.out.printf("%s try %d: unexpected answer %s%n", post.file(), attempt,
						answer == null ? "none" : answer.status());
				continue;
			}
			if (answer.body().contains(properties.apiKey()) || answer.body().contains("sk-or-")
					|| answer.body().contains("Bearer ")) {
				throw new IllegalStateException(post.file() + ": the answer quotes a key; nothing written");
			}
			Files.writeString(post.recording(), answer.body());
			System.out.printf("%s recorded (try %d)%n", post.file(), attempt);
			return true;
		}
		return false;
	}

	/** Every recording replayed through the production client and judged as the pipeline judges it. */
	private static void printVerdicts(LlmProviderProperties properties) {
		RecordedProvider provider = new RecordedProvider();
		try (LocalApiServer server = new LocalApiServer(RecordedProvider.BASE_PATH, provider)) {
			LlmProviderProperties replay = new LlmProviderProperties(properties.provider(), properties.model(),
					"synthetic-key", properties.serviceTier(), properties.reasoningEffort(), properties.timeout());
			Assessor assessor = new Assessor(LlmClientFactory.create(replay, RecordedProvider.baseUrl(server), wait -> {
			}), replay.provider(), replay.model(), Clock.systemUTC());
			System.out.println("| File | Page | Verdict |");
			System.out.println("| --- | --- | --- |");
			for (Post post : RecordedDays.posts()) {
				AssessmentResult result = assessor.assess(post.input());
				System.out.printf("| `%s` | %s | %s |%n", post.file(), post.page(),
						result.ok() ? "ok" : result.failureClass().value());
			}
		}
		assertThat(provider.unmatched()).as("requests without a recording").isEmpty();
	}

	/** The configuration {@value #CONFIG} of {@code models.json} with the key and time-out of {@code LLM_*}. */
	private static LlmProviderProperties properties() throws IOException {
		LlmProviderProperties shell = LlmProviderProperties.from(new StandardEnvironment());
		Model model = ModelsConfig.read(ModelsConfig.FILE)
			.model(CONFIG)
			.orElseThrow(() -> new IllegalStateException("no configuration " + CONFIG + " in " + ModelsConfig.FILE));
		return new LlmProviderProperties(byId(Provider.values(), Provider::id, model.provider()), model.model(),
				shell.apiKey(), byId(ServiceTier.values(), ServiceTier::id, model.serviceTier()),
				model.reasoningEffort() == null ? null
						: byId(ReasoningEffort.values(), ReasoningEffort::id, model.reasoningEffort()),
				shell.timeout());
	}

	private static <E> E byId(E[] values, Function<E, String> id, String value) {
		return Arrays.stream(values)
			.filter(candidate -> id.apply(candidate).equals(value))
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("unknown id " + value));
	}

	/** Passes the client's request on to OpenRouter and keeps the answer's raw body. */
	private static Reply relay(HttpClient http, Request request, LlmProviderProperties properties,
			AtomicReference<@Nullable Answer> last) {
		HttpRequest.Builder forward = HttpRequest.newBuilder(OPENROUTER.resolve(request.path()))
			.timeout(properties.timeout())
			.header("Content-Type", "application/json")
			.method(request.method(), HttpRequest.BodyPublishers.ofString(request.body(), StandardCharsets.UTF_8));
		if (request.authorization() != null) {
			forward.header("Authorization", request.authorization());
		}
		HttpResponse<String> response;
		try {
			response = http.send(forward.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		}
		catch (IOException ex) {
			return new Reply(502, "{\"error\":{\"code\":502,\"message\":\"relay: " + ex.getClass().getSimpleName() + "\"}}");
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return new Reply(502, "{\"error\":{\"code\":502,\"message\":\"relay interrupted\"}}");
		}
		last.set(new Answer(response.statusCode(), response.body()));
		return new Reply(response.statusCode(), response.body(), response.headers()
			.firstValue("Retry-After")
			.map(value -> Map.of("Retry-After", value))
			.orElse(Map.of()));
	}

}
