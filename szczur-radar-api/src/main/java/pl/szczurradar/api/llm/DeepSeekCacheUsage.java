package pl.szczurradar.api.llm;

import java.io.IOException;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads DeepSeek's cache hits from the raw response: Spring AI's {@code DeepSeekApi.Usage} drops
 * {@code usage.prompt_cache_hit_tokens}. Needs a buffering request factory (the body is read twice).
 * The value is kept per thread — a call and its attempts run on the caller's thread.
 */
final class DeepSeekCacheUsage implements ClientHttpRequestInterceptor {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private final ThreadLocal<Integer> cacheHitTokens = new ThreadLocal<>();

	@Override
	public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
			throws IOException {
		ClientHttpResponse response = execution.execute(request, body);
		if (response.getStatusCode().is2xxSuccessful()) {
			cacheHitTokens.set(cacheHits(response.getBody().readAllBytes()));
		}
		return response;
	}

	void clear() {
		cacheHitTokens.remove();
	}

	/** The cache hits of the last successful response on this thread, then forgets them. */
	@Nullable Integer take() {
		Integer value = cacheHitTokens.get();
		cacheHitTokens.remove();
		return value;
	}

	static @Nullable Integer cacheHits(byte[] body) {
		try {
			JsonNode usage = MAPPER.readTree(body).path("usage");
			JsonNode hits = usage.path("prompt_cache_hit_tokens");
			if (hits.isIntegralNumber()) {
				return hits.asInt();
			}
			JsonNode cached = usage.path("prompt_tokens_details").path("cached_tokens");
			return cached.isIntegralNumber() ? cached.asInt() : null;
		}
		catch (JacksonException ex) {
			return null;
		}
	}

}
