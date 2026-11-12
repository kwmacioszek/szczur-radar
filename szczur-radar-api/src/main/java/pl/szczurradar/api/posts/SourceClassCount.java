package pl.szczurradar.api.posts;

import org.jspecify.annotations.Nullable;

import pl.szczurradar.api.agents.assessor.validation.FailureClass;
import pl.szczurradar.api.sources.Source;

/**
 * How many assessment attempts with a model call one source has in one failure class over a time window, a
 * row of {@link AssessmentRepository#countModelAttemptsBySourceAndClass}; {@link AssessmentQuality} sums them.
 *
 * @param source the posts' source
 * @param failureClass the class of the failed attempts; {@code null} for the {@code ok} ones
 * @param count the attempts counted
 */
public record SourceClassCount(Source source, @Nullable FailureClass failureClass, long count) {
}
