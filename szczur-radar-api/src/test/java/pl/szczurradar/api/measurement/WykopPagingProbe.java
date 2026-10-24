package pl.szczurradar.api.measurement;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import pl.szczurradar.api.sources.wykop.WykopApiClient;
import pl.szczurradar.api.sources.wykop.WykopApiException;
import pl.szczurradar.api.sources.wykop.WykopMapper;
import pl.szczurradar.api.sources.wykop.WykopPosts;
import pl.szczurradar.api.text.Whitespace;

/**
 * Probes the paging of the Wykop search API on live answers (S-16, plan phase 1): for each of the
 * last {@code probe.days} closed days (Warsaw time) it reads the #gielda {@code search/entries} and
 * {@code search/links} page by page straight through {@link WykopApiClient} — raw pages, not
 * {@code WykopRepository}, whose paging is what is measured — with the query the repository sends
 * ({@code tags[]}, the whole day as {@code date_from}/{@code date_to}, {@code sort=newest},
 * {@value #PAGE_SIZE} per page).
 *
 * <pre>
 * WYKOP_KEY=… WYKOP_SECRET=… ./mvnw -Pmeasurement test -Dtest=WykopPagingProbe [-Dprobe.days=30]
 * </pre>
 *
 * It prints only counts: per day and resource the pages, each page's {@code pagination.total} (and
 * whether it changed), elements received / unique / duplicated, an empty or short page before the
 * total, and whether the pre-S-16 stop rule (elements received, duplicates included, reach the total) would
 * have stopped short of the unique total; for the whole run the distribution of the elements'
 * {@code status} (absent and {@code null} apart) and how many elements have no text. Nothing is
 * written to disk; no content, nicks, ids or URLs are printed. Fails at the end when a request
 * failed (after printing everything).
 */
@Tag("measurement")
class WykopPagingProbe {

	/** As {@code WykopRepository.PAGE_SIZE} (package-private there): Wykop's maximum {@code limit}. */
	private static final int PAGE_SIZE = 50;

	/** As {@code WykopRepository.MAX_PAGES}. */
	private static final int MAX_PAGES = 200;

	/** As {@code WykopRepository.DEFAULT_PAUSE}, before every request but the first. */
	private static final long PAUSE_MILLIS = 300;

	private static final DateTimeFormatter SEARCH_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private enum Resource {

		ENTRIES("search/entries", "entries"), LINKS("search/links", "links");

		private final String path;

		private final String label;

		Resource(String path, String label) {
			this.path = path;
			this.label = label;
		}

	}

	/** How the reading of one resource on one day ended. */
	private enum End {

		TOTAL_REACHED("unique elements reached the first page's total"),

		EMPTY_PAGE("empty page"),

		SHORT_PAGE("short page"),

		PAGE_LIMIT("page limit " + MAX_PAGES),

		FAILED("request failed");

		private final String text;

		End(String text) {
			this.text = text;
		}

	}

	/** Per {@code "<resource> status=<value>"}: {elements, of them without text}; unique elements only. */
	private final Map<String, int[]> statuses = new TreeMap<>();

	private boolean firstRequest = true;

	@Test
	void probesThePaging() throws InterruptedException {
		int days = Integer.getInteger("probe.days", 30);
		assertThat(days).as("probe.days").isPositive();
		LocalDate today = LocalDate.now(WykopMapper.WYKOP_ZONE);
		LocalDate first = today.minusDays(days);
		WykopApiClient client = new WykopApiClient(env("WYKOP_KEY"), env("WYKOP_SECRET"));

		System.out.printf("Wykop paging probe: #%s, %s and %s, closed days %s…%s (Warsaw), %d per page, sort newest,"
				+ " %d ms between requests%n", WykopPosts.TAG, Resource.ENTRIES.path, Resource.LINKS.path, first,
				today.minusDays(1), PAGE_SIZE, PAUSE_MILLIS);
		System.out.println("A resource stops at an empty page, a short page or when its unique elements (by resource"
				+ " and id) reach the first page's total; page limit " + MAX_PAGES + ".");
		System.out.println("Without text: an entry with blank content, a link with blank title and description"
				+ " (white space of contract-v2.md §10). Only counts are printed: no content, nicks, ids or URLs.");

		Summary summary = new Summary();
		for (LocalDate day = first; day.isBefore(today); day = day.plusDays(1)) {
			for (Resource resource : Resource.values()) {
				Read read = read(client, resource, day);
				System.out.println(day + " " + resource.label + ": " + read.describe());
				summary.add(read);
			}
		}
		summary.print(statuses);
		assertThat(summary.failed).as("reads with a failed request (see above)").isZero();
	}

