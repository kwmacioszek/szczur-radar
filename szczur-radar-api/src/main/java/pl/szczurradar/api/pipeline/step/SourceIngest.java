package pl.szczurradar.api.pipeline.step;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import pl.szczurradar.api.pipeline.SourceNotConfiguredException;
import pl.szczurradar.api.pipeline.flow.RunFlow.FetchedBatch;
import pl.szczurradar.api.pipeline.flow.RunFlow.RangeTarget;
import pl.szczurradar.api.sources.Source;
import pl.szczurradar.api.sources.SourceFetcher;
import pl.szczurradar.api.sources.SourcePost;

/**
 * The fetch step of a range run over the sources the run names, through their {@link SourceFetcher}
 * beans: it knows no source by name, so a new source changes nothing here.
 */
@Service
public class SourceIngest {

	private final Map<Source, SourceFetcher> fetchers;

	SourceIngest(List<SourceFetcher> fetchers) {
		this.fetchers = fetchers.stream().collect(Collectors.toUnmodifiableMap(SourceFetcher::source, Function.identity()));
	}

	/**
	 * The sources a range run fetches: all configured ones when {@code requested} is empty, otherwise
	 * {@code requested} (distinct, in {@link Source} order).
	 *
	 * @throws SourceNotConfiguredException when a requested source is not configured, or none is
	 */
	public List<Source> resolve(List<Source> requested) {
		List<Source> sources = requested.isEmpty()
				? fetchers.values().stream().filter(SourceFetcher::configured).map(SourceFetcher::source).toList()
				: requested.stream().distinct().toList();
		if (sources.isEmpty()) {
			throw new SourceNotConfiguredException(List.of());
		}
		List<Source> missing = sources.stream().filter(source -> !configured(source)).toList();
		if (!missing.isEmpty()) {
			throw new SourceNotConfiguredException(missing);
		}
		return sources.stream().sorted(Comparator.naturalOrder()).toList();
	}

	/** Every named source's posts in the range, one source after another; a failing source fails the step. */
	public FetchedBatch fetch(RangeTarget target) {
		List<SourcePost> posts = new ArrayList<>();
		int fetched = 0;
		int skipped = 0;
		for (Source source : target.sources()) {
			if (!configured(source)) {
				throw new SourceNotConfiguredException(List.of(source));
			}
			SourceFetcher.Fetched result = fetchers.get(source).fetch(target.from(), target.to());
			posts.addAll(result.posts());
			fetched += result.fetched();
			skipped += result.skipped();
		}
		return new FetchedBatch(target.runId(), posts, fetched, skipped);
	}

	private boolean configured(Source source) {
		SourceFetcher fetcher = fetchers.get(source);
		return fetcher != null && fetcher.configured();
	}

}
