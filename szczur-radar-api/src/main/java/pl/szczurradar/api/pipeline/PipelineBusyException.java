package pl.szczurradar.api.pipeline;

/** A run is already queued or running; a new request is refused. */
public class PipelineBusyException extends RuntimeException {

	public PipelineBusyException() {
		super("a pipeline run is already queued or running");
	}

}
