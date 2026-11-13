package pl.szczurradar.api.posts;

import org.jspecify.annotations.Nullable;

/**
 * A source whose share of theses in another language crossed its threshold tonight
 * ({@link AssessmentQuality#languageCrossed}), with what the previous night's summary saw of it.
 *
 * @param tonight the source in tonight's window, {@link QualityStatus#ABOVE_THRESHOLD}
 * @param before the same source in the window of the night before (cut at the previous night's summary);
 *     {@code null} when it had no thesis there
 */
public record LanguageCrossing(SourceLanguage tonight, @Nullable SourceLanguage before) {
}
