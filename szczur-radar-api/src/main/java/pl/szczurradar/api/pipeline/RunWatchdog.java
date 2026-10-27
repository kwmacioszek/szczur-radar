package pl.szczurradar.api.pipeline;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Component;

import pl.szczurradar.api.alerts.AlertEvent;
import pl.szczurradar.api.alerts.OperatorAlertQueue;
import pl.szczurradar.api.pipeline.state.Durations;

/**
 * Stops a run of this instance that makes no progress, so it cannot block the queue (once a run
 * "lasted" about 22 h and every later start was refused). Every {@link #checkInterval} — a fifth of
 * {@code PIPELINE_STALL_TIMEOUT}, at most a minute — it hands each run this instance queued and has
 * not finished to {@link PipelineRunService#stopStalled}, which ends a running one without activity
 * for that long as {@code failed}.
 *
 * <ul>
 *   <li>Only this instance's runs: another instance's (a deployment overlap) are start-up recovery's,
 *       or, once none of the active runs shows activity for the same timeout, the next request to start
 *       a run's ({@link PipelineRunService}, cause {@code lost}).
 *   <li>No database query while this instance has no run (an in-memory check): Railway counts every
 *       outgoing packet, a query to Postgres too, as activity, so a query every minute would keep the
 *       API from sleeping and bring the plan's hard spending limit closer.
 *   <li>A failed check is logged, whatever was thrown, and raised to the operator
 *       ({@link AlertEvent#WATCHDOG_FAILED}, with the run, so at most once in the queue's repeat window
 *       for that run); the next one runs as planned. A failure of that raise is logged too and stops
 *       nothing: the other runs are still checked.
 *   <li>Only in the server, like {@link PipelineStartupRecovery}: the operator commands start the same
 *       context, and only the server reads {@code PIPELINE_STALL_TIMEOUT} (the server's one
 *       {@link PipelineStallSettings}, {@link PipelineStallConfiguration}, validated when it starts).
 * </ul>
 */
@Component
@ConditionalOnWebApplication
public class RunWatchdog implements InitializingBean, DisposableBean {

	/** The longest time between two checks. */
	static final Duration MAX_CHECK_INTERVAL = Duration.ofMinutes(1);

	/** The shortest time between two checks, whatever the timeout. */
	static final Duration MIN_CHECK_INTERVAL = Duration.ofMillis(100);

	private static final Logger log = LoggerFactory.getLogger(RunWatchdog.class);

	private final PipelineRunService service;

	private final PipelineStallSettings settings;

	private final OperatorAlertQueue alerts;

	private final ScheduledExecutorService executor = Executors
		.newSingleThreadScheduledExecutor(task -> Thread.ofPlatform().name("pipeline-watchdog").unstarted(task));

	RunWatchdog(PipelineRunService service, PipelineStallSettings settings, OperatorAlertQueue alerts) {
		this.service = service;
		this.settings = settings;
		this.alerts = alerts;
	}

	/** How often {@code timeout} is checked: a fifth of it, at most {@link #MAX_CHECK_INTERVAL}. */
	static Duration checkInterval(Duration timeout) {
		Duration interval = timeout.dividedBy(5);
		if (interval.compareTo(MAX_CHECK_INTERVAL) > 0) {
			return MAX_CHECK_INTERVAL;
		}
		return interval.compareTo(MIN_CHECK_INTERVAL) < 0 ? MIN_CHECK_INTERVAL : interval;
	}

	/** Starts the checks. */
	@Override
	public void afterPropertiesSet() {
		long interval = checkInterval(settings.timeout()).toNanos();
		executor.scheduleWithFixedDelay(this::check, interval, interval, TimeUnit.NANOSECONDS);
		log.info("Pipeline watchdog: a run without progress for {} is stopped (checked every {})",
				Durations.format(settings.timeout()), Durations.format(checkInterval(settings.timeout())));
	}

	/**
	 * One check of this instance's runs; package-private for tests. Any failure, an {@link Error} too,
	 * is logged here, and so is a failure to raise it: one escaping to {@code scheduleWithFixedDelay}
	 * would cancel every later check without a word.
	 */
	void check() {
		for (UUID runId : service.ownRuns()) {
			try {
				service.stopStalled(runId, settings.timeout());
			}
			catch (Throwable ex) {
				log.error("The pipeline watchdog could not check run {}; it checks again in {}", runId,
						Durations.format(checkInterval(settings.timeout())), ex);
				try {
					alerts.raise(AlertEvent.WATCHDOG_FAILED, runId, "The pipeline watchdog could not check run " + runId
							+ " (" + ex.getClass().getName() + "); it checks again in "
							+ Durations.format(checkInterval(settings.timeout()))
							+ ", a stalled run may block the queue meanwhile; /admin/runs/" + runId
							+ "; details in the API log");
				}
				catch (Throwable raiseFailure) {
					log.error("The pipeline watchdog could not raise WATCHDOG_FAILED for run {}", runId, raiseFailure);
				}
			}
		}
	}

	/** Stops the checks when the context closes. */
	@Override
	public void destroy() {
		executor.shutdownNow();
	}

}
