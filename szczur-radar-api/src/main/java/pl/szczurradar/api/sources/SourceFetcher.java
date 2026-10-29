package pl.szczurradar.api.sources;

import java.time.Instant;
import java.util.List;

/**
 * Fetches a source's posts added in a time range, as posts of the shared model — one Spring bean per
 * source (with {@link RawInputMapper}). The pipeline's fetch step talks only to this interface, so a
 * new source adds a bean here and changes nothing in {@code pipeline} (see {@code sources/CLAUDE.md}).
 */
public interface SourceFetcher {

	/** The source this fetches. */
	Source source();

	/** Whether it can fetch here (e.g. its keys are set); an unconfigured source is never fetched. */
	boolean configured();

	/**
	 * The posts added between {@code from} and {@code to} (inclusive), each once.
	 *
	 * @throws IllegalStateException when not {@link #configured()}
	 * @throws RuntimeException when the source fails: the source's result is {@code failed}, nothing of it
	 *     is stored, and the run goes on with the other sources
	 */
	Fetched fetch(Instant from, Instant to);

	/**
	 * What one fetch returned.
	 *
	 * @param posts the elements that became posts
	 * @param fetched every element the source returned
	 * @param skipped elements that could not become a post (no id, unsupported type, no text, no time,
	 *     unexpected shape); their raw payload is not stored
	 */
	record Fetched(List<SourcePost> posts, int fetched, int skipped) {

		public Fetched {
			posts = List.copyOf(posts);
		}

	}

}
