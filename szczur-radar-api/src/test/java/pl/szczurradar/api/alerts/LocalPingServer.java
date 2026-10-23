package pl.szczurradar.api.alerts;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

import com.sun.net.httpserver.HttpServer;

/**
 * A local HTTP server in place of {@code https://hc-ping.com/}: every request is recorded and answered
 * with the status a test-supplied function returns (healthchecks.io answers {@code OK}; the sender reads
 * only the status). The check lives at {@link #pingUrl()}, a path with a random UUID like a real one.
 */
final class LocalPingServer implements AutoCloseable {

	/** One recorded request. */
	record Request(String method, String path, String contentType, String body) {
	}

	final String uuid = UUID.randomUUID().toString();

	private final HttpServer server;

	private final ExecutorService executor = Executors.newCachedThreadPool();

	private final List<Request> requests = Collections.synchronizedList(new ArrayList<>());

	/** @param statuses the status of each answer; it may sleep to make the client time out */
	LocalPingServer(Function<Request, Integer> statuses) {
		try {
			server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		server.createContext("/", exchange -> {
			Request request = new Request(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
					exchange.getRequestHeaders().getFirst("Content-Type"),
					new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			requests.add(request);
			int status = statuses.apply(request);
			byte[] body = "OK".getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(status, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		server.setExecutor(executor);
		server.start();
	}

	URI pingUrl() {
		return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/" + uuid);
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
