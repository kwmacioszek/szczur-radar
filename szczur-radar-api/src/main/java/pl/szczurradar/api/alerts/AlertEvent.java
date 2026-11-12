package pl.szczurradar.api.alerts;

/**
 * The events the API reports to the operator outside the application ({@link OperatorAlerts}). A
 * closed set: F-04's test signal, S-18's error paths that need a human, S-34's nightly summary and the quality
 * alarm of S-35 and S-36, which raise them through {@link OperatorAlertQueue}. Each has the ntfy priority of its message (S-34):
 * {@link #DEFAULT_PRIORITY} for a message that needs no reaction by itself, {@link #HIGH_PRIORITY} for one
 * that does.
 */
public enum AlertEvent {

	/** Sent by the operator command {@code send-test-alert} to check the channel end to end. */
	TEST_SIGNAL("test of the operator alert channel, nothing is wrong", AlertEvent.DEFAULT_PRIORITY),

	/**
	 * The night's summary ({@code pipeline/NightSummary}), raised by the cron's
	 * {@code POST /internal/pipeline-runs/nightly-summary} after its runs — every night, also with no dead
	 * post; the detail is the summary's text.
	 */
	NIGHT_SUMMARY("the nightly pipeline summary; nothing needs attention by itself", AlertEvent.DEFAULT_PRIORITY),

	/**
	 * A source's share of failed assessment attempts ({@code posts/AssessmentQuality#crossed}, S-35) or of theses
	 * in another language than their post's ({@code posts/AssessmentQuality#languageCrossed}, S-36) went above its
	 * threshold over the quality window tonight and was not above it the night before: raised by the same
	 * {@code POST /internal/pipeline-runs/nightly-summary} after {@link #NIGHT_SUMMARY}, once a night with every
	 * crossing of both measures in its detail, never while a source stays above; the detail names each source
	 * with its shares (and the failure classes).
	 */
	ASSESSMENT_QUALITY("the share of failed assessment attempts or of theses in another language than the post"
			+ " of a source crossed its threshold over the last 7 days; check the source's quality in /admin, the"
			+ " failed posts and the theses on the posts' pages", AlertEvent.HIGH_PRIORITY),

	/** A run ended in a state that notified the admins in the application (the bell). */
	RUN_NEEDS_ATTENTION("a pipeline run needs attention, the admins were notified in the app; check /admin",
			AlertEvent.HIGH_PRIORITY),

	/** The admins' in-app notifications about a run that needs attention could not be written. */
	NOTIFICATION_FAILED("the admins were not notified in the app about a run; check /admin and the API log",
			AlertEvent.HIGH_PRIORITY),

	/** Start-up recovery could not mark unfinished runs interrupted and put their posts back. */
	RECOVERY_FAILED("start-up recovery of unfinished pipeline runs failed, a run may stay running;"
			+ " check /admin and the API log", AlertEvent.HIGH_PRIORITY),

	/** A run could not be closed (or this instance's runs on shutdown), so it may stay running. */
	RUN_CLOSE_FAILED("a pipeline run could not be closed and may stay running without a notification;"
			+ " check /admin and the API log", AlertEvent.HIGH_PRIORITY),

	/** The watchdog could not check a run, so a stalled run may keep blocking the queue. */
	WATCHDOG_FAILED("the pipeline watchdog could not check a run, a stalled run may block the queue;"
			+ " check /admin and the API log", AlertEvent.HIGH_PRIORITY),

	/** Runs left running by an instance that is gone could not be cleaned up before a new run. */
	ORPHAN_CLEANUP_FAILED("runs left running by a lost instance could not be cleaned up, a new run may be"
			+ " refused; check /admin and the API log", AlertEvent.HIGH_PRIORITY),

	/** Post results did not reach a run's counters, so its status may hide dead posts or errors. */
	RUN_COUNTERS_LOST("post results did not reach a run's counters, its status may hide dead posts or errors;"
			+ " check /admin and the API log", AlertEvent.HIGH_PRIORITY);

	/** ntfy's default priority (3 of 1–5): a message that needs no reaction by itself. */
	public static final int DEFAULT_PRIORITY = 3;

	/** ntfy's high priority (4 of 1–5): a message that needs the operator. */
	public static final int HIGH_PRIORITY = 4;

	private final String description;

	private final int priority;

	AlertEvent(String description, int priority) {
		this.description = description;
		this.priority = priority;
	}

	/** A short human description, part of the message the operator reads. */
	public String description() {
		return description;
	}

	/** The ntfy priority of the message (1–5), its {@code Priority} header. */
	public int priority() {
		return priority;
	}

}
