package pl.szczurradar.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PageLimitsTest {

	/** The offset of the last allowed page of the largest size is an {@code int}; the next page is not. */
	@Test
	void theLastPageOfTheLargestSizeHasAnOffsetThatFitsAnInt() {
		assertThat((long) PageLimits.MAX_PAGE * PageLimits.MAX_SIZE).isLessThanOrEqualTo(Integer.MAX_VALUE);
		assertThat((long) (PageLimits.MAX_PAGE + 1) * PageLimits.MAX_SIZE).isGreaterThan(Integer.MAX_VALUE);
	}

}
