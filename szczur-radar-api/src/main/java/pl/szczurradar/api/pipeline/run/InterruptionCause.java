package pl.szczurradar.api.pipeline.run;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Why a run was {@link RunStatus#INTERRUPTED interrupted}: the process stopped while it was queued or
 * running. {@link #value()} is the stored and API value, {@link #error()} the run's error text.
 */
public enum InterruptionCause {

	/** Another deployment took over: its start-up recovery found a run queued by an older deployment. */
	DEPLOY("deploy", "interrupted: a new deployment took over"),

	/**
	 * The application restarted: start-up recovery found a run of the same deployment, or of an unknown
	 * one (no {@code RAILWAY_DEPLOYMENT_ID}, e.g. locally).
	 */
	RESTART("restart", "interrupted: the application restarted"),

	/** The instance running it was stopping (its context closed). */
	SHUTDOWN("shutdown", "interrupted: the application stopped");

	private final String value;

	private final String error;

	InterruptionCause(String value, String error) {
		this.value = value;
		this.error = error;
	}

	@JsonValue
	public String value() {
		return value;
	}

	/** The run's error text for this cause. */
	public String error() {
		return error;
	}

}
