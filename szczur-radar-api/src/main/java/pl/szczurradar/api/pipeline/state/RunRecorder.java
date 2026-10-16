package pl.szczurradar.api.pipeline.state;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import pl.szczurradar.api.pipeline.run.InterruptionCause;
import pl.szczurradar.api.pipeline.run.RunStatus;
import pl.szczurradar.api.pipeline.run.RunStep;
import pl.szczurradar.api.pipeline.run.StepStatus;
import pl.szczurradar.api.posts.PostStage;

/**
 * Writes a run's state, steps and counters. Every change of a run is one conditional {@code UPDATE}
 * that requires the expected status (no row locks), so a run that was interrupted meanwhile — e.g. by
 * a newer deployment's recovery — never changes again, and another run is never touched. Every change
 * while the run is running also moves its {@code last_activity_at}.
 *
 * <p>A step is opened only in a running run and closed only once: an interruption closes the steps
 * its runs left open ({@code interrupted}), the watchdog those of a run it stops ({@code failed}), and a
 * worker that finishes later leaves them as they are.
 * Status values are bound from their enums, never written into the SQL.
 */
@Component
public class RunRecorder {

	/** The longest error text kept on a run or step. */
	static final int MAX_ERROR = 2000;

	/**
	 * Whether a run's {@code deployment_id} names another deployment than {@code :current}: then a newer
	 * deployment took over; an unknown id on either side is a restart.
	 */
	private static final String BY_ANOTHER_DEPLOYMENT = "deployment_id IS NOT NULL"
			+ " AND CAST(:current AS TEXT) IS NOT NULL AND deployment_id <> CAST(:current AS TEXT)";

	private static final Logger log = LoggerFactory.getLogger(RunRecorder.class);

	private final JdbcClient jdbc;

	private final TransactionTemplate transaction;

	RunRecorder(JdbcClient jdbc, TransactionTemplate transaction) {
		this.jdbc = jdbc;
		this.transaction = transaction;
	}

	/** {@code queued} → {@code running}; {@code false} when the run is no longer queued (then it must not run). */
	public boolean start(UUID runId) {
		return statuses(jdbc.sql("UPDATE pipeline_runs SET status = :running, started_at = now(),"
				+ " last_activity_at = now() WHERE id = :id AND status = :queued"))
			.param("id", runId)
			.update() == 1;
	}

	/** Whether the run is still running here (not finished or interrupted). */
	public boolean isRunning(UUID runId) {
		return statuses(jdbc.sql("SELECT count(*) FROM pipeline_runs WHERE id = :id AND status = :running"))
			.param("id", runId)
			.query(Integer.class)
			.single() == 1;
	}

	/** {@code running} → {@code status}; {@code false} when the run was not running anymore. */
	public boolean finish(UUID runId, RunStatus status, @Nullable String error) {
		return statuses(jdbc
			.sql("UPDATE pipeline_runs SET status = :status, finished_at = now(), error = COALESCE(:error, error)"
					+ " WHERE id = :id AND status = :running"))
			.param("status", status.value())
			.param("error", error == null ? null : truncate(error))
			.param("id", runId)
			.update() == 1;
	}

	/**
	 * {@code queued} or {@code running} → {@code failed} with {@code error}: a run that cannot go on,
	 * e.g. after an exception, or one that ended without a summary. No change for a run already
	 * finished or interrupted.
	 */
	public void fail(UUID runId, String error) {
		statuses(jdbc.sql("UPDATE pipeline_runs SET status = :failed, finished_at = now(), error = :error"
				+ " WHERE id = :id AND status IN (:queued, :running)"))
			.param("failed", RunStatus.FAILED.value())
			.param("error", truncate(error))
			.param("id", runId)
			.update();
	}

	/** Adds to the run's counters while it is running, and moves its last activity. */
	public void count(UUID runId, Counters delta) {
		statuses(jdbc.sql("UPDATE pipeline_runs SET fetched = fetched + :fetched, new_posts = new_posts + :newPosts,"
				+ " assessed_ok = assessed_ok + :ok, assessed_failed = assessed_failed + :failed,"
				+ " processing_errors = processing_errors + :errors, dead = dead + :dead, skipped = skipped + :skipped,"
				+ " llm_retries = llm_retries + :retries, flex_fallbacks = flex_fallbacks + :fallbacks,"
				+ " last_activity_at = now()"
				+ " WHERE id = :id AND status = :running"))
			.param("fetched", delta.fetched())
			.param("newPosts", delta.newPosts())
			.param("ok", delta.assessedOk())
			.param("failed", delta.assessedFailed())
			.param("errors", delta.processingErrors())
			.param("dead", delta.dead())
			.param("skipped", delta.skipped())
			.param("retries", delta.llmRetries())
			.param("fallbacks", delta.flexFallbacks())
			.param("id", runId)
			.update();
	}

	/** Sets the run's error text while it is running (e.g. the summary of technical errors). */
	public void error(UUID runId, String error) {
		statuses(jdbc.sql("UPDATE pipeline_runs SET error = :error WHERE id = :id AND status = :running"))
			.param("error", truncate(error))
			.param("id", runId)
			.update();
	}

