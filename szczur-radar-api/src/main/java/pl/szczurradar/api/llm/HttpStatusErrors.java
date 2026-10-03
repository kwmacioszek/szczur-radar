package pl.szczurradar.api.llm;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.ResponseErrorHandler;

/**
 * Turns an HTTP error status of a {@code RestClient}-based provider API into a classified
 * {@link TransportException} with {@code Retry-After} — Spring AI's default handler keeps neither.
 */
final class HttpStatusErrors implements ResponseErrorHandler {

	private final ProviderMessages messages;

	HttpStatusErrors(ProviderMessages messages) {
		this.messages = messages;
	}

	@Override
	public boolean hasError(ClientHttpResponse response) throws IOException {
		return response.getStatusCode().isError();
	}

	@Override
	public void handleError(URI url, HttpMethod method, ClientHttpResponse response) throws IOException {
		String body = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
		throw TransportException.ofStatus(response.getStatusCode().value(), messages.clean(body),
				TransportException.parseRetryAfter(response.getHeaders().getFirst("Retry-After")), null);
	}

}
