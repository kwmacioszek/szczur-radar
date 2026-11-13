package pl.szczurradar.api.scenario;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import pl.szczurradar.api.llm.LlmClient;
import pl.szczurradar.api.sources.LocalApiServer;
import pl.szczurradar.api.sources.LocalApiServer.Reply;
import pl.szczurradar.api.sources.LocalApiServer.Request;

/**
 * OpenRouter's chat completions as recorded, for a {@link LocalApiServer} in place of the provider: each
 * request is answered by its user message, so the answers fit their posts whatever order the parallel calls
 * of a run arrive in.
 *
 * <ul>
 * <li>the check's message ({@link LlmClient#CHECK_REQUEST}) → the real {@code fixtures/openrouter/chat-completion.json}
 * (the check's answer is not parsed);</li>
 * <li>the message of a post of {@link RecordedDays} → that post's recording;</li>
 * <li>any other request → HTTP 400 in OpenRouter's error shape (a refused request, never retried), and the
 * request is kept in {@link #unmatched()}.</li>
 * </ul>
 *
 * <p>The recordings are read when it is made; a post without its recording answers as an unknown request.
 * The client's base URL is {@link #baseUrl(LocalApiServer)}.
 */
public final class RecordedProvider implements Function<Request, Reply> {

	/** The API root the server is made with ({@code new LocalApiServer(RecordedProvider.BASE_PATH, provider)}). */
	public static final String BASE_PATH = "/";

	/** OpenRouter's API root under the server's ({@code OpenRouterClient.BASE_URL}'s path). */
	private static final String API_PATH = "api/v1";

	/** The short synthetic body of an unknown request, in OpenRouter's error shape. */
	static final String UNKNOWN_BODY = "{\"error\":{\"code\":400,\"message\":\"no recording answers this request\"}}";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final Map<String, String> answers = new HashMap<>();

	private final List<Request> unmatched = Collections.synchronizedList(new ArrayList<>());

	public RecordedProvider() {
		if (!Files.isDirectory(RecordedDays.DIR)) {
			throw new IllegalStateException("No recordings in " + RecordedDays.DIR.toAbsolutePath()
					+ ": the tests read them from the API module's root (szczur-radar-api/), where Maven runs them");
		}
		answers.put(LlmClient.CHECK_REQUEST.userMessage(), LocalApiServer.fixture("openrouter", "chat-completion.json"));
		for (RecordedDays.Post post : RecordedDays.posts()) {
			if (Files.exists(post.recording())) {
				answers.put(post.userMessage(), RecordedDays.recording(post));
			}
		}
	}

	/** The base URL of the provider's client ({@code LlmClientFactory.create(properties, baseUrl, sleeper)}). */
	public static String baseUrl(LocalApiServer server) {
		return server.baseUrl() + API_PATH;
	}

	@Override
	public Reply apply(Request request) {
		String userMessage = userMessage(request.body());
		String answer = userMessage == null ? null : answers.get(userMessage);
		if (answer == null) {
			unmatched.add(request);
			return new Reply(400, UNKNOWN_BODY);
		}
		return Reply.ok(answer);
	}

	/** The requests no recording answered, in order of arrival. */
	public List<Request> unmatched() {
		return List.copyOf(unmatched);
	}

	public void clearUnmatched() {
		unmatched.clear();
	}

	/**
	 * The content of the request's only user message (a string, or its text parts joined), or {@code null} for a
	 * body that is not a chat completion with one.
	 */
	static @Nullable String userMessage(String body) {
		JsonNode request;
		try {
			request = JSON.readTree(body);
		}
		catch (JacksonException ex) {
			return null;
		}
		List<String> messages = new ArrayList<>();
		for (JsonNode message : request.path("messages")) {
			if (!"user".equals(message.path("role").asString())) {
				continue;
			}
			JsonNode content = message.path("content");
			if (content.isString()) {
				messages.add(content.stringValue());
			}
			else if (content.isArray()) {
				StringBuilder text = new StringBuilder();
				content.forEach(part -> text.append(part.path("text").asString()));
				messages.add(text.toString());
			}
		}
		return messages.size() == 1 ? messages.getFirst() : null;
	}

}
