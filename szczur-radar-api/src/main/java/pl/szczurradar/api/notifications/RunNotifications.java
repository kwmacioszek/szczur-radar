package pl.szczurradar.api.notifications;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import pl.szczurradar.api.pipeline.run.PipelineRun;
import pl.szczurradar.api.pipeline.run.PipelineRunRepository;

/**
 * Tells the admins about a pipeline run that needs attention ({@link PipelineRun#needsAttention()}): one
 * {@link NotificationKind#RUN_ATTENTION} notification for every admin who can log in. The pipeline calls
 * it on every way a run ends — the run's own end, the watchdog's stop, an instance closing and start-up
 * recovery — directly, not through Spring events (recovery runs before event listeners are known to be
 * registered). One run may be reported twice, so creating them is idempotent: a second report adds
 * nothing. An admin created after the report gets none.
 *
 * <p>It never throws: a failure is logged, and the run's ending (or the server's start) goes on.
 */
@Component
public class RunNotifications {

	private static final Logger log = LoggerFactory.getLogger(RunNotifications.class);

	private final PipelineRunRepository runs;

	private final NotificationRepository notifications;

	RunNotifications(PipelineRunRepository runs, NotificationRepository notifications) {
		this.runs = runs;
		this.notifications = notifications;
	}

	/**
	 * {@code runId} ended, or may have: when it needs attention, every admin who can log in and has no
	 * notification about it yet gets one. Nothing happens for a run that is unknown, still active or needs
	 * no attention — e.g. one that never started here.
	 */
	public void runEnded(UUID runId) {
		try {
			PipelineRun run = runs.findById(runId).orElse(null);
			if (run == null || !run.needsAttention()) {
				return;
			}
			int created = notifications.notifyAdmins(NotificationKind.RUN_ATTENTION, runId);
			if (created > 0) {
				log.info("Pipeline run {} ({}) ended {} and needs attention: {} admin(s) notified", runId,
						run.getKind().value(), run.getStatus().value(), created);
			}
		}
		catch (RuntimeException ex) {
			log.error("Pipeline run {}: the admins could not be notified", runId, ex);
		}
	}

}
