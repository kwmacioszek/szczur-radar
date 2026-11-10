package pl.szczurradar.api.posts;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

import pl.szczurradar.api.sources.Source;

/**
 * How many of something one source has, a row of an aggregate per source ({@link PostRepository},
 * {@link AssessmentRepository}); the night's summary reads them ({@code pipeline/NightSummary}).
 *
 * @param source the source
 * @param count the rows counted for it
 */
public record SourceCount(Source source, Long count) {

	/** {@code counts} as a map in {@link Source} order; a source without a row is missing. */
	public static Map<Source, Long> bySource(Collection<SourceCount> counts) {
		Map<Source, Long> map = new EnumMap<>(Source.class);
		counts.forEach(count -> map.put(count.source(), count.count()));
		return map;
	}

}
