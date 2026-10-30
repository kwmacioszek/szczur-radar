package pl.szczurradar.api.sources.reddit;

/** Why a Reddit (Arctic Shift) call or fetch failed ({@link RedditApiException#failure()}). */
public enum RedditFailure {

	/** No answer: connection error or time-out. */
	TRANSPORT,

	/** The API answered with a non-2xx status ({@link RedditApiException#status()}). */
	HTTP_STATUS,

	/**
	 * A 2xx answer without the expected shape: invalid JSON, an {@code error} in the envelope, no
	 * {@code data} list.
	 */
	MALFORMED_ANSWER,

	/** A subreddit's search did not end within the page limit ({@code RedditRepository.MAX_PAGES}). */
	PAGE_LIMIT,

	/**
	 * A subreddit's paging could not go on without a gap ({@code RedditRepository}): a full page with no
	 * post not seen before, or posts whose {@code created_utc} goes down. The range would be incomplete, so
	 * it is a failure rather than a result.
	 */
	INCOMPLETE_PAGING,

	/** The thread was interrupted during a call, between its attempts or between pages. */
	INTERRUPTED,

	/**
	 * The fetch did not fit its deadline ({@code RedditRepository.FETCH_DEADLINE}): no attempt or wait was
	 * started that would end after it. The deadline keeps a failing fetch shorter than the pipeline's
	 * watchdog ({@code PipelineStallSettings.MIN_DEFAULT}), so the source fails with this cause instead of
	 * the run being stopped as stalled.
	 */
	DEADLINE_EXCEEDED

}
