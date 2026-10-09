package pl.szczurradar.api.pipeline.run;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** The steps of pipeline runs. */
public interface PipelineRunStepRepository extends JpaRepository<PipelineRunStep, UUID> {

	/** The steps of {@code runId} in the order they started. */
	List<PipelineRunStep> findByRunIdOrderByStartedAtAscIdAsc(UUID runId);

	/** The steps of all {@code runIds} (one query for a page of runs), in the order they started. */
	List<PipelineRunStep> findByRunIdInOrderByStartedAtAscIdAsc(Collection<UUID> runIds);

}
