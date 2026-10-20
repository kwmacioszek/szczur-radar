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

	/** The thread was interrupted during a call, between its attempts or between pages. */
	INTERRUPTED,

	/**
	 * The fetch did not fit its deadline ({@code WykopRepository.FETCH_DEADLINE}): no attempt or wait
	 * was started that would end after it. The deadline keeps a failing fetch shorter than the
	 * pipeline's watchdog ({@code PipelineStallSettings.MIN_DEFAULT}), so the run fails with this cause
	 * instead of being stopped as stalled.
	 */
	DEADLINE_EXCEEDED

}
