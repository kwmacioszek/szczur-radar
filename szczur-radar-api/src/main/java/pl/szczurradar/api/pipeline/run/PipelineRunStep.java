package pl.szczurradar.api.pipeline.run;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.jspecify.annotations.Nullable;

/**
 * One step of a pipeline run ({@code pipeline_run_steps}, owned by Flyway,
 * {@code V3__create_pipeline_and_posts.sql}), created {@code running} when the step starts.
 */
@Entity
@Table(name = "pipeline_run_steps")
public class PipelineRunStep {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "run_id", nullable = false, updatable = false)
	private UUID runId;

	@Column(nullable = false, updatable = false)
	private RunStep step;

	@Column(nullable = false)
	private StepStatus status;

	@CreationTimestamp
	@Column(name = "started_at", nullable = false, updatable = false)
	private Instant startedAt;

	@Column(name = "finished_at")
	private @Nullable Instant finishedAt;

	@Column(name = "items_in")
	private @Nullable Integer itemsIn;

	@Column(name = "items_out")
	private @Nullable Integer itemsOut;

	private @Nullable String error;

	/** For JPA. */
	protected PipelineRunStep() {
	}

	/** A step of {@code runId} starting now. */
	public PipelineRunStep(UUID runId, RunStep step) {
		this.runId = Objects.requireNonNull(runId, "runId");
		this.step = Objects.requireNonNull(step, "step");
		this.status = StepStatus.RUNNING;
	}

	public UUID getId() {
		return id;
	}

	public UUID getRunId() {
		return runId;
	}

	public RunStep getStep() {
		return step;
	}

	public StepStatus getStatus() {
		return status;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public @Nullable Instant getFinishedAt() {
		return finishedAt;
	}

	public @Nullable Integer getItemsIn() {
		return itemsIn;
	}

	public @Nullable Integer getItemsOut() {
		return itemsOut;
	}

	public @Nullable String getError() {
		return error;
	}

}
