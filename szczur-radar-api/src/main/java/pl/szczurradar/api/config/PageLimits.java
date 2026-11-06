package pl.szczurradar.api.config;

/**
 * The bounds of a paged list ({@code page} from 0, {@code size} from 1): the largest {@code size} and, with it,
 * the largest {@code page}. A page's offset ({@code page × size}) is an {@code int} in the database query, so a
 * page above {@link #MAX_PAGE} used to end as a 500 (audit X6); it is a 400 {@code invalid_request} instead.
 * {@code MAX_PAGE × MAX_SIZE} stays within {@link Integer#MAX_VALUE}; {@code PageLimitsTest} keeps the two
 * together.
 */
public final class PageLimits {

	/** The largest {@code size} of a page of any list. */
	public static final int MAX_SIZE = 100;

	/** The largest {@code page} of any list (constant, for {@code @Max}). */
	public static final int MAX_PAGE = Integer.MAX_VALUE / MAX_SIZE;

	private PageLimits() {
	}

}
