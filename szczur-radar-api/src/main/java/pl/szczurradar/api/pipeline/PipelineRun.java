package pl.szczurradar.api.pipeline;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

/**
 * One pipeline run ({@code pipeline_runs}, owned by Flyway, {@code V3__create_pipeline_and_posts.sql}).
 * Created {@code queued}; its status and counters change only through conditional updates that
 * require the run to be {@code running}, so an interrupted run never changes again.
 */
@Entity
@Table(name = "pipeline_runs")
public class PipelineRun {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, updatable = false)
	private RunKind kind;

	@Column(nullable = false)
	private RunStatus status;

	/** Start of the range; only {@link RunKind#RANGE}. */
	@Column(name = "range_from", updatable = false)
	private @Nullable Instant rangeFrom;

	/** End of the range, inclusive; only {@link RunKind#RANGE}. */
	@Column(name = "range_to", updatable = false)
	private @Nullable Instant rangeTo;

	/** The posts to assess; only {@link RunKind#POSTS}. */
	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "requested_post_ids", updatable = false)
	private @Nullable List<UUID> requestedPostIds;

	@Column(name = "requested_by", nullable = false, updatable = false)
	private UUID requestedBy;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "started_at")
	private @Nullable Instant startedAt;

	@Column(name = "finished_at")
	private @Nullable Instant finishedAt;

	@Column(nullable = false)
	private int fetched;

	@Column(name = "new_posts", nullable = false)
	private int newPosts;

	@Column(name = "assessed_ok", nullable = false)
	private int assessedOk;

	@Column(name = "assessed_failed", nullable = false)
	private int assessedFailed;

	/** Posts with a technical error for which no assessment was stored; not failed LLM attempts. */
	@Column(name = "processing_errors", nullable = false)
	private int processingErrors;

	@Column(nullable = false)
	private int dead;

	@Column(nullable = false)
	private int skipped;

	private @Nullable String error;

	/** For JPA. */
	protected PipelineRun() {
	}

	private PipelineRun(RunKind kind, @Nullable Instant rangeFrom, @Nullable Instant rangeTo,
			@Nullable List<UUID> requestedPostIds, UUID requestedBy) {
		this.kind = kind;
		this.status = RunStatus.QUEUED;
		this.rangeFrom = rangeFrom;
		this.rangeTo = rangeTo;
		this.requestedPostIds = requestedPostIds;
		this.requestedBy = Objects.requireNonNull(requestedBy, "requestedBy");
	}

	/** A queued run over the source's posts added between {@code from} and {@code to} (inclusive). */
	public static PipelineRun range(Instant from, Instant to, UUID requestedBy) {
		if (from.isAfter(to)) {
			throw new IllegalArgumentException("range from " + from + " is after " + to);
		}
		return new PipelineRun(RunKind.RANGE, from, to, null, requestedBy);
	}

	/** A queued run over the leftovers of earlier runs. */
	public static PipelineRun retry(UUID requestedBy) {
		return new PipelineRun(RunKind.RETRY, null, null, null, requestedBy);
	}

	/** A queued run over the given posts. */
	public static PipelineRun posts(List<UUID> postIds, UUID requestedBy) {
		if (postIds.isEmpty()) {
			throw new IllegalArgumentException("a posts run needs at least one post");
		}
		return new PipelineRun(RunKind.POSTS, null, null, List.copyOf(postIds), requestedBy);
	}

	public UUID getId() {
		return id;
	}

	public RunKind getKind() {
		return kind;
	}

	public RunStatus getStatus() {
		return status;
	}

	public @Nullable Instant getRangeFrom() {
		return rangeFrom;
	}

	public @Nullable Instant getRangeTo() {
		return rangeTo;
	}

	public @Nullable List<UUID> getRequestedPostIds() {
		return requestedPostIds;
	}

	public UUID getRequestedBy() {
		return requestedBy;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public @Nullable Instant getStartedAt() {
		return startedAt;
	}

	public @Nullable Instant getFinishedAt() {
		return finishedAt;
	}

	public int getFetched() {
		return fetched;
	}

	public int getNewPosts() {
		return newPosts;
	}

	public int getAssessedOk() {
		return assessedOk;
	}

	public int getAssessedFailed() {
		return assessedFailed;
	}

	public int getProcessingErrors() {
		return processingErrors;
	}

	public int getDead() {
		return dead;
	}

	public int getSkipped() {
		return skipped;
	}

	public @Nullable String getError() {
		return error;
	}

}
