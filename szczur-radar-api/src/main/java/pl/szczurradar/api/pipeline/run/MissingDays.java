package pl.szczurradar.api.pipeline.run;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.jspecify.annotations.Nullable;

import pl.szczurradar.api.sources.Source;

/**
 * The days without data: calendar days of the window that no covering run fetched, per configured
 * source — the admin's list of ranges to start again (S-13). Days are always Warsaw calendar days (the
 * caller passes them; never instants divided by 24 hours, a day may have 23 or 25).
 *
 * <ol>
 *   <li>A day is covered for a source when a {@link RunKind#RANGE} run, the cron's or an admin's,
 *       {@link RunStatus#SUCCEEDED succeeded} or ended {@link RunStatus#PARTIAL partial} with the source's
 *       result {@link SourceFetchStatus#SUCCEEDED succeeded} (it gave a post; a source that failed or gave
 *       none covers nothing, though the run requested it) and the day between its first and last day
 *       ({@link Coverage}).
 *   <li>The window is the {@value #WINDOW_DAYS} days up to {@value #LAST_DAY_BEFORE_TODAY} days before
 *       today ({@link #windowStart}, {@link #windowEnd}).
 *   <li>For a source it starts no earlier than the first day the source was ever covered: a source never
 *       covered has no missing day, so the time before production or before the source was added is no
 *       gap.
 *   <li>Only configured sources count: only they can be started.
 *   <li>A day is listed when at least one source misses it, with the missing sources in {@link Source}
 *       order; the days ascending.
 * </ol>
 *
 * Just after midnight one bad night already shows the day before yesterday as missing, until the
 * night's run that covers it ends (about 2–3 hours); nothing here hides it.
 *
 * @param from the window's first day
 * @param to the window's last day
 * @param days the days without data, ascending
 */
public record MissingDays(LocalDate from, LocalDate to, List<Day> days) {

	/** How many days the window has. */
	public static final int WINDOW_DAYS = 14;

	/**
	 * How many days before today the window ends. The daily run fetches the day before yesterday and
	 * yesterday, so each day is fetched on two nights (the next one and the one after); the day before
	 * yesterday has had both, yesterday still has one ahead and today both, so they are not missing yet.
	 */
	public static final int LAST_DAY_BEFORE_TODAY = 2;

	public MissingDays {
		days = List.copyOf(days);
	}

	/** The window's first day for {@code today}. */
	public static LocalDate windowStart(LocalDate today) {
		return windowEnd(today).minusDays(WINDOW_DAYS - 1);
	}

	/** The window's last day for {@code today}. */
	public static LocalDate windowEnd(LocalDate today) {
		return today.minusDays(LAST_DAY_BEFORE_TODAY);
	}

	/**
	 * The days without data in the window of {@code today}.
	 *
	 * @param today today in Warsaw
	 * @param configured the configured sources
	 * @param coverages the days the covering runs fetched; at least those reaching the window
	 * @param firstCovered each source's first covered day ever; a source without one was never covered
	 */
	public static MissingDays of(LocalDate today, Collection<Source> configured, Collection<Coverage> coverages,
			Map<Source, LocalDate> firstCovered) {
		LocalDate from = windowStart(today);
		LocalDate to = windowEnd(today);
		TreeMap<LocalDate, Set<Source>> missing = new TreeMap<>();
		for (Source source : configured) {
			@Nullable LocalDate first = firstCovered.get(source);
			if (first == null) {
				continue;
			}
			for (LocalDate day = first.isAfter(from) ? first : from; !day.isAfter(to); day = day.plusDays(1)) {
				LocalDate checked = day;
				if (coverages.stream().noneMatch(coverage -> coverage.covers(checked, source))) {
					missing.computeIfAbsent(day, key -> EnumSet.noneOf(Source.class)).add(source);
				}
			}
		}
		List<Day> days = new ArrayList<>();
		missing.forEach((day, sources) -> days.add(new Day(day, List.copyOf(sources))));
		return new MissingDays(from, to, days);
	}

	/** A day without data and the sources that miss it, in {@link Source} order. */
	public record Day(LocalDate day, List<Source> sources) {

		public Day {
			sources = List.copyOf(sources);
		}

	}

	/** The days {@code from}–{@code to} (inclusive) a covering run fetched from {@code sources}. */
	public record Coverage(LocalDate from, LocalDate to, Set<Source> sources) {

		public Coverage {
			sources = Set.copyOf(sources);
		}

		/**
		 * The days a covering run fetched from one source: the calendar days in {@code zone} of its range's
		 * ends, for that source only.
		 */
		public static Coverage of(CoveredRange range, ZoneId zone) {
			return new Coverage(LocalDate.ofInstant(range.rangeFrom(), zone),
					LocalDate.ofInstant(range.rangeTo(), zone), Set.of(range.source()));
		}

		boolean covers(LocalDate day, Source source) {
			return sources.contains(source) && !day.isBefore(from) && !day.isAfter(to);
		}

	}

}
