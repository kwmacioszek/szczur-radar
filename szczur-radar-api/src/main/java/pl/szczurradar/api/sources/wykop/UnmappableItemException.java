package pl.szczurradar.api.sources.wykop;

/**
 * A Wykop element that cannot become an assessment input: an unsupported resource type or no text.
 * It is never mapped silently (contract-v2.md §2).
 */
public class UnmappableItemException extends RuntimeException {

	public UnmappableItemException(String message) {
		super(message);
	}

}
