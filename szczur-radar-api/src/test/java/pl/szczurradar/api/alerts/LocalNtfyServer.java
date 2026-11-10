package pl.szczurradar.api.alerts;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import com.sun.net.httpserver.HttpServer;

/**
 * A local HTTP server in place of {@code https://ntfy.sh/}: every request is recorded with its headers
 * and answered with the status a test-supplied function returns (ntfy answers the message as JSON; the
 * sender reads only the status). The topic lives at {@link #topicUrl()}, a path with a random name like a
 * real secret one.
 */
final class LocalNtfyServer implements AutoCloseable {

	/** One recorded request; {@code headers} has each header's first value under its lower-case name. */
	record Request(String method, String path, Map<String, String> headers, String body) {

		@Nullable String header(String name) {
			return headers.get(name.toLowerCase(Locale.ROOT));
		}

	}

	final String topic = UUID.randomUUID().toString();

	private final HttpServer server;

	private final ExecutorService executor = Executors.newCachedThreadPool();

	private final List<Request> requests = Collections.synchronizedList(new ArrayList<>());

	/** @param statuses the status of each answer; it may sleep to make the client time out */
	LocalNtfyServer(Function<Request, Integer> statuses) {
		try {
			server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		server.createContext("/", exchange -> {
			Map<String, String> headers = new HashMap<>();
			exchange.getRequestHeaders()
				.forEach((name, values) -> headers.put(name.toLowerCase(Locale.ROOT), values.getFirst()));
			Request request = new Request(exchange.getRequestMethod(), exchange.getRequestURI().getPath(), headers,
					new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			requests.add(request);
			int status = statuses.apply(request);
			byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(status, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		server.setExecutor(executor);
		server.start();
	}

	URI topicUrl() {
		return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/" + topic);
	}

	List<Request> requests() {
		return List.copyOf(requests);
	}

	@Override
	public void close() {
		server.stop(0);
		executor.shutdownNow();
	}

}
