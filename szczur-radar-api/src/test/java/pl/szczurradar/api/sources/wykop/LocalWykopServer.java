package pl.szczurradar.api.sources.wykop;

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
import java.util.function.Function;

import com.sun.net.httpserver.HttpServer;

/**
 * A local HTTP server in place of {@code https://wykop.pl/api/v3/}: every request is recorded and
 * answered by a test-supplied function.
 */
final class LocalWykopServer implements AutoCloseable {

	/** One recorded request; {@code query} is URL-decoded. */
	record Request(String method, String path, String query, String authorization, String body) {
	}

	/** The answer to a request. */
	record Reply(int status, String body) {

		static Reply ok(String body) {
			return new Reply(200, body);
		}

	}

	private final HttpServer server;

	private final List<Request> requests = Collections.synchronizedList(new ArrayList<>());

	LocalWykopServer(Function<Request, Reply> answers) {
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
					new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			requests.add(request);
			Reply reply = answers.apply(request);
			byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(reply.status(), body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		server.start();
	}

	URI baseUrl() {
		return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api/v3/");
	}

	List<Request> requests() {
		return List.copyOf(requests);
	}

	/** A real, anonymized Wykop answer from {@code fixtures/wykop/}. */
	static String fixture(String name) {
		try (InputStream in = LocalWykopServer.class.getResourceAsStream("/fixtures/wykop/" + name)) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	@Override
	public void close() {
		server.stop(0);
	}

}