	/** How many posts the run holds in assessment now (index {@code ix_posts_assessing_run}). */
	public int inFlight(UUID runId) {
		return jdbc.sql("SELECT count(*) FROM posts WHERE stage = :assessing AND assessing_run_id = :run")
			.param("assessing", PostStage.ASSESSING.value())
			.param("run", runId)
			.query(Integer.class)
			.single();
	}

	/**
	 * Runs {@code work} as a recorded step: {@code running} before, {@code succeeded} with its output
	 * count after, {@code failed} with the error when it throws (the exception is rethrown). The step's
	 * start and end move the run's last activity.
	 *
	 * @throws RunNoLongerRunningException without running {@code work} when the run is no longer
	 *     running: no step is added to a run that is over
	 */
	public <T> T step(UUID runId, RunStep step, @Nullable Integer itemsIn, Supplier<T> work, ToIntFunction<T> itemsOut) {
		UUID stepId = openStep(runId, step, itemsIn).orElseThrow(() -> new RunNoLongerRunningException(runId, step));
		log.info("Run {} step {} started{}", runId, step.value(), itemsIn == null ? "" : " (in " + itemsIn + ")");
		long started = System.nanoTime();
		T result;
		try {
			result = work.get();
		}
		catch (RuntimeException ex) {
			endStep(runId, stepId, step, StepStatus.FAILED, null, String.valueOf(ex.getMessage()), started);
			throw ex;
		}
		endStep(runId, stepId, step, StepStatus.SUCCEEDED, itemsOut.applyAsInt(result), null, started);
		return result;
	}

	/**
	 * The ids of {@code runIds} still queued or running, now {@code interrupted} because of
	 * {@code cause}, with their open steps closed, in one transaction.
	 */
	public List<UUID> interrupt(List<UUID> runIds, InterruptionCause cause) {
		if (runIds.isEmpty()) {
			return List.of();
		}
		return inTransaction(() -> closeSteps(statuses(jdbc
			.sql("UPDATE pipeline_runs SET status = :interrupted, finished_at = now(), interruption_cause = :cause,"
					+ " error = :reason WHERE id = ANY(:ids) AND status IN (:queued, :running) RETURNING id"))
			.param("cause", cause.value())
			.param("reason", cause.error())
			.param("ids", runIds.toArray(UUID[]::new))
			.query(UUID.class)
			.list(), StepStatus.INTERRUPTED, null));
	}

	/**
	 * {@code running} → {@code failed} when the run made no progress for {@code threshold} (its last
	 * activity, or its start, is older), with its open steps closed as {@code failed} with the same
	 * error, in one transaction (the watchdog). The age is measured by the database's clock, which also
	 * wrote the activity.
	 *
	 * @return whether the run was stopped; {@code false} when it is not running or made progress meanwhile
	 */
	public boolean stall(UUID runId, Duration threshold) {
		String error = "no progress for " + Durations.format(threshold) + ": the run was stopped";
		return !inTransaction(() -> closeSteps(statuses(jdbc
			.sql("UPDATE pipeline_runs SET status = :failed, finished_at = now(), error = :error WHERE id = :id"
					+ " AND status = :running AND COALESCE(last_activity_at, started_at)"
					+ " < now() - CAST(:millis AS BIGINT) * INTERVAL '1 millisecond' RETURNING id"))
			.param("failed", RunStatus.FAILED.value())
			.param("error", error)
			.param("id", runId)
			.param("millis", threshold.toMillis())
			.query(UUID.class)
			.list(), StepStatus.FAILED, error)).isEmpty();
	}

	/**
	 * Every queued or running run, now {@code interrupted}, with its open steps closed, in one
	 * transaction (start-up recovery). The cause is {@link InterruptionCause#DEPLOY} for a run queued by
	 * another deployment than {@code currentDeploymentId}, otherwise {@link InterruptionCause#RESTART}
	 * (the same deployment, or either one unknown).
	 */
	public List<UUID> interruptAll(@Nullable String currentDeploymentId) {
		return inTransaction(() -> closeSteps(statuses(jdbc
			.sql("UPDATE pipeline_runs SET status = :interrupted, finished_at = now(),"
					+ " interruption_cause = CASE WHEN " + BY_ANOTHER_DEPLOYMENT + " THEN :deploy ELSE :restart END,"
					+ " error = CASE WHEN " + BY_ANOTHER_DEPLOYMENT + " THEN :deployError ELSE :restartError END"
					+ " WHERE status IN (:queued, :running) RETURNING id"))
			.param("current", currentDeploymentId)
			.param("deploy", InterruptionCause.DEPLOY.value())
			.param("restart", InterruptionCause.RESTART.value())
			.param("deployError", InterruptionCause.DEPLOY.error())
			.param("restartError", InterruptionCause.RESTART.error())
			.query(UUID.class)
			.list(), StepStatus.INTERRUPTED, null));
	}

