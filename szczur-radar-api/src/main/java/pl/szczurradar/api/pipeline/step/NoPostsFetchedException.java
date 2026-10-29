package pl.szczurradar.api.pipeline.step;

import java.util.List;

import pl.szczurradar.api.pipeline.flow.RunFlow.SourceFetch;
import pl.szczurradar.api.sources.Source;

/**
 * No source of a range run gave a post to store: each one failed, fetched no element in the range, or
 * only elements that could not become a post. The fetch step, and so the run, fails; the message names
 * each source with its result ({@link SourceFetch#summary}).
 */
public class NoPostsFetchedException extends RuntimeException {

	private final List<Source> sources;

	private final int fetched;

	private final int skipped;

	/** @param outcomes the result of every source of the run, none of them {@code succeeded} */
	public NoPostsFetchedException(List<SourceFetch> outcomes) {
		super(SourceFetch.summary(outcomes));
		this.sources = outcomes.stream().map(SourceFetch::source).toList();
		this.fetched = outcomes.stream().mapToInt(SourceFetch::fetched).sum();
		this.skipped = outcomes.stream().mapToInt(SourceFetch::skipped).sum();
	}

	/** Every source of the run, in the order they were fetched. */
	public List<Source> sources() {
		return sources;
	}

	/** The elements every source of the run fetched. */
	public int fetched() {
		return fetched;
	}

	/** The elements of every source of the run that could not become a post. */
	public int skipped() {
		return skipped;
	}

}
