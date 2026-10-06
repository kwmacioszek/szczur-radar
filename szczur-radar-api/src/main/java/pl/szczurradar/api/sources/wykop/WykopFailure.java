package pl.szczurradar.api.sources.wykop;

/** Why a Wykop API call failed ({@link WykopApiException#failure()}). */
public enum WykopFailure {

	/** No answer: connection error or time-out. */
	TRANSPORT,

	/** The API answered with a non-2xx status ({@link WykopApiException#status()}). */
	HTTP_STATUS,

	/** A 2xx answer without the expected shape: invalid JSON, no token, no data list, a page short of the total. */
	MALFORMED_ANSWER,

	/** The search did not end within the page limit. */
	PAGE_LIMIT,

	/** The thread was interrupted during a call or between pages. */
	INTERRUPTED

}
