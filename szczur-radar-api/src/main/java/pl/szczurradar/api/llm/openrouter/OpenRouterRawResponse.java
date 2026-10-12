package pl.szczurradar.api.llm.openrouter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.concurrent.CompletableFuture;

import com.openai.core.RequestOptions;
import com.openai.core.http.Headers;
import com.openai.core.http.HttpClient;
import com.openai.core.http.HttpRequest;
import com.openai.core.http.HttpResponse;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads what openai-java and Spring AI lose from OpenRouter's raw 2xx answer: an error OpenRouter
 * reports in the body of an HTTP 200 once the model has started on the request
 * ({@code {"error":{"code","message","metadata":{"error_type"}}}} instead of {@code choices}, or
 * {@code choices[0].error} after partial output; docs:
 * openrouter.ai/docs/api/reference/errors-and-debugging), e.g. a flex refusal. Wraps openai-java's
 * synchronous HTTP client and keeps what the last 2xx answer reported per thread — a call and its
 * attempts run on the caller's thread. The body is buffered (a chat answer is a few KB) and handed on
 * unchanged.
 */
final class OpenRouterRawResponse implements HttpClient {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	/**
	 * What one 2xx answer reported.
	 *
	 * @param answered whether a 2xx answer arrived at all (not after a time-out or I/O error)
	 * @param errorCode {@code error.code} or {@code choices[0].error.code}, the status the error would have had
	 * @param limitSource the error's {@code metadata.limit_source}: the budget that refused the request
	 * @param choices whether the answer has a non-empty {@code choices} list
	 */
	record Observed(boolean answered, @Nullable Integer errorCode, @Nullable String errorMessage,
			@Nullable String errorType, @Nullable String limitSource, boolean choices) {

		static final Observed NONE = new Observed(false, null, null, null, null, false);

	}

	private final HttpClient delegate;

	private final ThreadLocal<Observed> last = new ThreadLocal<>();

	OpenRouterRawResponse(HttpClient delegate) {
		this.delegate = delegate;
	}

	@Override
	public HttpResponse execute(HttpRequest request, RequestOptions requestOptions) {
		HttpResponse response = delegate.execute(request, requestOptions);
		int status = response.statusCode();
		if (status < 200 || status >= 300) {
			return response;
		}
		byte[] body;
		try (response) {
			body = response.body().readAllBytes();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		last.set(observe(body));
		return new Buffered(status, response.headers(), body);
	}

	@Override
	public CompletableFuture<HttpResponse> executeAsync(HttpRequest request, RequestOptions requestOptions) {
		return delegate.executeAsync(request, requestOptions);
	}

	@Override
	public void close() {
		delegate.close();
	}

	void clear() {
		last.remove();
	}

	/** What the last 2xx answer on this thread reported ({@link Observed#NONE} if none), then forgets it. */
	Observed take() {
		Observed value = last.get();
		last.remove();
		return value != null ? value : Observed.NONE;
	}

	static Observed observe(byte[] body) {
		try {
			JsonNode root = MAPPER.readTree(body);
			JsonNode error = root.path("error");
			if (!error.isObject()) {
				error = root.path("choices").path(0).path("error");
			}
			JsonNode code = error.path("code");
			JsonNode message = error.path("message");
			JsonNode type = error.path("metadata").path("error_type");
			JsonNode limitSource = error.path("metadata").path("limit_source");
			JsonNode choices = root.path("choices");
			return new Observed(true, code.isIntegralNumber() ? code.asInt() : null,
					message.isString() ? message.asString() : null, type.isString() ? type.asString() : null,
					limitSource.isString() ? limitSource.asString() : null, choices.isArray() && !choices.isEmpty());
		}
		catch (JacksonException ex) {
			return new Observed(true, null, null, null, null, false);
		}
	}

	/** A 2xx answer whose body was read here, readable once more. */
	private record Buffered(int statusCode, Headers headers, byte[] bytes) implements HttpResponse {

		@Override
		public InputStream body() {
			return new ByteArrayInputStream(bytes);
		}

		@Override
		public void close() {
		}

	}

}
