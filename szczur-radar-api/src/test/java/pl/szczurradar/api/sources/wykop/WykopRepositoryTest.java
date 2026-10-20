package pl.szczurradar.api.sources.wykop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import pl.szczurradar.api.pipeline.PipelineStallSettings;
import pl.szczurradar.api.sources.wykop.LocalWykopServer.Reply;
import pl.szczurradar.api.sources.wykop.LocalWykopServer.Request;

/**
 * {@link WykopRepository} on real, anonymized search answers of #gielda for 2026-10-01
 * ({@code fixtures/wykop/search-entries-page.json}: the day's last entries page, 12 of 262;
 * {@code search-links-page.json}: all 4 links).
 */
class WykopRepositoryTest {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private static final String ENTRIES = LocalWykopServer.fixture("search-entries-page.json");

	/** The real last page served as the whole day: {@code pagination.total} set to its 12 elements. */
	private static final String WHOLE_DAY = withItems(ENTRIES, 12, 12);

	private static final String LINKS = LocalWykopServer.fixture("search-links-page.json");

	private final List<Duration> pauses = new ArrayList<>();

	@Test
	void readsEntriesAndLinksOfADayWithTheTagAndDateRange() {
		try (LocalWykopServer server = new LocalWykopServer(request -> answer(request, WHOLE_DAY))) {
			List<WykopItem> items = items(repository(server).itemsOfDay("gielda", LocalDate.parse("2026-10-01")));

			assertThat(items).hasSize(12 + 4);
			assertThat(items.subList(0, 12)).allSatisfy(item -> assertThat(item.resource()).isEqualTo("entry"));
			assertThat(items.subList(12, 16)).allSatisfy(item -> assertThat(item.resource()).isEqualTo("link"));
			assertThat(items).allSatisfy(item -> {
				assertThat(item.tags()).contains("gielda");
				assertThat(item.createdAt()).startsWith("2026-10-01 ");
			});
			List<Request> searches = searches(server);
			assertThat(searches).extracting(Request::path).containsExactly("/api/v3/search/entries", "/api/v3/search/links");
			assertThat(searches).allSatisfy(request -> assertThat(request.query()).isEqualTo(
					"tags[]=gielda&date_from=2026-10-01 00:00:00&date_to=2026-10-01 23:59:59&sort=newest&limit=50&page=1"));
			assertThat(pauses).isEmpty();
		}
	}

	@Test
	void eachElementKeepsItsRawFormWithEveryFieldOfTheAnswer() {
		try (LocalWykopServer server = new LocalWykopServer(request -> answer(request, WHOLE_DAY))) {
			List<WykopSearchResult.Element> elements = repository(server)
				.itemsOfDay("gielda", LocalDate.parse("2026-10-01"))
				.elements();

			JsonNode firstEntry = MAPPER.readTree(WHOLE_DAY).get("data").get(0);
			JsonNode firstLink = MAPPER.readTree(LINKS).get("data").get(0);
			assertThat(elements.getFirst().raw()).isEqualTo(firstEntry);
			assertThat(elements.get(12).raw()).isEqualTo(firstLink);
			// Fields the assessment never reads are kept in the raw element.
			assertThat(elements.getFirst().raw().has("votes")).isTrue();
			assertThat(elements.getFirst().raw().has("comments")).isTrue();
			assertThat(elements.get(12).raw().has("source")).isTrue();
			assertThat(elements).allSatisfy(element -> assertThat(element.raw().get("id").asLong())
				.isEqualTo(element.item().id()));
		}
	}

	@Test
	void pagesUntilTheTotalIsReached() {
		// The real 12-entry page served as three full pages of 50 (the page's items repeated, total
		// set to 150): the repository must ask for pages 1–3 and stop without a fourth request.
		String fullPage = withItems(ENTRIES, 50, 150);
		try (LocalWykopServer server = new LocalWykopServer(request -> answer(request, fullPage))) {
			List<WykopItem> items = items(repository(server).itemsOfDay("gielda", LocalDate.parse("2026-10-01")));

			// Repeated elements are kept once (12 distinct entries + 4 links).
			assertThat(items).hasSize(12 + 4);
			assertThat(searches(server)).extracting(request -> request.path() + " " + page(request))
				.containsExactly("/api/v3/search/entries 1", "/api/v3/search/entries 2", "/api/v3/search/entries 3",
						"/api/v3/search/links 1");
			assertThat(pauses).containsExactly(Duration.ofMillis(1), Duration.ofMillis(1));
		}
	}

	@Test
	void aShortPageEndsTheSearchEvenWithoutATotal() {
		ObjectNode noTotal = (ObjectNode) MAPPER.readTree(ENTRIES);
		noTotal.remove("pagination");
		try (LocalWykopServer server = new LocalWykopServer(request -> answer(request, noTotal.toString()))) {
			assertThat(items(repository(server).itemsOfDay("gielda", LocalDate.parse("2026-10-01")))).hasSize(16);
			assertThat(searches(server)).hasSize(2);
		}
	}

