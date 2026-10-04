package pl.szczurradar.api.sources.wykop;

/**
 * A failed Wykop API call: transport error, non-2xx status or an answer without the expected shape.
 * The message never contains the app key, secret or token.
 */
public class WykopApiException extends RuntimeException {

	private final int status;

	public WykopApiException(int status, String message) {
		super(message);
		this.status = status;
	}

	public WykopApiException(String message, Throwable cause) {
		super(message, cause);
		this.status = 0;
	}

	/** The HTTP status, or 0 when there was no answer. */
	public int status() {
		return status;
	}

}
