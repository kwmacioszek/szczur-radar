package pl.szczurradar.api.posts;

import org.jspecify.annotations.Nullable;

/**
 * A source that crossed its threshold tonight ({@link AssessmentQuality#crossed}), with what the previous
 * night's summary saw of it — the alarm names both.
 *
 * @param tonight the source in tonight's window, {@link QualityStatus#ABOVE_THRESHOLD}
 * @param before the same source in the window of the night before (cut at the previous night's summary);
 *     {@code null} when it had no attempt there
 */
public record QualityCrossing(SourceQuality tonight, @Nullable SourceQuality before) {
}
