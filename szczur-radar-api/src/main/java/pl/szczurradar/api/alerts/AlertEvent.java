package pl.szczurradar.api.alerts;

/**
 * The events the API reports to the operator outside the application ({@link OperatorAlerts}). A
 * closed set: F-04 has only the test signal; S-18 adds the error paths that need a human.
 */
public enum AlertEvent {

	/** Sent by the operator command {@code send-test-alert} to check the channel end to end. */
	TEST_SIGNAL("test of the operator alert channel, nothing is wrong");

	private final String description;

	AlertEvent(String description) {
		this.description = description;
	}

	/** A short human description, part of the message the operator reads. */
	public String description() {
		return description;
	}

}
