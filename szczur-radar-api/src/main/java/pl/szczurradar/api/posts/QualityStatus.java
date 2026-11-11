package pl.szczurradar.api.posts;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The state of a source's quality window ({@link AssessmentQuality}): computed on every read, never stored
 * (no column, no {@code CHECK}).
 */
public enum QualityStatus {

	/** Enough attempts, and the share of failed ones not above the source's threshold. */
	OK,

	/** Enough attempts, and the share of failed ones strictly above the source's threshold. */
	ABOVE_THRESHOLD,

	/** Fewer than {@link AssessmentQuality#MIN_ATTEMPTS} attempts: the threshold is not checked. */
	NOT_ENOUGH_ATTEMPTS;

	/** The API value, e.g. {@code above_threshold}. */
	@JsonValue
	public String value() {
		return name().toLowerCase(Locale.ROOT);
	}

}
