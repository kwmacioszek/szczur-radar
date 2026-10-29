package pl.szczurradar.api.pipeline.run;

import java.time.Instant;

import pl.szczurradar.api.sources.Source;

/**
 * The range of a covering run and one source it covers ({@link PipelineRunRepository#findCoveringSince}):
 * a {@link RunKind#RANGE} run that {@link RunStatus#SUCCEEDED succeeded} or ended
 * {@link RunStatus#PARTIAL partial}, and its source whose result is {@link SourceFetchStatus#SUCCEEDED
 * succeeded}; one per such source of the run.
 *
 * @param rangeFrom the run's range start
 * @param rangeTo the run's range end
 * @param source the source that gave a post in it
 */
public record CoveredRange(Instant rangeFrom, Instant rangeTo, Source source) {
}
