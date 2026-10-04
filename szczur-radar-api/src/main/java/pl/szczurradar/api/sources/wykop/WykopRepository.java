package pl.szczurradar.api.sources.wykop;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads Wykop elements through the search API (https://doc.wykop.pl/#/Wyszukiwarka): all entries
 * ({@code GET search/entries}) and links ({@code GET search/links}) with a tag, added within a time
 * range, newest first, {@value #PAGE_SIZE} per page until the answer's {@code pagination.total} is
 * reached. Elements come back in the source's shape ({@link WykopItem}); mapping is
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
	public List<WykopItem> itemsOfDay(String tag, LocalDate day) {
		return itemsAddedBetween(tag, day.atStartOfDay(), day.atTime(LocalTime.of(23, 59, 59)));
	}

	/** Entries, then links, with {@code tag} added between {@code from} and {@code to} (inclusive). */
	public List<WykopItem> itemsAddedBetween(String tag, LocalDateTime from, LocalDateTime to) {
		List<WykopItem> items = new ArrayList<>();
		for (Resource resource : Resource.values()) {
			items.addAll(search(resource, tag, from, to));
		}
		return items;
	}

	private List<WykopItem> search(Resource resource, String tag, LocalDateTime from, LocalDateTime to) {
		List<WykopItem> items = new ArrayList<>();
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
			JsonNode answer = client.get(resource.path, query);
			JsonNode data = answer.path("data");
			if (!data.isArray()) {
				throw new WykopApiException(200, "Wykop " + resource.path + " answered without a data list");
			}
			for (JsonNode element : data) {
				items.add(json.treeToValue(element, WykopItem.class));
			}
			int total = answer.path("pagination").path("total").asInt(-1);
			if (data.isEmpty() || data.size() < PAGE_SIZE || (total >= 0 && items.size() >= total)) {
				return items;
			}
		}
		throw new WykopApiException(200, "Wykop " + resource.path + " did not end within " + MAX_PAGES + " pages");
	}

	private void pause() {
		try {
			pause.pause(between);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new WykopApiException("interrupted between Wykop pages", ex);
		}
	}

}
