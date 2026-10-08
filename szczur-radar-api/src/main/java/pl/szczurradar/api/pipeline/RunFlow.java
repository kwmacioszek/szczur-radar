package pl.szczurradar.api.pipeline;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.embabel.agent.api.common.SomeOf;
import org.jspecify.annotations.Nullable;

import pl.szczurradar.api.sources.Source;
import pl.szczurradar.api.sources.SourcePost;

/**
 * The types on the blackboard of {@link PipelineAgent}. The agent's graph follows from them (GOAP):
 *
 * <pre>
 * RunRequest → RouteRun = RangeTarget | SelectionTarget
 * RangeTarget → FetchedBatch → StoredBatch → PendingAssessments
 * SelectionTarget → PendingAssessments
 * PendingAssessments → AssessedBatch → RunSummary (goal)
 * </pre>
 *
 * Future ML stages go in as new types and actions between {@link StoredBatch} and
 * {@link PendingAssessments}.
 */
public final class RunFlow {

	private RunFlow() {
	}

	/** The run to execute, as stored in {@code pipeline_runs}. */
	public record RunRequest(UUID runId, RunKind kind, @Nullable Instant rangeFrom, @Nullable Instant rangeTo,
			List<Source> sources, @Nullable List<UUID> postIds) {

		public RunRequest {
			Objects.requireNonNull(runId, "runId");
			Objects.requireNonNull(kind, "kind");
			sources = List.copyOf(sources);
		}

		static RunRequest of(PipelineRun run) {
			return new RunRequest(run.getId(), run.getKind(), run.getRangeFrom(), run.getRangeTo(), run.getSources(),
					run.getRequestedPostIds());
		}

	}

	/** A range run: the posts of {@code sources} added between {@code from} and {@code to} (inclusive). */
	public record RangeTarget(UUID runId, Instant from, Instant to, List<Source> sources) {

		public RangeTarget {
			sources = List.copyOf(sources);
		}

	}

	/** A run over chosen posts: the leftovers ({@link RunKind#RETRY}) or the given ids ({@link RunKind#POSTS}). */
	public record SelectionTarget(UUID runId, RunKind kind, List<UUID> postIds) {

		public SelectionTarget {
			postIds = List.copyOf(postIds);
		}

	}

	/** Exactly one of the two is set; only the set one goes on the blackboard. */
	public record RouteRun(@Nullable RangeTarget range, @Nullable SelectionTarget selection) implements SomeOf {
	}

	/**
	 * What a range run read from the source.
	 *
	 * @param fetched elements the source returned
	 * @param skipped elements that could not become a post (no id, unsupported type, no text, no time,
	 *     unexpected shape); their raw payload is not stored
	 */
	public record FetchedBatch(UUID runId, List<SourcePost> posts, int fetched, int skipped) {

		public FetchedBatch {
			posts = List.copyOf(posts);
		}

	}

	/** The posts this run stored for the first time; duplicates of stored posts are not in it. */
	public record StoredBatch(UUID runId, List<UUID> newPostIds) {

		public StoredBatch {
			newPostIds = List.copyOf(newPostIds);
		}

	}

	/** The posts to assess, in order. */
	public record PendingAssessments(UUID runId, List<UUID> postIds) {

		public PendingAssessments {
			postIds = List.copyOf(postIds);
		}

	}

	/**
	 * What the assess step did.
	 *
	 * @param ok posts with a stored {@code ok} assessment
	 * @param failed posts with a stored {@code failed} assessment (including those that became dead)
	 * @param dead posts whose third failed attempt made them dead
	 * @param processingErrors posts with a technical error and no stored assessment
	 */
	public record AssessedBatch(UUID runId, int ok, int failed, int dead, List<ProcessingError> processingErrors) {

		public AssessedBatch {
			processingErrors = List.copyOf(processingErrors);
		}

		/** {@code succeeded} without failed or dead posts and technical errors, otherwise {@code partial}. */
		public RunStatus status() {
			return failed == 0 && dead == 0 && processingErrors.isEmpty() ? RunStatus.SUCCEEDED : RunStatus.PARTIAL;
		}

	}

	/** A post whose assessment could not be stored because of a technical error (a bug, a failed write). */
	public record ProcessingError(UUID postId, String message) {
	}

	/** The goal: the run's final status, written to {@code pipeline_runs}. */
	public record RunSummary(UUID runId, RunStatus status) {
	}

}
