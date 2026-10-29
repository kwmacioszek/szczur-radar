package pl.szczurradar.api.pipeline.step;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import pl.szczurradar.api.pipeline.SourceNotConfiguredException;
import pl.szczurradar.api.pipeline.flow.RunFlow.FetchedBatch;
import pl.szczurradar.api.pipeline.flow.RunFlow.RangeTarget;
import pl.szczurradar.api.pipeline.flow.RunFlow.SourceFetch;
import pl.szczurradar.api.pipeline.run.RunStep;
import pl.szczurradar.api.pipeline.run.SourceFetchStatus;
import pl.szczurradar.api.pipeline.state.RunInterruptedException;
import pl.szczurradar.api.sources.Source;
import pl.szczurradar.api.sources.SourceFetcher;
import pl.szczurradar.api.sources.SourcePost;

/**
 * The fetch step of a range run over the sources the run names, through their {@link SourceFetcher}
 * beans: it knows no source by name, so a new source changes nothing here. A source's failure or silence
 * is its own result, never the end of the other sources' fetch ({@link #fetch}).
 */
@Service
public class SourceIngest {

	private static final Logger log = LoggerFactory.getLogger(SourceIngest.class);

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

	/** Every known source (one per {@link SourceFetcher} bean), in {@link Source} order, and whether it is configured. */
	public List<SourceState> states() {
		return fetchers.values()
			.stream()
			.sorted(Comparator.comparing(SourceFetcher::source))
			.map(fetcher -> new SourceState(fetcher.source(), fetcher.configured()))
			.toList();
	}

	/** A source and whether a range run can fetch it here. */
	public record SourceState(Source source, boolean configured) {
	}

	/**
	 * Every named source's posts in the range, one source after another, each source isolated from the
	 * others: a source that throws (also one not configured any more) is that source's {@code failed}
	 * result ({@code <SimpleName>: <message>}, a WARN line with the exception), one without a post its
	 * {@code empty} result, and the step goes on with the next source. Each result is handed to
	 * {@code recorded} as soon as its source ended, outside the source's {@code try}: the caller writes it
	 * (the run's progress), and an exception of that write ends the step. A source with skipped elements
	 * gets a WARN line with its counts (only this loop knows them per source).
	 *
	 * <p>Before each source the run's thread is checked for an interrupt: a stopped run fetches no further
	 * source. A source's exception caused by the interrupt (its client keeps the flag) is its
	 * {@code failed} result like any other; whoever stopped the run wrote its end first, so the caller's
	 * conditional write adds nothing, and the checkpoint before the next source ends the step. After the
	 * last source there is none: the step returns or throws, and the next step is refused.
	 *
	 * @throws NoPostsFetchedException after every source was fetched, when none of them gave a post to
	 *     store: the step, and so the run, fails, naming each source with its result
	 * @throws RunInterruptedException when the run's thread was interrupted before a source
	 */
	public FetchedBatch fetch(RangeTarget target, Consumer<SourceFetch> recorded) {
		List<SourcePost> posts = new ArrayList<>();
		List<SourceFetch> outcomes = new ArrayList<>();
		int fetched = 0;
		int skipped = 0;
		for (Source source : target.sources()) {
			RunInterruptedException.checkpoint(target.runId(), RunStep.FETCH);
			SourceFetch outcome;
			List<SourcePost> sourcePosts = List.of();
			try {
				if (!configured(source)) {
					throw new SourceNotConfiguredException(List.of(source));
				}
				SourceFetcher.Fetched result = fetchers.get(source).fetch(target.from(), target.to());
				if (result.skipped() > 0) {
					log.warn("Run {}: {} skipped {} of {} elements (unexpected shape or not mappable)", target.runId(),
							source.value(), result.skipped(), result.fetched());
				}
				sourcePosts = result.posts();
				SourceFetchStatus status = sourcePosts.isEmpty() ? SourceFetchStatus.EMPTY
						: SourceFetchStatus.SUCCEEDED;
				outcome = new SourceFetch(source, status, result.fetched(), result.skipped(), null);
			}
			catch (RuntimeException ex) {
				log.warn("Run {}: {} failed: {}", target.runId(), source.value(), ex.getMessage(), ex);
				outcome = new SourceFetch(source, SourceFetchStatus.FAILED, 0, 0,
						ex.getClass().getSimpleName() + ": " + ex.getMessage());
			}
			recorded.accept(outcome);
			outcomes.add(outcome);
			posts.addAll(sourcePosts);
			fetched += outcome.fetched();
			skipped += outcome.skipped();
		}
		if (outcomes.stream().noneMatch(outcome -> outcome.status() == SourceFetchStatus.SUCCEEDED)) {
			throw new NoPostsFetchedException(outcomes);
		}
		return new FetchedBatch(target.runId(), posts, fetched, skipped, outcomes);
	}

	private boolean configured(Source source) {
		SourceFetcher fetcher = fetchers.get(source);
		return fetcher != null && fetcher.configured();
	}

}