	private Read read(WykopApiClient client, Resource resource, LocalDate day) throws InterruptedException {
		Read read = new Read();
		for (int page = 1; page <= MAX_PAGES; page++) {
			if (!firstRequest) {
				Thread.sleep(PAUSE_MILLIS);
			}
			firstRequest = false;
			JsonNode answer;
			try {
				answer = client.get(resource.path, query(day, page));
			}
			catch (WykopApiException ex) {
				// Only the kind and status: an error body could quote content.
				read.fail(ex.failure() + (ex.status() == 0 ? "" : " " + ex.status()));
				return read;
			}
			JsonNode data = answer.path("data");
			if (!data.isArray()) {
				read.fail("answer without a data list");
				return read;
			}
			read.pages++;
			int total = answer.path("pagination").path("total").asInt(-1);
			read.totals.add(total);
			for (JsonNode element : data) {
				read.received++;
				JsonNode id = element.path("id");
				if (id.isMissingNode() || id.isNull()) {
					read.withoutId++;
				}
				else if (!read.keys.add(element.path("resource").asString("?") + ":" + id)) {
					read.duplicates++;
					continue;
				}
				count(resource, element);
			}
			if (!read.todayStopped && total >= 0 && read.received >= total) {
				// WykopRepository stopped here before S-16: it counted received elements, duplicates included.
				read.todayStopped = true;
				read.todayStopsShort = read.unique() < total;
			}
			int expected = read.firstTotal();
			if (expected >= 0 && read.unique() >= expected) {
				read.end = End.TOTAL_REACHED;
				return read;
			}
			if (data.isEmpty()) {
				read.end = End.EMPTY_PAGE;
				read.emptyBeforeTotal = expected >= 0;
				return read;
			}
			if (data.size() < PAGE_SIZE) {
				read.end = End.SHORT_PAGE;
				read.shortBeforeTotal = expected >= 0;
				return read;
			}
		}
		read.end = End.PAGE_LIMIT;
		return read;
	}

	/** The query {@code WykopRepository.itemsOfDay} sends for {@code day}. */
	private static Map<String, Object> query(LocalDate day, int page) {
		Map<String, Object> query = new LinkedHashMap<>();
		query.put("tags", List.of(WykopPosts.TAG));
		query.put("date_from", day.atStartOfDay().format(SEARCH_TIME));
		query.put("date_to", day.atTime(LocalTime.of(23, 59, 59)).format(SEARCH_TIME));
		query.put("sort", "newest");
		query.put("limit", PAGE_SIZE);
		query.put("page", page);
		return query;
	}

	private void count(Resource resource, JsonNode element) {
		JsonNode status = element.path("status");
		String value = status.isMissingNode() ? "(absent)"
				: status.isNull() ? "null" : status.isString() ? status.asString() : "<" + status.getNodeType() + ">";
		int[] counts = statuses.computeIfAbsent(resource.label + " status=" + value, key -> new int[2]);
		counts[0]++;
		if (!hasText(resource, element)) {
			counts[1]++;
		}
	}

	private static boolean hasText(Resource resource, JsonNode element) {
		return switch (resource) {
			case ENTRIES -> text(element, "content");
			case LINKS -> text(element, "title") || text(element, "description");
		};
	}

	private static boolean text(JsonNode element, String field) {
		JsonNode value = element.path(field);
		return value.isString() && !Whitespace.normalize(value.asString().replace("\u0000", "")).isEmpty();
	}

	private static String env(String name) {
		String value = System.getenv(name);
		if (value == null || value.isBlank()) {
			throw new IllegalStateException(name + " is not set (Wykop API key and secret from your shell)");
		}
		return value;
	}

	/** The pages of one resource on one day. */
	private static final class Read {

