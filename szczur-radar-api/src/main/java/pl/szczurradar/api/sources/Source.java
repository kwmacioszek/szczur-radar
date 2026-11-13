package pl.szczurradar.api.sources;

import java.util.Arrays;
import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonValue;

import pl.szczurradar.api.text.Language;

/**
 * A data source of posts; {@link #value()} is the stored and API value. Each source declares its threshold
 * of failed assessment attempts ({@link #failureThresholdPercent()}), the language of its posts
 * ({@link #language()}) and its threshold of theses in another language ({@link #otherLanguageThresholdPercent()}),
 * so a new source does not compile without them.
 */
public enum Source {

	WYKOP(5, Language.PL, 2),

	/**
	 * Registered (the value in every {@code CHECK} set of a source, {@code V12}) and implemented in
	 * {@code sources/reddit} (S-27): configured only when {@code REDDIT_SUBREDDITS} names a subreddit
	 * ({@code RedditConfiguration}); without it a range request naming it is refused.
	 */
	REDDIT(25, Language.EN, 30);

	private final int failureThresholdPercent;

	private final Language language;

	private final int otherLanguageThresholdPercent;

	Source(int failureThresholdPercent, Language language, int otherLanguageThresholdPercent) {
		this.failureThresholdPercent = failureThresholdPercent;
		this.language = language;
		this.otherLanguageThresholdPercent = otherLanguageThresholdPercent;
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

	/**
	 * The language of the source's posts, stored with each of its posts ({@code posts.language}, S-36): the
	 * language the model's theses must be in.
	 */
	public Language language() {
		return language;
	}

	/**
	 * The share of theses of successful model assessments in another language than their post, in percent of
	 * the source's theses over the quality window ({@code posts/AssessmentQuality}, S-36), above which the source
	 * needs the operator's attention: a share strictly greater than this is above it. Set from production data
	 * of 2026-11-12 ({@code context/changes/statement-language-quality/evidence/README.md}: 7-day windows of Wykop
	 * at 0.09–0.86 %, of Reddit at 14.01–20.76 %); a constant, so changing it is a PR, never a setting.
	 */
	public int otherLanguageThresholdPercent() {
		return otherLanguageThresholdPercent;
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
