package pl.szczurradar.api.pipeline.run;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * The results of the sources of range runs; written only by {@code pipeline/state/RunRecorder.source}.
 * Unordered: the reader puts them in {@link pl.szczurradar.api.sources.Source} order.
 */
public interface PipelineRunSourceRepository extends JpaRepository<PipelineRunSource, UUID> {

	/** The results of {@code runId}'s sources. */
	List<PipelineRunSource> findByRunId(UUID runId);

	/** The results of the sources of all {@code runIds} (one query for a page of runs). */
	List<PipelineRunSource> findByRunIdIn(Collection<UUID> runIds);

}
