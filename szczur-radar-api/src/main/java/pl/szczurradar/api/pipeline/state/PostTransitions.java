package pl.szczurradar.api.pipeline.state;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import pl.szczurradar.api.agents.assessor.AssessmentResult;
import pl.szczurradar.api.pipeline.run.RunStatus;
import pl.szczurradar.api.posts.Assessment;
import pl.szczurradar.api.posts.AssessmentRepository;
import pl.szczurradar.api.posts.Post;
import pl.szczurradar.api.posts.PostStage;
import pl.szczurradar.api.text.Nul;

/**
 * The stage changes of a post during assessment, each one conditional {@code UPDATE} (no row
 * locks) that also sets {@code updated_at}. A post being assessed has an owner
 * ({@code assessing_run_id}); a result is stored only while the run still owns the post, and only
 * the owner puts a post back. So a worker of an interrupted run (e.g. during a deployment overlap)
 * never changes a post another run took over. Stage and status values are bound from their enums,
 * never written into the SQL. A technical failure (an error of our code, no attempt) is counted on the
 * post with its message; every stored result resets the count ({@link Post#MAX_TECHNICAL_FAILURES} in a
 * row put the post on hold).
 */
@Component
public class PostTransitions {

	/** The longest technical error kept on a post; a longer one is cut, as a provider's message. */
	static final int MAX_ERROR_LENGTH = 500;

	/**
	 * Back to the stage before the attempt, without counting one (no failed attempt yet = stored); the
	 * callers may add to the {@code SET} list ({@link #COUNT_FAILURE}), then {@link #RELEASE_WHERE} and
	 * which posts.
	 */
	private static final String RELEASE_SET = "UPDATE posts SET stage = CASE WHEN assessment_attempts = 0 THEN :stored"
			+ " ELSE :failed END, assessing_run_id = NULL, updated_at = now()";

	private static final String RELEASE_WHERE = " WHERE stage = :assessing";

	private static final String RELEASE = RELEASE_SET + RELEASE_WHERE;

	/** One more technical failure, with its message ({@code :error}, see {@link #error}). */
	private static final String COUNT_FAILURE = ", technical_failures = technical_failures + 1, last_technical_error = :error";

	private final JdbcClient jdbc;

	private final AssessmentRepository assessments;

	private final TransactionTemplate transaction;

	PostTransitions(JdbcClient jdbc, AssessmentRepository assessments, TransactionTemplate transaction) {
		this.jdbc = jdbc;
		this.assessments = assessments;
		this.transaction = transaction;
	}

	/**
	 * Takes a stored or failed post for {@code runId}, only while the run is running.
	 *
	 * @return the post's failed attempts so far, or empty when the post is not assessable (assessed,
	 *     dead, held by another run) or the run is no longer running
	 */
	public Optional<Integer> claim(UUID postId, UUID runId) {
		return stages(jdbc
			.sql("UPDATE posts SET stage = :assessing, assessing_run_id = :run, updated_at = now()"
					+ " WHERE id = :id AND stage IN (:stored, :failed)"
					+ " AND EXISTS (SELECT 1 FROM pipeline_runs WHERE id = :run AND status = :running)"
					+ " RETURNING assessment_attempts"))
			.param("running", RunStatus.RUNNING.value())
			.param("run", runId)
			.param("id", postId)
			.query(Integer.class)
			.optional();
	}

	/**
	 * Stores {@code assessment} and moves the post on, in one transaction, only while {@code runId}
	 * owns the post; otherwise nothing changes. A stored result (ok or failed) resets the post's technical
	 * failures and their last message.
	 *
	 * @param attemptsBefore what {@link #claim} returned
	 * @return whether it was stored
	 */
	public boolean record(UUID postId, UUID runId, int attemptsBefore, Assessment assessment) {
		Outcome outcome = Outcome.after(assessment.getStatus() == AssessmentResult.Status.OK, attemptsBefore);
		return Boolean.TRUE.equals(transaction.execute(status -> {
			int moved = jdbc
				.sql("UPDATE posts SET stage = :stage, assessment_attempts = :attempts, assessing_run_id = NULL,"
						+ " technical_failures = 0, last_technical_error = NULL, updated_at = now()"
						+ " WHERE id = :id AND stage = :assessing AND assessing_run_id = :run")
				.param("stage", outcome.stage().value())
				.param("attempts", outcome.attempts())
				.param("assessing", PostStage.ASSESSING.value())
				.param("id", postId)
				.param("run", runId)
				.update();
			if (moved != 1) {
				return false;
			}
			assessments.save(assessment);
			return true;
		}));
	}

