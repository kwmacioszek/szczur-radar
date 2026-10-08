package pl.szczurradar.api.llm;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpServer;

/**
 * A local HTTP server standing in for a provider API: answers each request with the next scripted
 * reply and records every request it receives — the count proves no SDK or framework retried
 * behind our back.
 */
public final class ScriptedProviderServer implements AutoCloseable {

	/** One scripted answer; {@code delay} keeps the client waiting first (time-out tests). */
	public record Reply(int status, String body, Map<String, String> headers, Duration delay) {

		public static Reply ok(String body) {
			return new Reply(200, body, Map.of(), Duration.ZERO);
		}

		public static Reply status(int status, String body) {
			return new Reply(status, body, Map.of(), Duration.ZERO);
		}

		public Reply withHeader(String name, String value) {
			return new Reply(status, body, Map.of(name, value), delay);
		}

		public Reply after(Duration wait) {
			return new Reply(status, body, headers, wait);
		}

	}

	/** A request as received. */
	public record Request(String method, String path, String query, String body) {
	}

	private final HttpServer server;

	private final ExecutorService executor = Executors.newCachedThreadPool();

	private final ConcurrentLinkedQueue<Reply> replies = new ConcurrentLinkedQueue<>();

	private final List<Request> requests = new CopyOnWriteArrayList<>();

	public ScriptedProviderServer(Reply... script) {
		replies.addAll(List.of(script));
		try {
			server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		server.setExecutor(executor);
		server.createContext("/", exchange -> {
			try (InputStream in = exchange.getRequestBody()) {
				requests.add(new Request(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
						exchange.getRequestURI().getRawQuery(), new String(in.readAllBytes(), StandardCharsets.UTF_8)));
			}
			Reply reply = replies.poll();
			if (reply == null) {
				reply = Reply.status(500, "{\"error\":\"script exhausted\"}");
			}
			if (!reply.delay().isZero()) {
				try {
					Thread.sleep(reply.delay());
				}
				catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
				}
			}
			byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			reply.headers().forEach((name, value) -> exchange.getResponseHeaders().add(name, value));
			try {
				exchange.sendResponseHeaders(reply.status(), body.length);
				exchange.getResponseBody().write(body);
			}
			catch (IOException ex) {
				// the client gave up (time-out test)
			}
			exchange.close();
		});
		server.start();
	}

	public String baseUrl() {
		return "http://127.0.0.1:" + server.getAddress().getPort();
	}

	/** A local URL nothing listens on: every connection attempt is refused. */
	public static String refusingBaseUrl() {
		try (ServerSocket socket = new ServerSocket(0, 0,
				InetAddress.getLoopbackAddress())) {
			return "http://127.0.0.1:" + socket.getLocalPort();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	public List<Request> requests() {
		return requests;
	}

	@Override
	public void close() {
		server.stop(0);
		executor.shutdownNow();
	}

	/** A real recorded payload from {@code src/test/resources/fixtures/}. */
	public static String fixture(String path) {
		try (InputStream in = ScriptedProviderServer.class.getResourceAsStream("/fixtures/" + path)) {
			if (in == null) {
				throw new IllegalStateException("missing fixture " + path);
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