		/** {@code pagination.total} of each page; -1 when the page had none. */
		final List<Integer> totals = new ArrayList<>();

		final Set<String> keys = new HashSet<>();

		int pages;

		int received;

		int duplicates;

		int withoutId;

		End end = End.PAGE_LIMIT;

		@Nullable String failure;

		boolean emptyBeforeTotal;

		boolean shortBeforeTotal;

		boolean todayStopped;

		boolean todayStopsShort;

		/** Distinct elements: by resource and id, each element without an id counted once. */
		int unique() {
			return keys.size() + withoutId;
		}

		int firstTotal() {
			return totals.isEmpty() ? -1 : totals.getFirst();
		}

		boolean totalChanged() {
			return totals.stream().distinct().count() > 1;
		}

		boolean belowTotal() {
			return firstTotal() >= 0 && unique() < firstTotal();
		}

		void fail(String why) {
			end = End.FAILED;
			failure = why;
		}

		String describe() {
			String total = totalChanged()
					? "totals " + totals.stream().map(String::valueOf).collect(Collectors.joining(",")) + " (changed)"
					: "total " + (firstTotal() < 0 ? "none" : String.valueOf(firstTotal()));
			StringBuilder line = new StringBuilder("pages %d, %s, received %d, unique %d, duplicates %d"
				.formatted(pages, total, received, unique(), duplicates));
			if (withoutId > 0) {
				line.append(", without id ").append(withoutId);
			}
			line.append(", end: ").append(end.text);
			if (failure != null) {
				line.append(" (").append(failure).append(')');
			}
			List<String> flags = new ArrayList<>();
			if (totalChanged()) {
				flags.add("total changed");
			}
			if (emptyBeforeTotal) {
				flags.add("empty page before total");
			}
			if (shortBeforeTotal) {
				flags.add("short page before total");
			}
			if (belowTotal()) {
				flags.add("unique below total");
			}
			if (todayStopsShort) {
				flags.add("pre-S-16 stop short of the unique total");
			}
			if (!flags.isEmpty()) {
				line.append(" !! ").append(String.join(", ", flags));
			}
			return line.toString();
		}

	}

	/** Counts over every resource and day. */
	private static final class Summary {

		int reads;

		int failed;

		int pageLimit;

		int requests;

		int totalChanged;

		int emptyBeforeTotal;

		int shortBeforeTotal;

		int belowTotal;

		int todayStopsShort;

		int received;

		int unique;

		int duplicates;

		int withoutId;

		void add(Read read) {
			reads++;
			failed += read.end == End.FAILED ? 1 : 0;
			pageLimit += read.end == End.PAGE_LIMIT ? 1 : 0;
			requests += read.pages;
			totalChanged += read.totalChanged() ? 1 : 0;
			emptyBeforeTotal += read.emptyBeforeTotal ? 1 : 0;
			shortBeforeTotal += read.shortBeforeTotal ? 1 : 0;
			belowTotal += read.belowTotal() ? 1 : 0;
			todayStopsShort += read.todayStopsShort ? 1 : 0;
			received += read.received;
			unique += read.unique();
			duplicates += read.duplicates;
			withoutId += read.withoutId;
		}

		void print(Map<String, int[]> statuses) {
			System.out.println();
			System.out.printf("reads (resource × day): %d, failed %d, page limit %d; pages read: %d%n", reads, failed,
					pageLimit, requests);
			System.out.printf("total changed between pages: %d; empty page before total: %d; short page before total: %d;"
					+ " unique below the first total: %d; pre-S-16 stop short of the unique total: %d%n", totalChanged,
					emptyBeforeTotal, shortBeforeTotal, belowTotal, todayStopsShort);
			System.out.printf("elements received: %d, unique: %d, duplicates: %d, without id: %d%n", received, unique,
					duplicates, withoutId);
			System.out.println("status of unique elements (elements / of them without text):");
			int withoutText = 0;
			for (Map.Entry<String, int[]> status : statuses.entrySet()) {
				System.out.printf("  %s: %d / %d%n", status.getKey(), status.getValue()[0], status.getValue()[1]);
				withoutText += status.getValue()[1];
			}
			System.out.printf("elements without text: %d%n", withoutText);
		}

	}

}
