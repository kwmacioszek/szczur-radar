package pl.szczurradar.api.sources.wykop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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
 * {@code search-links-page.json}: all 4 links). Days of more than one page are the real entries
 * renumbered ({@link #entriesPage}).
 */
@ExtendWith(OutputCaptureExtension.class)
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
		// Three pages of distinct entries (50 + 50 + 12, total 112): pages 1–3, no fourth request, no second read.
		try (LocalWykopServer server = new LocalWykopServer(entriesByReadAndPage((read, page) -> switch (page) {
			case 1 -> entriesPage(112, ids(1, 50));
			case 2 -> entriesPage(112, ids(51, 100));
			default -> entriesPage(112, ids(101, 112));
		}))) {
			List<WykopItem> items = items(repository(server).itemsOfDay("gielda", LocalDate.parse("2026-10-01")));

			assertThat(entryIds(items)).isEqualTo(ids(1, 112));
			assertThat(items).hasSize(112 + 4);
			assertThat(searches(server)).extracting(WykopRepositoryTest::pathAndPage)
				.containsExactly("search/entries 1", "search/entries 2", "search/entries 3", "search/links 1");
			assertThat(pauses).containsExactly(Duration.ofMillis(1), Duration.ofMillis(1));
		}
	}

	@Test
	void aQuietNightAsksForEachPageOnce() {
		// Two full pages that reach the total exactly: no third page, no second read, one auth.
		try (LocalWykopServer server = new LocalWykopServer(entriesByReadAndPage(
				(read, page) -> page == 1 ? entriesPage(100, ids(1, 50)) : entriesPage(100, ids(51, 100))))) {
			List<WykopItem> items = items(repository(server).itemsOfDay("gielda", LocalDate.parse("2026-10-01")));

			assertThat(entryIds(items)).isEqualTo(ids(1, 100));
			assertThat(server.requests()).hasSize(1 + 2 + 1);
			assertThat(searches(server)).extracting(WykopRepositoryTest::pathAndPage)
				.containsExactly("search/entries 1", "search/entries 2", "search/links 1");
		}
	}

	@Test
	void anEmptyDayIsReadOnce() {
		try (LocalWykopServer server = new LocalWykopServer(
				entriesByReadAndPage((read, page) -> entriesPage(0, List.of())))) {
			List<WykopItem> items = items(repository(server).itemsOfDay("gielda", LocalDate.parse("2026-10-01")));

			assertThat(entryIds(items)).isEmpty();
			assertThat(searches(server)).extracting(WykopRepositoryTest::pathAndPage)
				.containsExactly("search/entries 1", "search/links 1");
			assertThat(pauses).isEmpty();
		}
	}

	@Test
	void anElementDeletedWhilePagingIsCaughtByTheChangedTotalAndReadAgain(CapturedOutput output) {
		// 100 entries; entry 10 is deleted after the first read's page 1, so entry 51 moves to page 1 and
		// page 2 has 49 at total 99: 50 + 49 reach the new total, but entry 51 was never received.
		List<Integer> afterDeletion = IntStream.rangeClosed(1, 51).filter(id -> id != 10).boxed().toList();
		try (LocalWykopServer server = new LocalWykopServer(entriesByReadAndPage((read, page) -> {
			if (page == 2) {
				return entriesPage(99, ids(52, 100));
			}
			return read == 1 ? entriesPage(100, ids(1, 50)) : entriesPage(99, afterDeletion);
		}))) {
			List<WykopItem> items = items(repository(server).itemsOfDay("gielda", LocalDate.parse("2026-10-01")));

			// The second read replaces the first: entry 51 is there, the deleted entry 10 is not.
			assertThat(entryIds(items)).hasSize(99).contains(51L).doesNotContain(10L);
			assertThat(searches(server)).extracting(WykopRepositoryTest::pathAndPage)
				.containsExactly("search/entries 1", "search/entries 2", "search/entries 1", "search/entries 2",
						"search/links 1");
			// Between pages and before the second read's page 1.
			assertThat(pauses).hasSize(3);
			assertThat(output).contains("WARN",
					"Reading Wykop search/entries again: 99 of 100, total changed 100→99 on page 2");
		}
	}

	@Test
	void anEmptyPageBeforeTheTotalIsReadAgain() {
		try (LocalWykopServer server = new LocalWykopServer(entriesByReadAndPage((read, page) -> {
			if (page == 1) {
				return entriesPage(100, ids(1, 50));
			}
			return read == 1 ? entriesPage(100, List.of()) : entriesPage(100, ids(51, 100));
		}))) {
			List<WykopItem> items = items(repository(server).itemsOfDay("gielda", LocalDate.parse("2026-10-01")));

			assertThat(entryIds(items)).isEqualTo(ids(1, 100));
			assertThat(searches(server)).extracting(WykopRepositoryTest::pathAndPage)
				.containsExactly("search/entries 1", "search/entries 2", "search/entries 1", "search/entries 2",
						"search/links 1");
		}
	}

	@Test
	void duplicatesThatHideAGapAreCaughtAndReadAgain() {
		// The first read's page 2 repeats page 1 (total 100): counting every element would end at 100 with
		// only 50 distinct; counting distinct ones asks for page 3, which is empty.
		try (LocalWykopServer server = new LocalWykopServer(entriesByReadAndPage((read, page) -> switch (page) {
			case 1 -> entriesPage(100, ids(1, 50));
			case 2 -> entriesPage(100, read == 1 ? ids(1, 50) : ids(51, 100));
			default -> entriesPage(100, List.of());
		}))) {
			List<WykopItem> items = items(repository(server).itemsOfDay("gielda", LocalDate.parse("2026-10-01")));

			assertThat(entryIds(items)).isEqualTo(ids(1, 100));
			assertThat(searches(server)).extracting(WykopRepositoryTest::pathAndPage)
				.containsExactly("search/entries 1", "search/entries 2", "search/entries 3", "search/entries 1",
						"search/entries 2", "search/links 1");
		}
	}

	@Test
	void pagingInconsistentInBothReadsIsAnError() {
		// Both reads see the total change from 100 to 99 on page 2.
		try (LocalWykopServer server = new LocalWykopServer(entriesByReadAndPage(
				(read, page) -> page == 1 ? entriesPage(100, ids(1, 50)) : entriesPage(99, ids(52, 100))))) {
			assertThatExceptionOfType(WykopApiException.class)
				.isThrownBy(() -> repository(server).itemsOfDay("gielda", LocalDate.parse("2026-10-01")))
				.satisfies(ex -> assertThat(ex.failure()).isEqualTo(WykopFailure.INCOMPLETE_PAGING))
				.withMessage("Wykop search/entries stayed inconsistent after 2 reads: 99 of 100, "
						+ "total changed 100→99 on page 2");
			// Two reads of two pages, and the links are never asked for.
			assertThat(searches(server)).extracting(WykopRepositoryTest::pathAndPage)
				.containsExactly("search/entries 1", "search/entries 2", "search/entries 1", "search/entries 2");
		}
	}

	@Test
	void anInconsistentEntriesReadDoesNotReadTheLinksAgain() {
		try (LocalWykopServer server = new LocalWykopServer(entriesByReadAndPage((read, page) -> {
			if (page == 1) {
				return entriesPage(100, ids(1, 50));
			}
			return read == 1 ? entriesPage(100, List.of()) : entriesPage(100, ids(51, 100));
		}))) {
			List<WykopItem> items = items(repository(server).itemsOfDay("gielda", LocalDate.parse("2026-10-01")));

			assertThat(items.stream().filter(item -> "link".equals(item.resource()))).hasSize(4);
			assertThat(searches(server)).filteredOn(request -> request.path().endsWith("/search/links")).hasSize(1);
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
		// The real 12-entry page claiming 100 entries in total (e.g. an API that capped the limit), in both reads.
		((ObjectNode) page.get("pagination")).put("total", 100);
		try (LocalWykopServer server = new LocalWykopServer(request -> answer(request, page.toString()))) {
			assertThatExceptionOfType(WykopApiException.class)
				.isThrownBy(() -> repository(server).itemsOfDay("gielda", LocalDate.parse("2026-10-01")))
				.satisfies(ex -> assertThat(ex.failure()).isEqualTo(WykopFailure.INCOMPLETE_PAGING))
				.withMessageContaining("12 of 100");
			assertThat(searches(server)).extracting(WykopRepositoryTest::pathAndPage)
				.containsExactly("search/entries 1", "search/entries 1");
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

	private static List<Long> entryIds(List<WykopItem> items) {
		return items.stream().filter(item -> "entry".equals(item.resource())).map(WykopItem::id).toList();
	}

	private static List<Long> ids(int from, int to) {
		return IntStream.rangeClosed(from, to).mapToObj(id -> (long) id).toList();
	}

	/** The entries search's answer for one page of one read (1 = the first read, 2 = the second). */
	@FunctionalInterface
	private interface EntriesPages {

		String answer(int read, int page);

	}

	/** Entries pages by read and page number (a read starts at page 1); links: the real links page. */
	private static Function<Request, Reply> entriesByReadAndPage(EntriesPages pages) {
		AtomicInteger reads = new AtomicInteger();
		return request -> {
			if (!request.path().endsWith("/search/entries")) {
				return answer(request, ENTRIES);
			}
			int page = Integer.parseInt(page(request));
			int read = page == 1 ? reads.incrementAndGet() : reads.get();
			return Reply.ok(pages.answer(read, page));
		};
	}

	/**
	 * The real entries page changed minimally: its entries repeated in order with their ids renumbered
	 * to {@code ids}, and {@code pagination.total} set to {@code total}.
	 */
	private static String entriesPage(int total, List<? extends Number> ids) {
		ObjectNode answer = (ObjectNode) MAPPER.readTree(ENTRIES);
		ArrayNode real = (ArrayNode) answer.get("data");
		ArrayNode items = MAPPER.createArrayNode();
		for (int i = 0; i < ids.size(); i++) {
			ObjectNode entry = (ObjectNode) real.get(i % real.size()).deepCopy();
			entry.put("id", ids.get(i).longValue());
			items.add(entry);
		}
		answer.set("data", items);
		((ObjectNode) answer.get("pagination")).put("total", total);
		return answer.toString();
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

	private static String pathAndPage(Request request) {
		return request.path().replace("/api/v3/", "") + " " + page(request);
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
