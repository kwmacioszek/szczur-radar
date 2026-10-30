package pl.szczurradar.api.sources.reddit;

import java.util.function.Function;

import pl.szczurradar.api.sources.LocalApiServer;

/**
 * {@link LocalApiServer} in place of Arctic Shift ({@code https://arctic-shift.photon-reddit.com/}), with
 * Reddit's fixtures.
 */
public final class LocalRedditServer extends LocalApiServer {

	public LocalRedditServer(Function<Request, Reply> answers) {
		super("/", answers);
	}

	/** A real, anonymized Arctic Shift answer from {@code fixtures/reddit/}. */
	public static String fixture(String name) {
		return fixture("reddit", name);
	}

}
