package pl.szczurradar.api.sources.wykop;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads Wykop elements through the search API (https://doc.wykop.pl/#/Wyszukiwarka): all entries
 * ({@code GET search/entries}) and links ({@code GET search/links}) with a tag, added within a time
 * range, newest first, {@value #PAGE_SIZE} per page until the answer's {@code pagination.total} is
 * reached; each element is kept once (by resource and id) and an element of an unexpected shape is
 * skipped and counted ({@link WykopSearchResult}). Elements come back in the source's shape: the
 * fields the assessment needs ({@link WykopItem}) next to the raw element; mapping is
 * {@link WykopMapper}'s job.
 *
 * <p>{@code GET search/all} is not used: it has no pages and returns only a preview (e.g. 3 of 262
 * entries of #gielda on 2026-10-01). Without {@code votes} the search is not limited to popular
 * elements (checked live on 2026-10-04: entries with 0 votes come back).
 */
public class WykopRepository {

	/** Wykop's maximum {@code limit}. */
	static final int PAGE_SIZE = 50;

	/** Guard against an answer whose {@code total} never comes. */
	static final int MAX_PAGES = 200;

	static final Duration DEFAULT_PAUSE = Duration.ofMillis(300);

	/**
	 * The deadline of one {@link #itemsAddedBetween} for the client's attempts and waits. With the last
	 * attempt's time-out (and one connection retry inside the JDK) a fetch ends within about
	 * {@code FETCH_DEADLINE + 2 × WykopApiClient.DEFAULT_TIMEOUT}, which must stay below the watchdog's
	 * shortest default ({@code PipelineStallSettings.MIN_DEFAULT}; pinned by a test).
	 */
	static final Duration FETCH_DEADLINE = Duration.ofMinutes(10);

	private static final DateTimeFormatter SEARCH_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	/** The searched resources: path and the {@code resource} value of their elements. */
	private enum Resource {

		ENTRIES("search/entries"), LINKS("search/links");

		private final String path;

		Resource(String path) {
			this.path = path;
		}

	}

	/** Waits between pages; replaced in tests. */
	@FunctionalInterface
	public interface Pause {

		void pause(Duration duration) throws InterruptedException;

		Pause REAL = Thread::sleep;

	}

	private final WykopApiClient client;

	private final Pause pause;

	private final Duration between;

	private final JsonMapper json = JsonMapper.builder().build();

	public WykopRepository(WykopApiClient client) {
		this(client, Pause.REAL, DEFAULT_PAUSE);
	}

	public WykopRepository(WykopApiClient client, Pause pause, Duration between) {
		this.client = client;
		this.pause = pause;
		this.between = between;
	}

	/** Entries and links with {@code tag} added on {@code day} (Warsaw time, the API's clock). */
	public WykopSearchResult itemsOfDay(String tag, LocalDate day) {
		return itemsAddedBetween(tag, day.atStartOfDay(), day.atTime(LocalTime.of(23, 59, 59)));
	}

	/**
	 * Entries, then links, with {@code tag} added between {@code from} and {@code to} (inclusive), each
	 * once.
	 *
	 * @throws WykopApiException when a page is short of {@code pagination.total} (a cut-off answer),
	 *     the page limit is reached, the API fails (after the client's retries) or the fetch does not fit
	 *     {@link #FETCH_DEADLINE} ({@link WykopFailure#DEADLINE_EXCEEDED})
	 */
	public WykopSearchResult itemsAddedBetween(String tag, LocalDateTime from, LocalDateTime to) {
		Instant deadline = client.deadline(FETCH_DEADLINE);
		Map<String, WykopSearchResult.Element> elements = new LinkedHashMap<>();
		int skipped = 0;
		for (Resource resource : Resource.values()) {
			skipped += search(resource, tag, from, to, deadline, elements);
		}
		return new WykopSearchResult(new ArrayList<>(elements.values()), skipped);
	}

	/** Adds the resource's elements to {@code elements} (keyed by resource and id); returns the skipped count. */
	private int search(Resource resource, String tag, LocalDateTime from, LocalDateTime to, Instant deadline,
			Map<String, WykopSearchResult.Element> elements) {
		int received = 0;
		int skipped = 0;
		for (int page = 1; page <= MAX_PAGES; page++) {
			if (page > 1) {
				pause();
			}
			Map<String, Object> query = new LinkedHashMap<>();
			query.put("tags", List.of(tag));
			query.put("date_from", from.format(SEARCH_TIME));
			query.put("date_to", to.format(SEARCH_TIME));
			query.put("sort", "newest");
			query.put("limit", PAGE_SIZE);
			query.put("page", page);
			JsonNode answer = client.get(resource.path, query, deadline);
			JsonNode data = answer.path("data");
			if (!data.isArray()) {
				throw new WykopApiException(WykopFailure.MALFORMED_ANSWER,
						"Wykop " + resource.path + " answered without a data list");
			}
			for (JsonNode element : data) {
				received++;
				WykopItem item;
				try {
					item = json.treeToValue(element, WykopItem.class);
				}
				catch (JacksonException ex) {
					// One element of an unexpected shape must not lose the rest of the day.
					skipped++;
					continue;
				}
				// Pages shift when elements are added or removed while paging: keep each element once.
				elements.putIfAbsent(key(item, elements.size()), new WykopSearchResult.Element(item, element));
			}
			int total = answer.path("pagination").path("total").asInt(-1);
			if (data.isEmpty() || (total >= 0 && received >= total)) {
				return skipped;
			}
			if (data.size() < PAGE_SIZE) {
				if (total >= 0) {
					throw new WykopApiException(WykopFailure.MALFORMED_ANSWER, "Wykop " + resource.path + " page "
							+ page + " has " + data.size() + " elements, but only " + received + " of " + total
							+ " were received");
				}
				return skipped;
			}
		}
		throw new WykopApiException(WykopFailure.PAGE_LIMIT,
				"Wykop " + resource.path + " did not end within " + MAX_PAGES + " pages");
	}

	private static String key(WykopItem item, int position) {
		return item.id() == null ? "no-id:" + position : item.resource() + ":" + item.id();
	}

	private void pause() {
		try {
			pause.pause(between);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new WykopApiException(WykopFailure.INTERRUPTED, "interrupted between Wykop pages", ex);
		}
	}

}
