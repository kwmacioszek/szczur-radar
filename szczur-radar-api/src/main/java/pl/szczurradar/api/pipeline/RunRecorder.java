package pl.szczurradar.api.pipeline;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Writes a run's state, steps and counters. Every change of a run is one conditional {@code UPDATE}
 * that requires the expected status (no row locks), so a run that was interrupted meanwhile — e.g. by
 * a newer deployment's recovery — never changes again, and another run is never touched.
 */
@Component
public class RunRecorder {

	/** The longest error text kept on a run or step. */
	static final int MAX_ERROR = 2000;

	private final JdbcClient jdbc;

	RunRecorder(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** {@code queued} → {@code running}; {@code false} when the run is no longer queued (then it must not run). */
	public boolean start(UUID runId) {
		return jdbc.sql("UPDATE pipeline_runs SET status = 'running', started_at = now() WHERE id = :id AND status = 'queued'")
			.param("id", runId)
			.update() == 1;
	}

	/** Whether the run is still running here (not finished or interrupted). */
	public boolean isRunning(UUID runId) {
		return jdbc.sql("SELECT count(*) FROM pipeline_runs WHERE id = :id AND status = 'running'")
			.param("id", runId)
			.query(Integer.class)
			.single() == 1;
	}

	/** {@code running} → {@code status}; {@code false} when the run was not running anymore. */
	public boolean finish(UUID runId, RunStatus status, @Nullable String error) {
		return jdbc
			.sql("UPDATE pipeline_runs SET status = :status, finished_at = now(), error = COALESCE(:error, error)"
					+ " WHERE id = :id AND status = 'running'")
			.param("status", status.value())
			.param("error", error == null ? null : truncate(error))
			.param("id", runId)
			.update() == 1;
	}

	/** Adds to the run's counters while it is running. */
	public void count(UUID runId, Counters delta) {
		jdbc.sql("UPDATE pipeline_runs SET fetched = fetched + :fetched, new_posts = new_posts + :newPosts,"
				+ " assessed_ok = assessed_ok + :ok, assessed_failed = assessed_failed + :failed,"
				+ " processing_errors = processing_errors + :errors, dead = dead + :dead, skipped = skipped + :skipped"
				+ " WHERE id = :id AND status = 'running'")
			.param("fetched", delta.fetched())
			.param("newPosts", delta.newPosts())
			.param("ok", delta.assessedOk())
			.param("failed", delta.assessedFailed())
			.param("errors", delta.processingErrors())
			.param("dead", delta.dead())
			.param("skipped", delta.skipped())
			.param("id", runId)
			.update();
	}

	/** Sets the run's error text while it is running (e.g. the summary of technical errors). */
	public void error(UUID runId, String error) {
		jdbc.sql("UPDATE pipeline_runs SET error = :error WHERE id = :id AND status = 'running'")
			.param("error", truncate(error))
			.param("id", runId)
			.update();
	}

	/**
	 * Runs {@code work} as a recorded step: {@code running} before, {@code succeeded} with its output
	 * count after, {@code failed} with the error when it throws (the exception is rethrown).
	 */
	public <T> T step(UUID runId, RunStep step, @Nullable Integer itemsIn, Supplier<T> work, ToIntFunction<T> itemsOut) {
		UUID stepId = jdbc
			.sql("INSERT INTO pipeline_run_steps (run_id, step, items_in) VALUES (:run, :step, :in) RETURNING id")
			.param("run", runId)
			.param("step", step.value())
			.param("in", itemsIn)
			.query(UUID.class)
			.single();
		T result;
		try {
			result = work.get();
		}
		catch (RuntimeException ex) {
			endStep(stepId, StepStatus.FAILED, null, String.valueOf(ex.getMessage()));
			throw ex;
		}
		endStep(stepId, StepStatus.SUCCEEDED, itemsOut.applyAsInt(result), null);
		return result;
	}

	/** The ids of {@code runIds} still queued or running, now {@code interrupted}. */
	public List<UUID> interrupt(List<UUID> runIds, String reason) {
		if (runIds.isEmpty()) {
			return List.of();
		}
		return jdbc
			.sql("UPDATE pipeline_runs SET status = 'interrupted', finished_at = now(), error = :reason"
					+ " WHERE id = ANY(:ids) AND status IN ('queued', 'running') RETURNING id")
			.param("reason", reason)
			.param("ids", runIds.toArray(UUID[]::new))
			.query(UUID.class)
			.list();
	}

	/** Every queued or running run, now {@code interrupted} (start-up recovery). */
	public List<UUID> interruptAll(String reason) {
		return jdbc
			.sql("UPDATE pipeline_runs SET status = 'interrupted', finished_at = now(), error = :reason"
					+ " WHERE status IN ('queued', 'running') RETURNING id")
			.param("reason", reason)
			.query(UUID.class)
			.list();
	}

	private void endStep(UUID stepId, StepStatus status, @Nullable Integer itemsOut, @Nullable String error) {
		jdbc.sql("UPDATE pipeline_run_steps SET status = :status, finished_at = now(), items_out = :out, error = :error"
				+ " WHERE id = :id")
			.param("status", status.value())
			.param("out", itemsOut)
			.param("error", error == null ? null : truncate(error))
			.param("id", stepId)
			.update();
	}

	private static String truncate(String text) {
		return text.length() > MAX_ERROR ? text.substring(0, MAX_ERROR) + "…" : text;
	}

	/** Additions to a run's counters. */
	public record Counters(int fetched, int newPosts, int assessedOk, int assessedFailed, int processingErrors,
			int dead, int skipped) {

		static Counters fetched(int fetched, int skipped) {
			return new Counters(fetched, 0, 0, 0, 0, 0, skipped);
		}

		static Counters stored(int newPosts) {
			return new Counters(0, newPosts, 0, 0, 0, 0, 0);
		}

		static Counters assessed(RunFlow.AssessedBatch batch) {
			return new Counters(0, 0, batch.ok(), batch.failed(), batch.processingErrors().size(), batch.dead(), 0);
		}

	}

}