	@Test
	void anElementOfAnUnexpectedShapeIsSkippedAndCounted() {
		ObjectNode page = (ObjectNode) MAPPER.readTree(WHOLE_DAY);
		// The whole-day page with the first element's id changed to text (minimally changed recording).
		((ObjectNode) page.get("data").get(0)).put("id", "not-a-number");
		try (LocalWykopServer server = new LocalWykopServer(request -> answer(request, page.toString()))) {
			WykopSearchResult result = repository(server).itemsOfDay("gielda", LocalDate.parse("2026-10-01"));

			assertThat(result.skippedElements()).isEqualTo(1);
			assertThat(result.elements()).hasSize(11 + 4);
		}
	}

	@Test
	void aPageShortOfTheTotalIsAnErrorNotASilentlyCutDay() {
		ObjectNode page = (ObjectNode) MAPPER.readTree(ENTRIES);
		// The real 12-entry page claiming 100 entries in total (e.g. an API that capped the limit).
		((ObjectNode) page.get("pagination")).put("total", 100);
		try (LocalWykopServer server = new LocalWykopServer(request -> answer(request, page.toString()))) {
			assertThatExceptionOfType(WykopApiException.class)
				.isThrownBy(() -> repository(server).itemsOfDay("gielda", LocalDate.parse("2026-10-01")))
				.satisfies(ex -> assertThat(ex.failure()).isEqualTo(WykopFailure.MALFORMED_ANSWER))
				.withMessageContaining("12 of 100");
		}
	}

	@Test
	void anAnswerWithoutADataListIsMalformed() {
		try (LocalWykopServer server = new LocalWykopServer(request -> answer(request, "{\"data\":{}}"))) {
			assertThatExceptionOfType(WykopApiException.class)
				.isThrownBy(() -> repository(server).itemsOfDay("gielda", LocalDate.parse("2026-10-01")))
				.satisfies(ex -> assertThat(ex.failure()).isEqualTo(WykopFailure.MALFORMED_ANSWER));
		}
	}

	@Test
	void aSearchThatNeverEndsStopsAtThePageLimit() {
		// Full pages of the real elements with a total that is never reached.
		String endless = withItems(ENTRIES, 50, Integer.MAX_VALUE);
		try (LocalWykopServer server = new LocalWykopServer(request -> answer(request, endless))) {
			assertThatExceptionOfType(WykopApiException.class)
				.isThrownBy(() -> repository(server).itemsOfDay("gielda", LocalDate.parse("2026-10-01")))
				.satisfies(ex -> assertThat(ex.failure()).isEqualTo(WykopFailure.PAGE_LIMIT));
			assertThat(searches(server)).hasSize(WykopRepository.MAX_PAGES);
		}
	}

	@Test
	void aFetchEndsBeforeTheWatchdogsShortestDefault() {
		// The deadline bounds the attempts; the last one's time-out and one connection retry inside the
		// JDK come on top. A fetch that ends within this is failed by its source, not stopped as stalled.
		Duration longestFetch = WykopRepository.FETCH_DEADLINE.plus(WykopApiClient.DEFAULT_TIMEOUT.multipliedBy(2));

		assertThat(longestFetch).isLessThan(PipelineStallSettings.MIN_DEFAULT);
	}

	private static List<WykopItem> items(WykopSearchResult result) {
		return result.elements().stream().map(WykopSearchResult.Element::item).toList();
	}

	private static Reply answer(Request request, String entries) {
		if (request.path().endsWith("/auth")) {
			return Reply.ok("{\"data\":{\"token\":\"t\"}}");
		}
		return Reply.ok(request.path().endsWith("/search/entries") ? entries : LINKS);
	}

	private WykopRepository repository(LocalWykopServer server) {
		return new WykopRepository(new WykopApiClient(server.baseUrl(), "k", "s", Duration.ofSeconds(5)), pauses::add,
				Duration.ofMillis(1));
	}

	private static List<Request> searches(LocalWykopServer server) {
		return server.requests().stream().filter(request -> request.path().contains("/search/")).toList();
	}

	private static String page(Request request) {
		return request.query().replaceAll(".*page=(\\d+).*", "$1");
	}

	private static String withItems(String page, int count, int total) {
		ObjectNode answer = (ObjectNode) MAPPER.readTree(page);
		ArrayNode real = (ArrayNode) answer.get("data");
		ArrayNode items = MAPPER.createArrayNode();
		for (int i = 0; i < count; i++) {
			items.add(real.get(i % real.size()));
		}
		answer.set("data", items);
		((ObjectNode) answer.get("pagination")).put("total", total);
		return answer.toString();
	}

}