	/**
	 * Puts a post {@code runId} still holds back where it was before the attempt, without an attempt:
	 * {@code stored} when it never failed, otherwise {@code failed}.
	 *
	 * @return whether it put the post back (the run still held it)
	 */
	public boolean release(UUID postId, UUID runId) {
		return stages(jdbc.sql(RELEASE + " AND assessing_run_id = :run AND id = :id")).param("run", runId)
			.param("id", postId)
			.update() == 1;
	}

	/**
	 * {@link #release} after a technical failure of {@code runId} (an error of our code while assessing the
	 * post): in the same statement the post counts one more technical failure and keeps {@code error} as
	 * the last one. Only while {@code runId} still holds the post.
	 *
	 * @return the post's technical failures in a row now, or empty when the run no longer held it
	 */
	public Optional<Integer> releaseFailed(UUID postId, UUID runId, String error) {
		return stages(jdbc.sql(RELEASE_SET + COUNT_FAILURE + RELEASE_WHERE
				+ " AND assessing_run_id = :run AND id = :id RETURNING technical_failures"))
			.param("error", error(error))
			.param("run", runId)
			.param("id", postId)
			.query(Integer.class)
			.optional();
	}

	/**
	 * One more technical failure, with {@code error} as the last one, for a post {@code runId} already put
	 * back ({@code stored} or {@code failed}), and only while that run is still running: a run stopped or
	 * interrupted in the meantime (deploy, watchdog) counts nothing, and while it runs no other run can hold
	 * the post.
	 *
	 * @return whether the post counted it
	 */
	public boolean countTechnicalFailure(UUID postId, UUID runId, String error) {
		return stages(jdbc.sql("UPDATE posts SET updated_at = now()" + COUNT_FAILURE
				+ " WHERE id = :id AND stage IN (:stored, :failed)"
				+ " AND EXISTS (SELECT 1 FROM pipeline_runs WHERE id = :run AND status = :running)"))
			.param("error", error(error))
			.param("running", RunStatus.RUNNING.value())
			.param("run", runId)
			.param("id", postId)
			.update() == 1;
	}

	/** {@link #release} for every post {@code runId} still holds; returns how many. */
	public int releaseAll(UUID runId) {
		return stages(jdbc.sql(RELEASE + " AND assessing_run_id = :run")).param("run", runId).update();
	}

	/**
	 * {@link #release} for every post whose run is no longer queued or running (start-up recovery). It
	 * also catches a post a worker claimed just as its run was interrupted and never put back because
	 * the instance stopped: the claim checks the run's status when its statement starts.
	 *
	 * @return how many posts were put back
	 */
	public int releaseOrphans() {
		return stages(jdbc.sql(RELEASE + " AND NOT EXISTS (SELECT 1 FROM pipeline_runs r"
				+ " WHERE r.id = posts.assessing_run_id AND r.status IN (:queued, :running))"))
			.param("queued", RunStatus.QUEUED.value())
			.param("running", RunStatus.RUNNING.value())
			.update();
	}

	/**
	 * A technical error as the post keeps it: at most {@value #MAX_ERROR_LENGTH} characters (an SDK's
	 * message must not bloat the row) and without U+0000, which the database rejects ({@link Nul}).
	 */
	static String error(String error) {
		String text = Objects.requireNonNull(Nul.replaced(error));
		return text.length() > MAX_ERROR_LENGTH ? text.substring(0, MAX_ERROR_LENGTH) + "…" : text;
	}

	/** Binds the stages the statements above name. */
	private static StatementSpec stages(StatementSpec statement) {
		return statement.param("stored", PostStage.STORED.value())
			.param("failed", PostStage.FAILED.value())
			.param("assessing", PostStage.ASSESSING.value());
	}

	/**
	 * Where a post goes after a stored attempt: {@code assessed} (attempts unchanged) for an ok
	 * result; for a failed one the attempt counts, and the {@value Post#MAX_ASSESSMENT_ATTEMPTS}rd
	 * makes it {@code dead}.
	 */
	record Outcome(PostStage stage, int attempts) {

		static Outcome after(boolean ok, int attemptsBefore) {
			if (ok) {
				return new Outcome(PostStage.ASSESSED, attemptsBefore);
			}
			int attempts = attemptsBefore + 1;
			return new Outcome(attempts >= Post.MAX_ASSESSMENT_ATTEMPTS ? PostStage.DEAD : PostStage.FAILED, attempts);
		}

	}

}
