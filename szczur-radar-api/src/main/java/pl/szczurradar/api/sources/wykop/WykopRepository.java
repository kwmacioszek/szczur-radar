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

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * <p>Paging is checked for consistency, per resource: a read counts the elements it has seen (each
 * distinct {@code resource:id} once; elements without an id and of an unexpected shape at every
 * occurrence) and compares them with page 1's {@code pagination.total}. It is consistent when it ends
 * with at least {@code total} seen and every page had the same {@code total}. An empty or short page
 * before {@code total}, or a page whose {@code total} differs from page 1's, means the pages shifted
 * (e.g. an element was deleted while paging, so one would be lost between pages). Such a resource is
 * read once more from page 1 and that read replaces the first (an element deleted meanwhile is not
 * kept); when it is inconsistent too, the fetch fails ({@link WykopFailure#INCOMPLETE_PAGING}) rather
 * than return an incomplete day. Only the inconsistent resource is read again. An answer without
 * {@code pagination.total} ends at its first short or empty page, unchecked.
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

	private static final Logger log = LoggerFactory.getLogger(WykopRepository.class);

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
	 * @throws WykopApiException when a resource's paging stays inconsistent in two reads
	 *     ({@link WykopFailure#INCOMPLETE_PAGING}), the page limit is reached, the API fails (after the
	 *     client's retries) or the fetch, both reads included, does not fit {@link #FETCH_DEADLINE}
	 *     ({@link WykopFailure#DEADLINE_EXCEEDED})
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

	/**
	 * Adds the resource's elements to {@code elements} (keyed by resource and id), from its first read
	 * or, when that one is inconsistent, from a second read that replaces it; returns the skipped count.
	 */
	private int search(Resource resource, String tag, LocalDateTime from, LocalDateTime to, Instant deadline,
			Map<String, WykopSearchResult.Element> elements) {
		Read read = read(resource, tag, from, to, deadline, false);
		if (read.inconsistency() != null) {
			log.warn("Reading Wykop {} again: {} of {}, {}", resource.path, read.seen(), read.total(),
					read.inconsistency());
			read = read(resource, tag, from, to, deadline, true);
			if (read.inconsistency() != null) {
				throw new WykopApiException(WykopFailure.INCOMPLETE_PAGING, "Wykop " + resource.path
						+ " stayed inconsistent after 2 reads: " + read.seen() + " of " + read.total() + ", "
						+ read.inconsistency());
			}
		}
		elements.putAll(read.elements());
		return read.skipped();
	}

	/**
	 * One read of the resource from page 1, with its own elements; {@code again} pauses before page 1
	 * too (the first read just asked for pages).
	 */
	private Read read(Resource resource, String tag, LocalDateTime from, LocalDateTime to, Instant deadline,
			boolean again) {
		Map<String, WykopSearchResult.Element> elements = new LinkedHashMap<>();
		int skipped = 0;
		int total = -1;
		for (int page = 1; page <= MAX_PAGES; page++) {
			if (page > 1 || again) {
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
				elements.putIfAbsent(key(resource, item, elements.size()),
						new WykopSearchResult.Element(item, element));
			}
			int pageTotal = answer.path("pagination").path("total").asInt(-1);
			if (page == 1) {
				total = pageTotal;
			}
			// Without a total there is nothing to check: a short or empty page ends the read.
			if (total < 0) {
				if (data.size() < PAGE_SIZE) {
					return new Read(elements, skipped, total, null);
				}
				continue;
			}
			Read read = new Read(elements, skipped, total, null);
			if (pageTotal != total) {
				return read.inconsistent("total changed " + total + "→" + (pageTotal < 0 ? "none" : pageTotal)
						+ " on page " + page);
			}
			if (read.seen() >= total) {
				return read;
			}
			if (data.isEmpty()) {
				return read.inconsistent("page " + page + " is empty");
			}
			if (data.size() < PAGE_SIZE) {
				return read.inconsistent(
						"page " + page + " has only " + data.size() + " of " + PAGE_SIZE + " elements");
			}
			// A full page before the total: the next page (duplicates may hold back the count).
		}
		throw new WykopApiException(WykopFailure.PAGE_LIMIT,
				"Wykop " + resource.path + " did not end within " + MAX_PAGES + " pages");
	}

	/**
	 * One read of a resource: its elements (keyed by resource and id), the skipped count, page 1's
	 * {@code total} (-1 without one) and why the read is inconsistent, or {@code null}.
	 */
	private record Read(Map<String, WykopSearchResult.Element> elements, int skipped, int total,
			@Nullable String inconsistency) {

		/**
		 * The elements seen: each distinct one once; those without an id and those skipped cannot be told
		 * apart, so at every occurrence.
		 */
		int seen() {
			return elements.size() + skipped;
		}

		Read inconsistent(String why) {
			return new Read(elements, skipped, total, why);
		}

	}

	/**
	 * An element without an id is keyed by its position in the read (kept at every occurrence) and its
	 * resource (so the resources' reads never share a key).
	 */
	private static String key(Resource resource, WykopItem item, int position) {
		return item.id() == null ? "no-id:" + resource.path + ":" + position : item.resource() + ":" + item.id();
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
