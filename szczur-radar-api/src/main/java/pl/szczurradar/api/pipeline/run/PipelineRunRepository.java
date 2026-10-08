package pl.szczurradar.api.pipeline.run;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Pipeline runs. Status and counters change only through conditional updates, never a blind save. */
public interface PipelineRunRepository extends JpaRepository<PipelineRun, UUID> {

	/** Whether a run is queued or running (then a new request is refused). */
	default boolean existsActive() {
		return existsByStatusIn(List.of(RunStatus.QUEUED, RunStatus.RUNNING));
	}

	boolean existsByStatusIn(Collection<RunStatus> statuses);

}
