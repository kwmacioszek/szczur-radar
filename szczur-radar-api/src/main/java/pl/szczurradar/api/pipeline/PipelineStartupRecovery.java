package pl.szczurradar.api.pipeline;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import pl.szczurradar.api.pipeline.state.PostTransitions;
import pl.szczurradar.api.pipeline.state.RunRecorder;

/**
 * At start-up, runs that nobody finished (queued or running when a deploy or restart stopped their
 * instance) become {@code interrupted}, and every post still {@code assessing} without a queued or
 * running run is put back without an attempt — {@code stored} when it never failed, otherwise
 * {@code failed} — in one transaction. That includes a post claimed just as its run was interrupted
 * by a stopping instance. Without it the "one running run" index would block every later run. The
 * posts are taken by the next retry run.
 *
 * <p>Only in the server (a web application): operator commands such as the pre-deploy {@code migrate}
 * start the same context without a web server, and must neither interrupt the run an older instance is
 * still executing nor read tables before migrating them. A failure (e.g. the database not reachable
 * yet) is logged and does not stop the server, which starts without its database too; unfinished runs
 * then block new ones until the next start.
 */
@Component
@ConditionalOnWebApplication
public class PipelineStartupRecovery {

	private static final Logger log = LoggerFactory.getLogger(PipelineStartupRecovery.class);

	private final RunRecorder recorder;

	private final PostTransitions transitions;

	private final TransactionTemplate transaction;

	PipelineStartupRecovery(RunRecorder recorder, PostTransitions transitions, TransactionTemplate transaction) {
		this.recorder = recorder;
		this.transitions = transitions;
		this.transaction = transaction;
	}

	/** @return the runs marked interrupted */
	@EventListener(ApplicationReadyEvent.class)
	public List<UUID> recover() {
		List<UUID> interrupted;
		try {
			interrupted = transaction.execute(status -> {
				List<UUID> ids = recorder.interruptAll("interrupted: the application restarted");
				int released = transitions.releaseOrphans();
				if (released > 0) {
					log.warn("Put back {} post(s) left assessing by unfinished runs", released);
				}
				return ids;
			});
		}
		catch (RuntimeException ex) {
			log.error("Start-up recovery of pipeline runs failed; unfinished runs block new ones until the next start",
					ex);
			return List.of();
		}
		if (!interrupted.isEmpty()) {
			log.warn("Marked {} unfinished pipeline run(s) interrupted: {}", interrupted.size(), interrupted);
		}
		return interrupted;
	}

}
