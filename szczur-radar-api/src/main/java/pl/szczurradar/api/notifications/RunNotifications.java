package pl.szczurradar.api.notifications;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import pl.szczurradar.api.alerts.AlertEvent;
import pl.szczurradar.api.alerts.OperatorAlertQueue;
import pl.szczurradar.api.pipeline.run.PipelineRun;
import pl.szczurradar.api.pipeline.run.PipelineRunRepository;
import pl.szczurradar.api.pipeline.run.RunStatus;

/**
 * Tells the admins about a pipeline run that needs attention ({@link PipelineRun#needsAttention()}): one
 * {@link NotificationKind#RUN_ATTENTION} notification for every admin who can log in. The pipeline calls
 * it on every way a run ends — the run's own end, the watchdog's stop, an instance closing and start-up
 * recovery — directly, not through Spring events (recovery runs before event listeners are known to be
 * registered). One run may be reported twice, so creating them is idempotent: a second report adds
 * nothing. An admin created after the report gets none.
 *
 * <p>The operator hears of it outside the application too ({@link AlertEvent#RUN_NEEDS_ATTENTION}), once:
 * only when notifications were created. Not for a run of the daily cron ({@link PipelineRun#scheduled()})
 * that ended {@code failed} or {@code interrupted}: the cron reports that one by its exit code. A failure
 * to notify is {@link AlertEvent#NOTIFICATION_FAILED}. Both go through the {@link OperatorAlertQueue},
 * only in the server: an operator command's context has none, and then nothing is raised.
 *
 * <p>It never throws: a failure is logged, and the run's ending (or the server's start) goes on. A
 * failure on an interrupted thread (the watchdog's late interrupt) is tried once more first.
 */
@Component
public class RunNotifications {

	/** The most characters of a run's error in an alert. */
	static final int MAX_ERROR = 300;

	private static final Logger log = LoggerFactory.getLogger(RunNotifications.class);

	private final PipelineRunRepository runs;

	private final NotificationRepository notifications;

	/** The server's queue ({@code AlertsConfiguration}), resolved when used; none in an operator command. */
	private final ObjectProvider<OperatorAlertQueue> alerts;

	RunNotifications(PipelineRunRepository runs, NotificationRepository notifications,
			ObjectProvider<OperatorAlertQueue> alerts) {
		this.runs = runs;
		this.notifications = notifications;
		this.alerts = alerts;
	}

	/**
	 * {@code runId} ended, or may have: when it needs attention, every admin who can log in and has no
	 * notification about it yet gets one. Nothing happens for a run that is unknown, still active or needs
	 * no attention — e.g. one that never started here.
	 *
	 * <p>A failure on an interrupted thread is tried once more with the flag cleared: the watchdog's late
	 * {@code cancel(true)} of a run's thread may land in this work, and the connection pool refuses an
	 * interrupted thread — that would be a false {@link AlertEvent#NOTIFICATION_FAILED} for a run the
	 * watchdog stopped. {@code cancel(true)} interrupts once, so the second attempt gets its connection;
	 * only its failure counts. Doing it twice is safe: creating the notifications is idempotent, and the
	 * operator's alert follows only notifications created. The flag is restored after it, as it was when
	 * the work failed: the caller's rule decides it.
	 */
	public void runEnded(UUID runId) {
		RuntimeException failure;
		try {
			notifyAdmins(runId);
			return;
		}
		catch (RuntimeException ex) {
			failure = ex;
		}
		boolean interrupted = Thread.interrupted();
		try {
			if (interrupted) {
				try {
					notifyAdmins(runId);
					return;
				}
				catch (RuntimeException again) {
					again.addSuppressed(failure);
					failure = again;
				}
			}
			log.error("Pipeline run {}: the admins could not be notified", runId, failure);
			// Only the class: a database exception's message may carry SQL and values.
			OperatorAlertQueue.raise(alerts, AlertEvent.NOTIFICATION_FAILED, runId, "Pipeline run " + runId
					+ ": the admins could not be notified (" + failure.getClass().getName() + "); details in the API log");
		}
		finally {
			if (interrupted) {
				Thread.currentThread().interrupt();
			}
		}
	}

	/** One attempt of {@link #runEnded}: its exception is the caller's to handle. */
	private void notifyAdmins(UUID runId) {
		PipelineRun run = runs.findById(runId).orElse(null);
		if (run == null || !run.needsAttention()) {
			return;
		}
		int created = notifications.notifyAdmins(NotificationKind.RUN_ATTENTION, runId);
		if (created > 0) {
			log.info("Pipeline run {} ({}) ended {} and needs attention: {} admin(s) notified", runId,
					run.getKind().value(), run.getStatus().value(), created);
			if (!reportedByTheCron(run)) {
				OperatorAlertQueue.raise(alerts, AlertEvent.RUN_NEEDS_ATTENTION, runId, attentionDetail(run));
			}
		}
	}

	/** A run of the daily cron that ended {@code failed} or {@code interrupted}: the cron's exit code reports it. */
	private static boolean reportedByTheCron(PipelineRun run) {
		return run.scheduled() && (run.getStatus() == RunStatus.FAILED || run.getStatus() == RunStatus.INTERRUPTED);
	}

	/**
	 * {@code Pipeline run <id> (<kind>, <cron|admin>) ended <status>[ (<cause>)]: dead …, errors …,
	 * skipped …[; error: <the run's error, cut>]; /admin/runs/<id>} — the run's error is the text
	 * {@code /admin} shows (the LLM clients' messages in it are already cleaned of the key).
	 */
	static String attentionDetail(PipelineRun run) {
		StringBuilder detail = new StringBuilder("Pipeline run ").append(run.getId())
			.append(" (")
			.append(run.getKind().value())
			.append(", ")
			.append(run.scheduled() ? "cron" : "admin")
			.append(") ended ")
			.append(run.getStatus().value());
		if (run.getInterruptionCause() != null) {
			detail.append(" (").append(run.getInterruptionCause().value()).append(')');
		}
		detail.append(": dead ")
			.append(run.getDead())
			.append(", errors ")
			.append(run.getProcessingErrors())
			.append(", skipped ")
			.append(run.getSkipped());
		String error = run.getError();
		if (error != null && !error.isBlank()) {
			detail.append("; error: ").append(cut(error.strip()));
		}
		return detail.append("; /admin/runs/").append(run.getId()).toString();
	}

	private static String cut(String text) {
		return text.length() <= MAX_ERROR ? text : text.substring(0, MAX_ERROR - 1) + "…";
	}

}
