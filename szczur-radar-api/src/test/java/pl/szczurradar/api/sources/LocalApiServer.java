package pl.szczurradar.api.sources;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

import com.sun.net.httpserver.HttpServer;
import org.jspecify.annotations.Nullable;

/**
 * A local HTTP server in place of a source's API (Wykop, Arctic Shift): every request is recorded and
 * answered by a test-supplied function, each on its own thread (an answer may wait for another request).
 * Public for the pipeline's integration tests; each source has a thin subclass with its API's root and
 * fixtures ({@code LocalWykopServer}, {@code LocalRedditServer}).
 */
public class LocalApiServer implements AutoCloseable {

	/**
	 * One recorded request; {@code query} is URL-decoded, {@code headers} are every request header (names
	 * as the JDK server spells them, read with {@link #header}).
	 */
	public record Request(String method, String path, String query, String authorization, String body,
			Map<String, List<String>> headers) {

		/** The first value of the header {@code name} (in any case), or {@code null}. */
		public @Nullable String header(String name) {
			return headers.entrySet()
				.stream()
				.filter(entry -> entry.getKey().equalsIgnoreCase(name) && !entry.getValue().isEmpty())
				.map(entry -> entry.getValue().getFirst())
				.findFirst()
				.orElse(null);
		}

	}

	/** The answer to a request, with extra response headers (e.g. {@code Retry-After}). */
	public record Reply(int status, String body, Map<String, String> headers) {

		public Reply(int status, String body) {
			this(status, body, Map.of());
		}

		public static Reply ok(String body) {
			return new Reply(200, body);
		}

	}

	private final HttpServer server;

	private final String basePath;

	private final ExecutorService executor = Executors.newCachedThreadPool();

	private final List<Request> requests = Collections.synchronizedList(new ArrayList<>());

	/** @param basePath the API's root path, starting and ending with {@code /} (e.g. {@code /api/v3/}) */
	public LocalApiServer(String basePath, Function<Request, Reply> answers) {
		this.basePath = basePath;
		try {
			server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		server.createContext("/", exchange -> {
			String rawQuery = exchange.getRequestURI().getRawQuery();
			Request request = new Request(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
					rawQuery == null ? "" : URLDecoder.decode(rawQuery, StandardCharsets.UTF_8),
					exchange.getRequestHeaders().getFirst("Authorization"),
					new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8),
					Map.copyOf(exchange.getRequestHeaders()));
			requests.add(request);
			Reply reply = answers.apply(request);
			byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			reply.headers().forEach(exchange.getResponseHeaders()::add);
			exchange.sendResponseHeaders(reply.status(), body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		server.setExecutor(executor);
		server.start();
	}

	public URI baseUrl() {
		return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + basePath);
	}

	public List<Request> requests() {
		return List.copyOf(requests);
	}

	/** A real, anonymized answer of a source from {@code fixtures/<dir>/}. */
	public static String fixture(String dir, String name) {
		try (InputStream in = LocalApiServer.class.getResourceAsStream("/fixtures/" + dir + "/" + name)) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	@Override
	public void close() {
		server.stop(0);
		executor.shutdownNow();
	}

}
