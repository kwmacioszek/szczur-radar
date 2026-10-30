package pl.szczurradar.api.sources.wykop;

import java.util.function.Function;

import pl.szczurradar.api.sources.LocalApiServer;

/** {@link LocalApiServer} in place of {@code https://wykop.pl/api/v3/}, with Wykop's fixtures. */
public final class LocalWykopServer extends LocalApiServer {

	public LocalWykopServer(Function<Request, Reply> answers) {
		super("/api/v3/", answers);
	}

	/** A real, anonymized Wykop answer from {@code fixtures/wykop/}. */
	public static String fixture(String name) {
		return fixture("wykop", name);
	}

}
