package pl.szczurradar.api.pipeline;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Pipeline runs. Status and counters change only through conditional updates, never a blind save. */
public interface PipelineRunRepository extends JpaRepository<PipelineRun, UUID> {

}
