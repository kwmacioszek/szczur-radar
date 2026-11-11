package pl.szczurradar.api.sources;

import java.util.Arrays;
import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * A data source of posts; {@link #value()} is the stored and API value. Each source declares its threshold
 * of failed assessment attempts ({@link #failureThresholdPercent()}), so a new source does not compile
 * without one.
 */
public enum Source {

	WYKOP(5),

	/**
	 * Registered (the value in every {@code CHECK} set of a source, {@code V12}) and implemented in
	 * {@code sources/reddit} (S-27): configured only when {@code REDDIT_SUBREDDITS} names a subreddit
	 * ({@code RedditConfiguration}); without it a range request naming it is refused.
	 */
	REDDIT(25);

	private final int failureThresholdPercent;

	Source(int failureThresholdPercent) {
		this.failureThresholdPercent = failureThresholdPercent;
	}

	/**
	 * The share of failed assessment attempts with a model call, in percent of the source's attempts over the
	 * quality window ({@code posts/AssessmentQuality}, S-35), above which the source needs the operator's
	 * attention: a share strictly greater than this is above it. Set from production data of 2026-11-11
	 * ({@code context/archive/2026-11-11-per-source-failure-quality/evidence/README.md}: Wykop steady at 2.0–2.8 %,
	 * Reddit at 14.6–18.4 %); a constant, so changing it is a PR, never a setting.
	 */
	public int failureThresholdPercent() {
		return failureThresholdPercent;
	}

	@JsonValue
	public String value() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** The source stored as {@code value}. */
	public static Source fromValue(String value) {
		return Arrays.stream(values())
			.filter(source -> source.value().equals(value))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("unknown source: " + value));
	}

}
