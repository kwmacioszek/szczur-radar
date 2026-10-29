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

import pl.szczurradar.api.sources.Source;

/**
 * The result of one source of a range run ({@code pipeline_run_sources}, owned by Flyway,
 * {@code V12__pipeline_run_sources.sql}), once per run and source. Written only by
 * {@code pipeline/state/RunRecorder.source} (one conditional statement with the run's counters, only
 * while the run is running) and never changed; the entity reads it.
 */
@Entity
@Table(name = "pipeline_run_sources")
public class PipelineRunSource {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "run_id", nullable = false, updatable = false)
	private UUID runId;

	@Column(nullable = false, updatable = false)
	private Source source;

	@Column(nullable = false, updatable = false)
	private SourceFetchStatus status;

	/** The elements the source fetched in the range, skipped ones included. */
	@Column(nullable = false, updatable = false)
	private int fetched;

	/** The fetched elements that could not become a post. */
	@Column(nullable = false, updatable = false)
	private int skipped;

	/** Why the source failed ({@code <SimpleName>: <message>}); only a {@link SourceFetchStatus#FAILED failed} one. */
	@Column(updatable = false)
	private @Nullable String error;

	@CreationTimestamp
	@Column(name = "finished_at", nullable = false, updatable = false)
	private Instant finishedAt;

	/** For JPA. */
	protected PipelineRunSource() {
	}

	/** The result of {@code source} in {@code runId}, finishing now; for tests (the application writes it with SQL). */
	public PipelineRunSource(UUID runId, Source source, SourceFetchStatus status, int fetched, int skipped,
			@Nullable String error) {
		this.runId = Objects.requireNonNull(runId, "runId");
		this.source = Objects.requireNonNull(source, "source");
		this.status = Objects.requireNonNull(status, "status");
		this.fetched = fetched;
		this.skipped = skipped;
		this.error = error;
	}

	public UUID getId() {
		return id;
	}

	public UUID getRunId() {
		return runId;
	}

	public Source getSource() {
		return source;
	}

	public SourceFetchStatus getStatus() {
		return status;
	}

	public int getFetched() {
		return fetched;
	}

	public int getSkipped() {
		return skipped;
	}

	public @Nullable String getError() {
		return error;
	}

	public Instant getFinishedAt() {
		return finishedAt;
	}

}
