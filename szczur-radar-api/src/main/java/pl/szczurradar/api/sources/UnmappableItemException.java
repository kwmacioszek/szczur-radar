package pl.szczurradar.api.sources;

/**
 * A source's element that cannot become an assessment input: an unsupported type or no text. It is
 * never mapped silently (contract-v2.md §2); the fetcher counts it as skipped.
 */
public class UnmappableItemException extends RuntimeException {

	public UnmappableItemException(String message) {
		super(message);
	}

}
