package pl.szczurradar.api.posts;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The state of a source's window of a quality measure ({@link AssessmentQuality}: failed attempts, theses in
 * another language): computed on every read, never stored (no column, no {@code CHECK}).
 */
public enum QualityStatus {

	/** Enough data, and the measured share not above the source's threshold. */
	OK,

	/** Enough data, and the measured share strictly above the source's threshold. */
	ABOVE_THRESHOLD,

	/**
	 * Below the measure's minimum ({@link AssessmentQuality#MIN_ATTEMPTS} attempts,
	 * {@link AssessmentQuality#MIN_THESES} theses): the threshold is not checked.
	 */
	NOT_ENOUGH_DATA;

	/** The API value, e.g. {@code above_threshold}. */
	@JsonValue
	public String value() {
		return name().toLowerCase(Locale.ROOT);
	}

}
