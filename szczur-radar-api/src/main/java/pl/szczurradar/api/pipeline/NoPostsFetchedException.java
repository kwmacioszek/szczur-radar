package pl.szczurradar.api.pipeline;

import java.util.List;
import java.util.stream.Collectors;

import pl.szczurradar.api.sources.Source;

/**
 * A range run's sources gave no post to store: a source fetched no element in the range, or only
 * elements that could not become a post. The fetch step, and so the run, fails; the message names each
 * such source with its own counts.
 */
public class NoPostsFetchedException extends RuntimeException {

	private final List<Source> sources;

	private final int fetched;

	private final int skipped;

	/**
	 * @param empty each source that gave no post, with its own counts
	 * @param fetched the elements every source of the run fetched, for the run's counters
	 * @param skipped the elements of every source of the run that could not become a post
	 */
	public NoPostsFetchedException(List<EmptySource> empty, int fetched, int skipped) {
		super(empty.stream()
			.map(source -> source.source().value() + " gave no post to store (fetched " + source.fetched()
					+ ", skipped " + source.skipped() + ")")
			.collect(Collectors.joining("; ")));
		this.sources = empty.stream().map(EmptySource::source).toList();
		this.fetched = fetched;
		this.skipped = skipped;
	}

	/** The sources that gave no post. */
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

	/** A source that gave no post, with what it fetched and skipped. */
	public record EmptySource(Source source, int fetched, int skipped) {
	}

}
