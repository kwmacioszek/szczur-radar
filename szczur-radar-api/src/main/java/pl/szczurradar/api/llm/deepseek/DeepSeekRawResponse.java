package pl.szczurradar.api.llm.deepseek;

import java.io.IOException;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads what Spring AI's {@code DeepSeekApi} loses from DeepSeek's raw 2xx answer:
 * {@code usage.prompt_cache_hit_tokens} (dropped by its {@code Usage}) and the finish reasons
 * {@code insufficient_system_resource} / {@code aborted} (missing from its finish-reason enum).
 * Needs a buffering request factory (the body is read twice). Values are kept per thread — a call
 * and its attempts run on the caller's thread.
 */
final class DeepSeekRawResponse implements ClientHttpRequestInterceptor {

	/**
	 * Finish reasons meaning the server cut the answer off (api-docs.deepseek.com, Chat Completions):
	 * a transient server-side failure, retried like a 5xx.
	 */
	static final Set<String> SERVER_SIDE_FINISH_REASONS = Set.of("insufficient_system_resource", "aborted");

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	/** What one answer reported. */
	record Observed(@Nullable Integer cacheHitTokens, @Nullable String finishReason) {

		boolean cutOffByServer() {
			return finishReason != null && SERVER_SIDE_FINISH_REASONS.contains(finishReason);
		}

	}

	private final ThreadLocal<Observed> last = new ThreadLocal<>();

	@Override
	public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
			throws IOException {
		ClientHttpResponse response = execution.execute(request, body);
		if (response.getStatusCode().is2xxSuccessful()) {
			last.set(observe(response.getBody().readAllBytes()));
		}
		return response;
	}

	void clear() {
		last.remove();
	}

	/** What the last successful answer on this thread reported (empty if none), then forgets it. */
	Observed take() {
		Observed value = last.get();
		last.remove();
		return value != null ? value : new Observed(null, null);
	}

	static Observed observe(byte[] body) {
		try {
			JsonNode root = MAPPER.readTree(body);
			JsonNode usage = root.path("usage");
			JsonNode hits = usage.path("prompt_cache_hit_tokens");
			if (!hits.isIntegralNumber()) {
				hits = usage.path("prompt_tokens_details").path("cached_tokens");
			}
			JsonNode finishReason = root.path("choices").path(0).path("finish_reason");
			return new Observed(hits.isIntegralNumber() ? hits.asInt() : null,
					finishReason.isString() ? finishReason.asString() : null);
		}
		catch (JacksonException ex) {
			return new Observed(null, null);
		}
	}

}
