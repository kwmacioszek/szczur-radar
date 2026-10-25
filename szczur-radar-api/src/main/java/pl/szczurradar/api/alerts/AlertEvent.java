package pl.szczurradar.api.alerts;

/**
 * The events the API reports to the operator outside the application ({@link OperatorAlerts}). A
 * closed set: F-04's test signal and S-18's error paths that need a human, which raise them through
 * {@link OperatorAlertQueue}.
 */
public enum AlertEvent {

	/** Sent by the operator command {@code send-test-alert} to check the channel end to end. */
	TEST_SIGNAL("test of the operator alert channel, nothing is wrong"),

	/** A run ended in a state that notified the admins in the application (the bell). */
	RUN_NEEDS_ATTENTION("a pipeline run needs attention, the admins were notified in the app; check /admin"),

	/** The admins' in-app notifications about a run that needs attention could not be written. */
	NOTIFICATION_FAILED("the admins were not notified in the app about a run; check /admin and the API log"),

	/** Start-up recovery could not mark unfinished runs interrupted and put their posts back. */
	RECOVERY_FAILED("start-up recovery of unfinished pipeline runs failed, a run may stay running;"
			+ " check /admin and the API log"),

	/** A run could not be closed (or this instance's runs on shutdown), so it may stay running. */
	RUN_CLOSE_FAILED("a pipeline run could not be closed and may stay running without a notification;"
			+ " check /admin and the API log"),

	/** The watchdog could not check a run, so a stalled run may keep blocking the queue. */
	WATCHDOG_FAILED("the pipeline watchdog could not check a run, a stalled run may block the queue;"
			+ " check /admin and the API log"),

	/** Runs left running by an instance that is gone could not be cleaned up before a new run. */
	ORPHAN_CLEANUP_FAILED("runs left running by a lost instance could not be cleaned up, a new run may be"
			+ " refused; check /admin and the API log"),

	/** Post results did not reach a run's counters, so its status may hide dead posts or errors. */
	RUN_COUNTERS_LOST("post results did not reach a run's counters, its status may hide dead posts or errors;"
			+ " check /admin and the API log");

	private final String description;

	AlertEvent(String description) {
		this.description = description;
	}

	/** A short human description, part of the message the operator reads. */
	public String description() {
		return description;
	}

}
