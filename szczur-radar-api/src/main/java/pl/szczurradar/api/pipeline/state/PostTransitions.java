package pl.szczurradar.api.pipeline.state;

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

/**
 * The stage changes of a post during assessment, each one conditional {@code UPDATE} (no row
 * locks) that also sets {@code updated_at}. A post being assessed has an owner
 * ({@code assessing_run_id}); a result is stored only while the run still owns the post, and only
 * the owner puts a post back. So a worker of an interrupted run (e.g. during a deployment overlap)
 * never changes a post another run took over. Stage and status values are bound from their enums,
 * never written into the SQL.
 */
@Component
public class PostTransitions {

	/**
	 * Back to the stage before the attempt, without counting one (no failed attempt yet = stored); the
	 * callers add which posts.
	 */
	private static final String RELEASE = "UPDATE posts SET stage = CASE WHEN assessment_attempts = 0 THEN :stored"
			+ " ELSE :failed END, assessing_run_id = NULL, updated_at = now()"
			+ " WHERE stage = :assessing";

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
	 * owns the post; otherwise nothing changes.
	 *
	 * @param attemptsBefore what {@link #claim} returned
	 * @return whether it was stored
	 */
	public boolean record(UUID postId, UUID runId, int attemptsBefore, Assessment assessment) {
		Outcome outcome = Outcome.after(assessment.getStatus() == AssessmentResult.Status.OK, attemptsBefore);
		return Boolean.TRUE.equals(transaction.execute(status -> {
			int moved = jdbc
				.sql("UPDATE posts SET stage = :stage, assessment_attempts = :attempts, assessing_run_id = NULL,"
						+ " updated_at = now() WHERE id = :id AND stage = :assessing AND assessing_run_id = :run")
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
	 */
	public void release(UUID postId, UUID runId) {
		stages(jdbc.sql(RELEASE + " AND assessing_run_id = :run AND id = :id")).param("run", runId)
			.param("id", postId)
			.update();
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
