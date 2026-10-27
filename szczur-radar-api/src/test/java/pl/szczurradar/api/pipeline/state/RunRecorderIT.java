package pl.szczurradar.api.pipeline.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import pl.szczurradar.api.TestcontainersConfiguration;
import pl.szczurradar.api.cli.MigrateCommand;
import pl.szczurradar.api.pipeline.run.InterruptionCause;
import pl.szczurradar.api.pipeline.run.RunStatus;
import pl.szczurradar.api.pipeline.run.RunStep;
import pl.szczurradar.api.pipeline.run.StepStatus;
import pl.szczurradar.api.pipeline.state.RunRecorder.Counters;
import pl.szczurradar.api.pipeline.state.RunRecorder.StepOutcome;

/**
 * The conditional writes of {@link RunRecorder} on Postgres, each guard on its own: which status a write
 * needs, what it leaves alone, and how steps are opened and closed. Runs are rows inserted with SQL. Each
 * test rolls back; within it the database's {@code now()} is the transaction's start, so an age is set
 * with an interval. The runs other tests of this context committed are made finished a day ago first
 * (rolled back too): the statements over every queued or running run must see only this test's.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = MigrateCommand.ENABLE_FLYWAY)
@Import(TestcontainersConfiguration.class)
@Transactional
class RunRecorderIT {

	@Autowired
	RunRecorder recorder;

	@Autowired
	JdbcTemplate jdbc;

	private UUID adminId;

	@BeforeEach
	void isolate() {
		jdbc.update("UPDATE pipeline_runs SET status = CASE WHEN status IN ('queued', 'running') THEN 'failed'"
				+ " ELSE status END, finished_at = now() - interval '1 day'");
		// With SQL, as the runs: a saved entity would reach the table only at the next flush.
		adminId = jdbc.queryForObject("INSERT INTO users (email, role) VALUES (?, 'admin') RETURNING id", UUID.class,
				"recorder-" + UUID.randomUUID() + "@example.com");
	}

	@Test
	void aRunStartsOnlyFromQueued() {
		UUID queued = run("queued", null, null);
		UUID failed = run("failed", null, null);

		assertThat(recorder.start(queued)).isTrue();
		assertThat(row(queued)).containsEntry("status", "running");
		assertThat(row(queued).get("started_at")).isNotNull();
		assertThat(row(queued).get("last_activity_at")).isNotNull();
		assertThat(recorder.isRunning(queued)).isTrue();

		// Running already, or over: no second start.
		assertThat(recorder.start(queued)).isFalse();
		assertThat(recorder.start(failed)).isFalse();
		assertThat(row(failed)).containsEntry("status", "failed").containsEntry("last_activity_at", null);
		assertThat(recorder.isRunning(failed)).isFalse();
	}

	@Test
	void finishCountTouchAndErrorChangeOnlyARunningRun() {
		UUID queued = run("queued", null, null);
		UUID running = run("running", null, "1 hour");

		assertThat(recorder.finish(queued, RunStatus.SUCCEEDED, "x")).isFalse();
		recorder.count(queued, Counters.fetched(3, 1));
		recorder.touch(queued);
		recorder.error(queued, "not yours");
		assertThat(row(queued)).containsEntry("status", "queued")
			.containsEntry("fetched", 0)
			.containsEntry("skipped", 0)
			.containsEntry("last_activity_at", null)
			.containsEntry("error", null);

		Object before = row(running).get("last_activity_at");
		recorder.touch(running);
		assertThat(row(running).get("last_activity_at")).isNotEqualTo(before);
		recorder.count(running, Counters.fetched(3, 1));
		recorder.count(running, Counters.assessedOk(2, true));
		recorder.error(running, "1 post with a technical error");
		assertThat(row(running)).containsEntry("fetched", 3)
			.containsEntry("skipped", 1)
			.containsEntry("assessed_ok", 1)
			.containsEntry("llm_retries", 2)
			.containsEntry("flex_fallbacks", 1)
			.containsEntry("error", "1 post with a technical error");

		// Without an error of its own, the finish keeps the one set while running.
		assertThat(recorder.finish(running, RunStatus.PARTIAL, null)).isTrue();
		assertThat(row(running)).containsEntry("status", "partial").containsEntry("error", "1 post with a technical error");
		assertThat(row(running).get("finished_at")).isNotNull();

		// Over: nothing changes it any more.
		assertThat(recorder.finish(running, RunStatus.FAILED, "late")).isFalse();
		recorder.count(running, Counters.fetched(5, 0));
		recorder.error(running, "late");
		assertThat(row(running)).containsEntry("status", "partial")
			.containsEntry("fetched", 3)
			.containsEntry("error", "1 post with a technical error");
	}

	@Test
	void failEndsAQueuedOrRunningRunButNotAFinishedOneAndClosesItsOpenStep() {
		UUID queued = run("queued", null, null);
		UUID running = run("running", null, null);
		// An Error escaping the step: step() did not close it.
		closedStep(running, "select");
		openStep(running, "assess");
		UUID succeeded = run("succeeded", null, null);
		openStep(succeeded, "summarize");

		recorder.fail(queued, "the run ended without a summary");
		recorder.fail(running, "OutOfMemoryError: Java heap space");
		recorder.fail(succeeded, "too late");

		assertThat(row(queued)).containsEntry("status", "failed").containsEntry("error", "the run ended without a summary");
		assertThat(row(queued).get("finished_at")).isNotNull();
		assertThat(row(running)).containsEntry("status", "failed").containsEntry("error", "OutOfMemoryError: Java heap space");
		assertThat(steps(running)).containsExactly("select:succeeded::::true",
				"assess:failed:::OutOfMemoryError: Java heap space:true");
		assertThat(row(succeeded)).containsEntry("status", "succeeded").containsEntry("error", null);
		// Not this run's end: its step is left alone too.
		assertThat(steps(succeeded)).containsExactly("summarize:running::::false");
	}

	@Test
	void aStepOfARunThatIsNotRunningIsRefusedBeforeItsWorkAndAddsNoRow() {
		UUID interrupted = run("interrupted", null, null);
		AtomicBoolean worked = new AtomicBoolean();

		assertThatThrownBy(() -> recorder.step(interrupted, RunStep.ASSESS, 1, () -> {
			worked.set(true);
			return 1;
		}, Integer::intValue)).isInstanceOf(RunNoLongerRunningException.class).hasMessageContaining("assess");

		assertThat(worked).isFalse();
		assertThat(steps(interrupted)).isEmpty();
	}

	@Test
	void aStepRecordsItsEndAsItsWorkDecides() {
		UUID running = run("running", null, null);

		assertThat(recorder.step(running, RunStep.FETCH, null, () -> 16, Integer::intValue)).isEqualTo(16);
		// A dispatcher that stopped returns a result, and the step is still failed with its reason (A6).
		assertThat(recorder.step(running, RunStep.ASSESS, 4, () -> "stopped",
				(String result) -> new StepOutcome(StepStatus.FAILED, 3, "the LLM provider is unavailable: 3 calls in a row")))
			.isEqualTo("stopped");
		assertThatThrownBy(() -> recorder.step(running, RunStep.SUMMARIZE, null, () -> {
			throw new IllegalStateException("a bug");
		}, (Integer result) -> new StepOutcome(StepStatus.SUCCEEDED, null, null))).hasMessage("a bug");

		// One transaction: the three steps started at the same now().
		assertThat(steps(running)).containsExactlyInAnyOrder("fetch:succeeded::16::true",
				"assess:failed:4:3:the LLM provider is unavailable: 3 calls in a row:true", "summarize:failed:::a bug:true");
		// A failed step is the step's, not the run's end.
		assertThat(row(running)).containsEntry("status", "running");
	}

	@Test
	void aStepAnInterruptionClosedStaysAsTheInterruptionLeftIt() {
		UUID running = run("running", null, null);

		recorder.step(running, RunStep.ASSESS, 2, () -> {
			// A newer deployment's recovery (or a stopping instance) ends the run while the step works.
			assertThat(recorder.interrupt(List.of(running), InterruptionCause.SHUTDOWN)).containsExactly(running);
			return 2;
		}, Integer::intValue);

		assertThat(steps(running)).containsExactly("assess:interrupted:2:::true");
		assertThat(row(running)).containsEntry("status", "interrupted").containsEntry("interruption_cause", "shutdown");
	}

	@Test
	void stallStopsOnlyARunWithoutProgressForTheThresholdAndFailsItsOpenStep() {
		UUID fresh = run("running", null, "10 seconds");
		openStep(fresh, "assess");

		assertThat(recorder.stall(fresh, Duration.ofMinutes(1))).isFalse();
		assertThat(row(fresh)).containsEntry("status", "running");
		assertThat(steps(fresh)).containsExactly("assess:running::::false");

		jdbc.update("UPDATE pipeline_runs SET status = 'succeeded', finished_at = now() WHERE id = ?", fresh);
		UUID stalled = run("running", null, "1 hour");
		closedStep(stalled, "select");
		openStep(stalled, "assess");
		UUID queued = run("queued", null, null);
		jdbc.update("UPDATE pipeline_runs SET created_at = now() - interval '1 hour' WHERE id = ?", queued);

		assertThat(recorder.stall(stalled, Duration.ofMinutes(1))).isTrue();
		// Not running: nothing to stop, however old.
		assertThat(recorder.stall(queued, Duration.ofMinutes(1))).isFalse();

		String error = "no progress for " + Durations.format(Duration.ofMinutes(1)) + ": the run was stopped";
		assertThat(row(stalled)).containsEntry("status", "failed").containsEntry("error", error);
		assertThat(steps(stalled)).containsExactly("select:succeeded::::true", "assess:failed:::" + error + ":true");
		assertThat(row(queued)).containsEntry("status", "queued");
		// Over already: a second stall changes nothing.
		assertThat(recorder.stall(stalled, Duration.ofMinutes(1))).isFalse();
	}

	@Test
	void interruptEndsTheQueuedAndRunningRunsWithTheCauseAndClosesTheirOpenSteps() {
		UUID running = run("running", null, null);
		closedStep(running, "select");
		openStep(running, "assess");
		UUID queued = run("queued", null, null);
		UUID succeeded = run("succeeded", null, null);

		assertThat(recorder.interrupt(List.of(running, queued, succeeded), InterruptionCause.SHUTDOWN))
			.containsExactlyInAnyOrder(running, queued);

		for (UUID run : List.of(running, queued)) {
			assertThat(row(run)).containsEntry("status", "interrupted")
				.containsEntry("interruption_cause", "shutdown")
				.containsEntry("error", InterruptionCause.SHUTDOWN.error());
			assertThat(row(run).get("finished_at")).isNotNull();
		}
		assertThat(steps(running)).containsExactly("select:succeeded::::true", "assess:interrupted::::true");
		assertThat(row(succeeded)).containsEntry("status", "succeeded").containsEntry("interruption_cause", null);
		assertThat(recorder.interrupt(List.of(), InterruptionCause.SHUTDOWN)).isEmpty();
	}

	@Test
	void interruptAllNamesADeployByTheRunsDeploymentAndAnythingElseARestart() {
		UUID ofAnotherDeployment = run("running", "deployment-a", null);
		openStep(ofAnotherDeployment, "assess");
		UUID ofThisDeployment = run("queued", "deployment-b", null);
		UUID withoutDeployment = run("queued", null, null);
		UUID finished = run("succeeded", "deployment-a", null);

		assertThat(recorder.interruptAll("deployment-b")).containsExactlyInAnyOrder(ofAnotherDeployment,
				ofThisDeployment, withoutDeployment);

		assertThat(row(ofAnotherDeployment)).containsEntry("status", "interrupted")
			.containsEntry("interruption_cause", "deploy")
			.containsEntry("error", InterruptionCause.DEPLOY.error());
		assertThat(steps(ofAnotherDeployment)).containsExactly("assess:interrupted::::true");
		assertThat(row(ofThisDeployment)).containsEntry("interruption_cause", "restart")
			.containsEntry("error", InterruptionCause.RESTART.error());
		assertThat(row(withoutDeployment)).containsEntry("interruption_cause", "restart");
		assertThat(row(finished)).containsEntry("status", "succeeded").containsEntry("interruption_cause", null);

		// This instance's deployment unknown: every run a restart.
		UUID another = run("queued", "deployment-a", null);
		assertThat(recorder.interruptAll(null)).containsExactly(another);
		assertThat(row(another)).containsEntry("interruption_cause", "restart");
	}

	@Test
	void interruptOrphansEndsEveryActiveRunWhenNoneShowedActivity() {
		UUID dead = run("running", "deployment-other", "1 hour");
		openStep(dead, "assess");
		UUID behind = run("queued", "deployment-other", null);
		jdbc.update("UPDATE pipeline_runs SET created_at = now() - interval '1 hour' WHERE id IN (?, ?)", dead, behind);

		assertThat(recorder.interruptOrphans(Duration.ofMinutes(10))).containsExactlyInAnyOrder(dead, behind);

		for (UUID run : List.of(dead, behind)) {
			assertThat(row(run)).containsEntry("status", "interrupted")
				.containsEntry("interruption_cause", "lost")
				.containsEntry("error", InterruptionCause.LOST.error());
		}
		assertThat(steps(dead)).containsExactly("assess:interrupted::::true");
	}

	@Test
	void interruptOrphansEndsNoRunWhileOneShowedActivity() {
		UUID dead = run("running", "deployment-other", "1 hour");
		openStep(dead, "assess");
		jdbc.update("UPDATE pipeline_runs SET created_at = now() - interval '1 hour' WHERE id = ?", dead);
		// Queued just now: a living queue.
		UUID fresh = run("queued", "deployment-other", null);

		assertThat(recorder.interruptOrphans(Duration.ofMinutes(10))).isEmpty();

		assertThat(row(dead)).containsEntry("status", "running").containsEntry("interruption_cause", null);
		assertThat(row(fresh)).containsEntry("status", "queued");
		assertThat(steps(dead)).containsExactly("assess:running::::false");
	}

	/**
	 * A retry run in {@code status} of {@code deploymentId}; a running one started, and active
	 * {@code activityAgo} ago when given (an SQL interval), otherwise without activity yet.
	 */
	private UUID run(String status, @Nullable String deploymentId, @Nullable String activityAgo) {
		boolean started = !status.equals("queued");
		boolean finished = !status.equals("queued") && !status.equals("running");
		return jdbc.queryForObject("INSERT INTO pipeline_runs (kind, status, requested_by, deployment_id, started_at,"
				+ " finished_at, last_activity_at) VALUES ('retry', ?, ?, ?, " + (started ? "now()" : "NULL") + ", "
				+ (finished ? "now()" : "NULL") + ", "
				+ (activityAgo == null ? "NULL" : "now() - CAST(? AS INTERVAL)") + ") RETURNING id", UUID.class,
				activityAgo == null ? new Object[] { status, adminId, deploymentId }
						: new Object[] { status, adminId, deploymentId, activityAgo });
	}

	private void openStep(UUID runId, String step) {
		jdbc.update("INSERT INTO pipeline_run_steps (run_id, step) VALUES (?, ?)", runId, step);
	}

	private void closedStep(UUID runId, String step) {
		jdbc.update("INSERT INTO pipeline_run_steps (run_id, step, status, started_at, finished_at) VALUES (?, ?,"
				+ " 'succeeded', now() - interval '1 minute', now() - interval '30 seconds')", runId, step);
	}

	private Map<String, Object> row(UUID runId) {
		return jdbc.queryForMap("SELECT * FROM pipeline_runs WHERE id = ?", runId);
	}

	/** The run's steps as {@code step:status:in:out:error:closed}, in the order they started. */
	private List<String> steps(UUID runId) {
		return jdbc.queryForList("SELECT step || ':' || status || ':' || COALESCE(CAST(items_in AS TEXT), '') || ':'"
				+ " || COALESCE(CAST(items_out AS TEXT), '') || ':' || COALESCE(error, '') || ':'"
				+ " || (finished_at IS NOT NULL) FROM pipeline_run_steps WHERE run_id = ? ORDER BY started_at, id",
				String.class, runId);
	}

}
