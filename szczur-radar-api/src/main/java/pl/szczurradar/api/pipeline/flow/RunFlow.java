package pl.szczurradar.api.pipeline.flow;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import pl.szczurradar.api.pipeline.run.PipelineRun;
import pl.szczurradar.api.pipeline.run.RunKind;
import pl.szczurradar.api.pipeline.run.RunStatus;
import pl.szczurradar.api.pipeline.run.SourceFetchStatus;
import pl.szczurradar.api.sources.Source;
import pl.szczurradar.api.sources.SourcePost;

/**
 * The data the steps of {@link RunSequence} hand to each other, in this order:
 *
 * <pre>
 * RunRequest → RANGE: RangeTarget → FetchedBatch → StoredBatch → PendingAssessments
 *            → RETRY / POSTS: SelectionTarget → PendingAssessments
 * PendingAssessments → AssessedBatch → RunSummary
 * </pre>
 *
 * Future ML stages go in as new types and steps between {@link StoredBatch} and
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

		public static RunRequest of(PipelineRun run) {
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

	/**
	 * What one source of a range run gave ({@code pipeline_run_sources}): reported as soon as the source
	 * ended, so its result is written before the next source starts.
	 *
	 * @param fetched elements the source returned (0 when it failed)
	 * @param skipped elements that could not become a post (0 when it failed)
	 * @param error why the source failed, {@code <SimpleName>: <message>} as a run's error; only for
	 *     {@link SourceFetchStatus#FAILED}, otherwise {@code null}
	 */
	public record SourceFetch(Source source, SourceFetchStatus status, int fetched, int skipped,
			@Nullable String error) {

		public SourceFetch {
			Objects.requireNonNull(source, "source");
			Objects.requireNonNull(status, "status");
			if ((status == SourceFetchStatus.FAILED) != (error != null)) {
				throw new IllegalArgumentException("a failed source has an error, any other none: " + status);
			}
		}

		/**
		 * The run's error text about the sources that did not succeed, in their order and joined with
		 * {@code "; "}: {@code <source> failed: <error>} or {@code <source> gave no post to store (fetched N,
		 * skipped M)}; empty when every source succeeded.
		 */
		public static String summary(List<SourceFetch> sources) {
			return sources.stream()
				.filter(source -> source.status() != SourceFetchStatus.SUCCEEDED)
				.map(SourceFetch::problem)
				.collect(Collectors.joining("; "));
		}

		private String problem() {
			return status == SourceFetchStatus.FAILED ? source.value() + " failed: " + error
					: source.value() + " gave no post to store (fetched " + fetched + ", skipped " + skipped + ")";
		}

	}

	/**
	 * What a range run read from its sources: the posts of every source that gave some, and each source's
	 * result.
	 *
	 * @param fetched elements the sources returned
	 * @param skipped elements that could not become a post (no id, unsupported type, no text, no time,
	 *     unexpected shape); their raw payload is not stored
	 * @param sources each source's result, in the order they were fetched
	 */
	public record FetchedBatch(UUID runId, List<SourcePost> posts, int fetched, int skipped,
			List<SourceFetch> sources) {

		public FetchedBatch {
			posts = List.copyOf(posts);
			sources = List.copyOf(sources);
		}

		/** Whether a source did not succeed (it failed or gave no post): the run ends {@code partial} at best. */
		public boolean degraded() {
			return sources.stream().anyMatch(source -> source.status() != SourceFetchStatus.SUCCEEDED);
		}

		/** The run's error text about the sources that did not succeed ({@link SourceFetch#summary}), or empty. */
		public String summary() {
			return SourceFetch.summary(sources);
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
	 * What the assess step did: the run's status and error come from it. The run's counters are not
	 * written from it — the dispatcher counts each post as it finishes.
	 *
	 * @param ok posts with a stored {@code ok} assessment
	 * @param failed posts with a stored {@code failed} assessment (including those that became dead)
	 * @param dead posts whose third failed attempt made them dead
	 * @param processingErrors posts with a technical error and no stored assessment (also a single outage of
	 *     the LLM provider the step went on after)
	 * @param stoppedBy why the step stopped before assessing every post — the LLM check failed, the
	 *     provider refused the configuration or was unavailable for several calls in a row — or {@code null}
	 */
	public record AssessedBatch(UUID runId, int ok, int failed, int dead, List<ProcessingError> processingErrors,
			@Nullable String stoppedBy) {

		public AssessedBatch {
			processingErrors = List.copyOf(processingErrors);
		}

		/**
		 * The run's error text: why the step stopped, and when posts had technical errors, how many and
		 * each post with its error.
		 */
		public Optional<String> errorSummary() {
			List<String> parts = new ArrayList<>();
			if (stoppedBy != null) {
				parts.add("stopped, the remaining posts were put back: " + stoppedBy);
			}
			if (!processingErrors.isEmpty()) {
				parts.add(processingErrors.size() + " post(s) with a technical error, no assessment stored: "
						+ processingErrors.stream()
							.map(error -> error.postId() + " (" + error.message() + ")")
							.collect(Collectors.joining("; ")));
			}
			return parts.isEmpty() ? Optional.empty() : Optional.of(String.join(" | ", parts));
		}

		/**
		 * {@code failed} when the step stopped; {@code succeeded} without failed or dead posts and
		 * technical errors; otherwise {@code partial}.
		 */
		public RunStatus status() {
			if (stoppedBy != null) {
				return RunStatus.FAILED;
			}
			return failed == 0 && dead == 0 && processingErrors.isEmpty() ? RunStatus.SUCCEEDED : RunStatus.PARTIAL;
		}

	}

	/** A post whose assessment could not be stored because of a technical error (a bug, a failed write, an outage). */
	public record ProcessingError(UUID postId, String message) {
	}

	/** The run's final status, written to {@code pipeline_runs}. */
	public record RunSummary(UUID runId, RunStatus status) {
	}

}
