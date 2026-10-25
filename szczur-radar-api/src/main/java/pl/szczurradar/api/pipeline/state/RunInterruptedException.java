package pl.szczurradar.api.pipeline.state;

import java.util.UUID;

import pl.szczurradar.api.pipeline.run.RunStep;

/**
 * The run's thread was interrupted inside a step — the watchdog stopped the run
 * ({@code PipelineRunService.stopStalled}) or the instance is stopping ({@code onClose}) — so the step
 * does not start its next unit of work (a chunk of posts to store, a source to fetch). Whoever stopped
 * the run already wrote its end; {@code PipelineRunService.execute} logs this at INFO, not as a failure.
 */
public class RunInterruptedException extends RuntimeException {

	public RunInterruptedException(UUID runId, RunStep step) {
		super("the run's thread was interrupted during " + step.value() + " (run " + runId + ")");
	}

	/**
	 * Throws when the current thread is interrupted, without clearing its interrupt flag: a step calls it
	 * before each unit of work, so a stopped run does no more than the unit it was in.
	 *
	 * @throws RunInterruptedException when the current thread is interrupted
	 */
	public static void checkpoint(UUID runId, RunStep step) {
		if (Thread.currentThread().isInterrupted()) {
			throw new RunInterruptedException(runId, step);
		}
	}

}