	/**
	 * A new step of a running run, and the run's last activity moved, in one statement; empty when the
	 * run is no longer running. The run's row is updated first, so an interruption either waits for the
	 * new step (and closes it) or makes this add none.
	 */
	private Optional<UUID> openStep(UUID runId, RunStep step, @Nullable Integer itemsIn) {
		return statuses(jdbc
			.sql("WITH run AS (UPDATE pipeline_runs SET last_activity_at = now() WHERE id = :run AND status = :running"
					+ " RETURNING id)"
					+ " INSERT INTO pipeline_run_steps (run_id, step, items_in)"
					+ " SELECT id, :step, CAST(:in AS INTEGER) FROM run RETURNING id"))
			.param("run", runId)
			.param("step", step.value())
			.param("in", itemsIn)
			.query(UUID.class)
			.optional();
	}

	/**
	 * Closes a step that is still {@code running} and moves its run's last activity; a step an
	 * interruption closed meanwhile stays as it is. Two statements without a transaction, so this never
	 * holds a lock while waiting for an interruption's.
	 */
	private void endStep(UUID runId, UUID stepId, RunStep step, StepStatus status, @Nullable Integer itemsOut,
			@Nullable String error, long startedNanos) {
		int closed = steps(jdbc.sql("UPDATE pipeline_run_steps SET status = :status, finished_at = now(),"
				+ " items_out = :out, error = :error WHERE id = :id AND status = :stepRunning"))
			.param("status", status.value())
			.param("out", itemsOut)
			.param("error", error == null ? null : truncate(error))
			.param("id", stepId)
			.update();
		if (closed == 0) {
			log.info("Run {} step {} ended in {} after its run had stopped; the step stays as the run's end left it",
					runId, step.value(), Durations.since(startedNanos));
			return;
		}
		statuses(jdbc.sql("UPDATE pipeline_runs SET last_activity_at = now() WHERE id = :id AND status = :running"))
			.param("id", runId)
			.update();
		log.info("Run {} step {} {}{} in {}", runId, step.value(), status.value(),
				itemsOut == null ? "" : " (out " + itemsOut + ")", Durations.since(startedNanos));
	}

	/**
	 * Closes the steps {@code runIds} left {@code running} as {@code status} with {@code error}; returns
	 * {@code runIds}.
	 */
	private List<UUID> closeSteps(List<UUID> runIds, StepStatus status, @Nullable String error) {
		if (!runIds.isEmpty()) {
			steps(jdbc.sql("UPDATE pipeline_run_steps SET status = :status, finished_at = now(), error = :error"
					+ " WHERE run_id = ANY(:ids) AND status = :stepRunning"))
				.param("status", status.value())
				.param("error", error == null ? null : truncate(error))
				.param("ids", runIds.toArray(UUID[]::new))
				.update();
		}
		return runIds;
	}

	private List<UUID> inTransaction(Supplier<List<UUID>> work) {
		List<UUID> ids = transaction.execute(status -> work.get());
		return ids == null ? List.of() : ids;
	}

	private static String truncate(String text) {
		return text.length() > MAX_ERROR ? text.substring(0, MAX_ERROR) + "…" : text;
	}

	/** Binds the run statuses the statements above name. */
	private static StatementSpec statuses(StatementSpec statement) {
		return statement.param("queued", RunStatus.QUEUED.value())
			.param("running", RunStatus.RUNNING.value())
			.param("interrupted", RunStatus.INTERRUPTED.value());
	}

	/** Binds the open step status the statements above name. */
	private static StatementSpec steps(StatementSpec statement) {
		return statement.param("stepRunning", StepStatus.RUNNING.value());
	}

	/**
	 * Additions to a run's counters.
	 *
	 * @param llmRetries HTTP attempts beyond the first of the assessments' calls
	 * @param flexFallbacks assessments whose call fell back from the flex tier to the default one
	 */
	public record Counters(int fetched, int newPosts, int assessedOk, int assessedFailed, int processingErrors,
			int dead, int skipped, int llmRetries, int flexFallbacks) {

		public static Counters fetched(int fetched, int skipped) {
			return new Counters(fetched, 0, 0, 0, 0, 0, skipped, 0, 0);
		}

		public static Counters stored(int newPosts) {
			return new Counters(0, newPosts, 0, 0, 0, 0, 0, 0, 0);
		}

		/** One post with a stored {@code ok} assessment, its call's retries and whether flex fell back. */
		public static Counters assessedOk(int llmRetries, boolean flexFallback) {
			return new Counters(0, 0, 1, 0, 0, 0, 0, llmRetries, flexFallback ? 1 : 0);
		}

		/**
		 * One post with a stored {@code failed} assessment ({@code dead} when it was its third), its
		 * call's retries and whether flex fell back.
		 */
		public static Counters assessedFailed(boolean dead, int llmRetries, boolean flexFallback) {
			return new Counters(0, 0, 0, 1, 0, dead ? 1 : 0, 0, llmRetries, flexFallback ? 1 : 0);
		}

		/** One post with a technical error: no assessment stored, the post put back. */
		public static Counters processingError() {
			return new Counters(0, 0, 0, 0, 1, 0, 0, 0, 0);
		}

	}

}
