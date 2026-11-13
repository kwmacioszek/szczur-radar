package pl.szczurradar.api.sources.wykop;

import java.time.Duration;
import java.time.InstantSource;
import java.util.List;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import pl.szczurradar.api.sources.LocalApiServer.Reply;
import pl.szczurradar.api.sources.LocalApiServer.Request;

/**
 * Wykop for the scenarios ({@code scenario/PrEnvScenariosIT}), imported by those that use it: the production
 * {@link WykopApiClient}, {@link WykopRepository}, fetcher and mapper over a {@link LocalWykopServer} in place
 * of {@code https://wykop.pl/api/v3/}. Its {@link WykopRepository} bean stands in for the one
 * {@link WykopConfiguration} makes from {@code WYKOP_KEY} / {@code WYKOP_SECRET}, which no test sets.
 *
 * <p>Every search answers the real, anonymized #gielda day of 2026-10-01 unless a scenario switches it:
 * {@code /search/entries} the last entries page served as the whole day ({@value #ENTRIES} entries, its
 * {@code pagination.total} set to that), {@code /search/links} the links page ({@value #LINKS} links);
 * {@link #down()} (HTTP 503 with {@link #DOWN_BODY}, every attempt) or {@link #empty()} (no element in the
 * range). {@link #reset()} brings the day back. The older ITs keep their own nested copies (PipelineRunIT,
 * LlmOutageIT and others); moving them here is a change of its own.
 *
 * <p>The server and the switches are static, as they outlive each Spring context of the tests, and every
 * switch is {@code volatile}: the server's threads and the run's queue thread read what the test thread set.
 * The client waits for nothing between its attempts and the repository between its pages, so a Wykop that
 * stays down fails the fetch at once (after the client's attempts).
 */
@TestConfiguration(proxyBeanMethods = false)
public class LocalWykop {

	/** The entries of the day: the real last entries page. */
	public static final int ENTRIES = 12;

	/** The links of the day: the real links page. */
	public static final int LINKS = 4;

	/** The short synthetic body of {@link #down()}, in Wykop's error shape. */
	public static final String DOWN_BODY = "{\"error\":{\"code\":503,\"message\":\"Service unavailable\"}}";

	private static final String EMPTY_PAGE = "{\"data\":[],\"pagination\":{\"per_page\":50,\"total\":0}}";

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private static final String ENTRIES_PAGE = withTotal(LocalWykopServer.fixture("search-entries-page.json"), ENTRIES);

	private static final String LINKS_PAGE = LocalWykopServer.fixture("search-links-page.json");

	private static volatile boolean down;

	private static volatile boolean empty;

	private static final LocalWykopServer SERVER = new LocalWykopServer(LocalWykop::answer);

	@Bean
	WykopRepository wykopRepository() {
		// No real waits between the client's attempts: a Wykop that stays down fails the fetch at once.
		WykopApiClient client = new WykopApiClient(SERVER.baseUrl(), "k", "s", Duration.ofSeconds(5), wait -> {
		}, InstantSource.system());
		return new WykopRepository(client, pause -> {
		}, Duration.ZERO);
	}

	/** Every search answers HTTP 503 with {@link #DOWN_BODY}. */
	public static void down() {
		down = true;
	}

	/** Every search finds no element in the range (an empty page, {@code total} 0). */
	public static void empty() {
		empty = true;
	}

	/** Back to the day: no outage, no empty range, no requests remembered. */
	public static void reset() {
		down = false;
		empty = false;
		SERVER.clear();
	}

	/** Every request the server got since the last {@link #reset()} (the token's included), in order. */
	public static List<Request> requests() {
		return SERVER.requests();
	}

	/** The answer to a request, on the server's thread. */
	private static Reply answer(Request request) {
		if (request.path().endsWith("/auth")) {
			return Reply.ok("{\"data\":{\"token\":\"t\"}}");
		}
		if (down) {
			return new Reply(503, DOWN_BODY);
		}
		if (empty) {
			return Reply.ok(EMPTY_PAGE);
		}
		return Reply.ok(request.path().endsWith("/search/entries") ? ENTRIES_PAGE : LINKS_PAGE);
	}

	private static String withTotal(String page, int total) {
		ObjectNode answer = (ObjectNode) MAPPER.readTree(page);
		((ObjectNode) answer.get("pagination")).put("total", total);
		return answer.toString();
	}

}
