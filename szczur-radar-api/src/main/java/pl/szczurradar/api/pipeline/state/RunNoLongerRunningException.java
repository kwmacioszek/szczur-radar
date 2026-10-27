package pl.szczurradar.api.pipeline.state;

import java.util.UUID;

import pl.szczurradar.api.pipeline.run.RunStep;

/**
 * A step was not started because its run is no longer running: it was interrupted (a newer
 * deployment's recovery, a stopping instance) or ended elsewhere. Not a failure of the run — it ends
 * the run's sequence quietly, so a late worker never adds a step to a run that is over.
 */
public class RunNoLongerRunningException extends RuntimeException {

	public RunNoLongerRunningException(UUID runId, RunStep step) {
		super("run " + runId + " is no longer running; its " + step.value() + " step was not started");
	}

}
