package pl.szczurradar.api.pipeline;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import com.embabel.agent.api.invocation.AgentInvocation;
import com.embabel.agent.core.AgentPlatform;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import pl.szczurradar.api.notifications.RunNotifications;
import pl.szczurradar.api.pipeline.assess.AssessmentDispatcher;
import pl.szczurradar.api.pipeline.flow.RunFlow.RunRequest;
import pl.szczurradar.api.pipeline.flow.RunFlow.RunSummary;
import pl.szczurradar.api.pipeline.run.InterruptionCause;
import pl.szczurradar.api.pipeline.run.PipelineRun;
import pl.szczurradar.api.pipeline.run.PipelineRunRepository;
import pl.szczurradar.api.pipeline.run.RunKind;
import pl.szczurradar.api.pipeline.state.Durations;
import pl.szczurradar.api.pipeline.state.PostTransitions;
import pl.szczurradar.api.pipeline.state.RunNoLongerRunningException;
import pl.szczurradar.api.pipeline.state.RunRecorder;
import pl.szczurradar.api.pipeline.step.SourceIngest;
import pl.szczurradar.api.posts.PostRepository;
import pl.szczurradar.api.posts.PostStage;
import pl.szczurradar.api.sources.Source;

/**
 * Creates pipeline runs and executes them one at a time, in order, on one background thread.
 *
 * <ul>
 *   <li>A range request creates up to two queued runs in one transaction: a {@link RunKind#RETRY}
 *       run when there are leftovers, then the {@link RunKind#RANGE} run. A request while a run is
 *       queued or running is refused ({@link PipelineBusyException}); the check and the insert hold
 *       a PostgreSQL advisory lock, so two concurrent requests never create two sets of runs.
 *   <li>Every run remembers the deployment that queued it ({@code RAILWAY_DEPLOYMENT_ID}, none when
 *       unset), so a newer deployment's recovery can tell that it took over.
 *   <li>A run starts only from {@code queued} (a conditional update); it ends with the agent's
 *       summary, as {@code failed} on any exception (also one before it started, so a run never
 *       stays queued or running without a thread and blocks the queue), and in every case its posts
 *       still {@code assessing} are put back. A run that stopped elsewhere meanwhile (interrupted)
 *       ends quietly: its next step is not started ({@link RunNoLongerRunningException}).
 *   <li>A run of this instance without progress for {@code PIPELINE_STALL_TIMEOUT} is stopped by
 *       {@link RunWatchdog} ({@link #stopStalled}): {@code failed}, its posts put back, its post tasks
 *       interrupted, and the queue goes on with the next run.
 *   <li>When the context closes, this instance marks its own queued and running runs
 *       {@code interrupted} ({@link InterruptionCause#SHUTDOWN}) first, then stops its thread; a newer
 *       deployment's start-up recovery does the same for runs nobody finished
 *       ({@link PipelineStartupRecovery}).
 *   <li>Every way a run ends here — its own end, the watchdog's stop, the instance closing — reports it
 *       to {@link RunNotifications#runEnded}, which notifies the admins when it needs attention (a run
 *       reported twice is notified once).
 *   <li>The log gets a line when a run starts and when it ends, with its counters.
 * </ul>
 */
@Service
public class PipelineRunService {

	/** The calendar of a range request: Warsaw, the users' clock. */
	public static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");

	/** Stable advisory lock key for creating runs ("SR_PIPEL"). */
	static final long LOCK_ID = 0x53525F504950454CL;

	private static final Logger log = LoggerFactory.getLogger(PipelineRunService.class);

	private final PipelineRunRepository runs;

	private final PostRepository posts;

	private final SourceIngest sources;

	private final RunRecorder recorder;

	private final PostTransitions transitions;

	private final RunNotifications notifications;

	private final AssessmentDispatcher dispatcher;

	private final AgentPlatform agentPlatform;

	private final JdbcClient jdbc;

	private final TransactionTemplate transaction;

	/** This instance's deployment, {@code null} when unknown (locally, in tests). */
	private final @Nullable String deploymentId;

	private final ExecutorService executor = Executors
		.newSingleThreadExecutor(task -> Thread.ofPlatform().name("pipeline-run").unstarted(task));

	/** The runs this instance queued and has not finished. */
	private final Set<UUID> own = ConcurrentHashMap.newKeySet();

	/** The agent's future of the run this instance is waiting for; {@link #stopStalled} cancels it. */
	private final Map<UUID, Future<?>> waiting = new ConcurrentHashMap<>();

	PipelineRunService(PipelineRunRepository runs, PostRepository posts, SourceIngest sources, RunRecorder recorder,
			PostTransitions transitions, RunNotifications notifications, AssessmentDispatcher dispatcher,
			AgentPlatform agentPlatform, JdbcClient jdbc, TransactionTemplate transaction,
			@Value("${RAILWAY_DEPLOYMENT_ID:}") @Nullable String deploymentId) {
		this.runs = runs;
		this.posts = posts;
		this.sources = sources;
		this.recorder = recorder;
		this.transitions = transitions;
		this.notifications = notifications;
		this.dispatcher = dispatcher;
		this.agentPlatform = agentPlatform;
		this.jdbc = jdbc;
		this.transaction = transaction;
		this.deploymentId = deploymentId == null || deploymentId.isBlank() ? null : deploymentId;
	}

	/**
	 * Queues a retry run (when there are leftovers) and a range run over the posts added from
	 * {@code from} 00:00:00 to {@code to} 23:59:59, Warsaw time.
	 *
	 * @param requestedSources the sources to fetch; empty = every configured source
	 * @return the created runs in execution order
	 * @throws SourceNotConfiguredException when a requested source is not configured, or none is
	 * @throws PipelineBusyException when a run is queued or running
	 */
	public List<PipelineRun> startRange(LocalDate from, LocalDate to, List<Source> requestedSources, UUID requestedBy) {
		PipelineRun range = PipelineRun.range(from.atStartOfDay(ZONE).toInstant(),
				to.atTime(LocalTime.of(23, 59, 59)).atZone(ZONE).toInstant(), sources.resolve(requestedSources),
				requestedBy, deploymentId);
		return queue(() -> posts.existsLeftover() ? List.of(PipelineRun.retry(requestedBy, deploymentId), range)
				: List.of(range));
	}

	/**
	 * Queues a run over {@code postIds}.
	 *
	 * @throws PostDeadException when one of them is dead
	 * @throws PipelineBusyException when a run is queued or running
	 */
	public List<PipelineRun> startPosts(List<UUID> postIds, UUID requestedBy) {
		return queue(() -> {
			List<UUID> dead = posts.findIdsInStage(postIds, PostStage.DEAD);
			if (!dead.isEmpty()) {
				throw new PostDeadException(dead);
			}
			return List.of(PipelineRun.posts(postIds, requestedBy, deploymentId));
		});
	}

	/**
	 * In one transaction under the advisory lock: refuses while a run is active, saves the runs
	 * {@code toCreate} returns; after the commit (the run thread must see them) queues them in order.
	 */
	private List<PipelineRun> queue(Supplier<List<PipelineRun>> toCreate) {
		List<PipelineRun> created = transaction.execute(status -> {
			jdbc.sql("SELECT pg_advisory_xact_lock(:lock)").param("lock", LOCK_ID).query((rs, row) -> true).single();
			if (runs.existsActive()) {
				throw new PipelineBusyException();
			}
			return runs.saveAll(toCreate.get());
		});
		created.forEach(run -> submit(run.getId()));
		return created;
	}

	private void submit(UUID runId) {
		own.add(runId);
		executor.execute(() -> execute(runId));
	}

	/** The runs this instance queued and has not finished (the watchdog's; in memory, no database query). */
	List<UUID> ownRuns() {
		return List.copyOf(own);
	}

	/**
	 * Runs one queued run to its end; package-private for tests.
	 *
	 * <p>The agent's actions run on Embabel's threads; this one waits for the agent's future, kept in
	 * {@link #waiting} meanwhile. An action's exception arrives wrapped in an {@link ExecutionException};
	 * this thread being stopped (the instance closing) as an {@link InterruptedException}; the future
	 * cancelled by {@link #stopStalled} as a {@link CancellationException}. After an interruption the
	 * run is not closed or reported here: {@link #onClose} already did both, and the database may be
	 * closing.
	 */
	void execute(UUID runId) {
		String error = "the run ended without a summary";
		Long started = null;
		boolean interrupted = false;
		try {
			if (recorder.start(runId)) {
				started = System.nanoTime();
				PipelineRun run = runs.findById(runId).orElseThrow();
				log.info("Pipeline run {} ({}) started", runId, run.getKind().value());
				CompletableFuture<RunSummary> agent = AgentInvocation.create(agentPlatform, RunSummary.class)
					.invokeAsync(RunRequest.of(run));
				waiting.put(runId, agent);
				agent.get();
			}
		}
		catch (InterruptedException ex) {
			// The instance is stopping: onClose interrupted its runs first.
			interrupted = true;
			log.info("Pipeline run {}: stopped waiting, the application is stopping", runId);
		}
		catch (CancellationException ex) {
			// stopStalled ended the run and put its posts back; its action may still run, changing nothing.
			log.info("Pipeline run {}: stopped waiting, the watchdog stopped the run", runId);
		}
		catch (Exception ex) {
			if (RunNoLongerRunningException.isCauseOf(ex)) {
				log.info("Pipeline run {} is no longer running here; its remaining steps were not started", runId);
			}
			else {
				log.error("Pipeline run {} failed", runId, ex);
				Throwable failure = unwrap(ex);
				error = failure.getClass().getSimpleName() + ": " + failure.getMessage();
			}
		}
		finally {
			waiting.remove(runId);
			if (!interrupted) {
				close(runId, error, started);
				notifications.runEnded(runId);
			}
			own.remove(runId);
			if (interrupted) {
				// Restored after the clean-up, whose database calls an interrupted thread may not get.
				Thread.currentThread().interrupt();
			}
		}
	}

	/**
	 * Stops a run of this instance that made no progress for {@code threshold} ({@link RunWatchdog}):
	 * ends it {@code failed} with its open steps ({@link RunRecorder#stall}) and puts its posts back, in
	 * one transaction; then notifies the admins, cancels the wait for its agent, so this queue takes the
	 * next run, and interrupts its post tasks ({@link AssessmentDispatcher#cancel}), so their threads of
	 * the LLM pool are freed.
	 *
	 * <p>The agent's action goes on in Embabel's thread until its current call ends, but changes
	 * nothing: claiming a post needs a running run, storing a result needs the post still held by the
	 * run, and its counters and steps are conditional. The cancelled wait is temporary — it goes with
	 * Embabel out of the run.
	 *
	 * @return whether the run was stopped; nothing happens to a run that is not running (still queued,
	 *     finished) or made progress meanwhile
	 */
	boolean stopStalled(UUID runId, Duration threshold) {
		// As in start-up recovery: the run ends and its posts go back together.
		boolean stopped = Boolean.TRUE.equals(transaction.execute(status -> {
			if (!recorder.stall(runId, threshold)) {
				return false;
			}
			transitions.releaseAll(runId);
			return true;
		}));
		if (!stopped) {
			return false;
		}
		log.warn("Pipeline run {} made no progress for {}; stopped", runId, Durations.format(threshold));
		// After the commit: the notifications read the stopped run.
		notifications.runEnded(runId);
		Future<?> agent = waiting.get(runId);
		if (agent != null) {
			agent.cancel(false);
		}
		dispatcher.cancel(runId);
		return true;
	}

	/**
	 * A run that is still queued or running here has failed with {@code error} (no change when its
	 * summary or an interruption ended it); its posts still {@code assessing} go back. A run that
	 * started here gets its end line in the log. When even this fails (the database is gone), start-up
	 * recovery does it.
	 *
	 * @param startedNanos when it started ({@link System#nanoTime()}), {@code null} when it did not
	 */
	private void close(UUID runId, String error, @Nullable Long startedNanos) {
		try {
			recorder.fail(runId, error);
			transitions.releaseAll(runId);
			if (startedNanos != null) {
				runs.findById(runId).ifPresent(run -> log.info("Pipeline run {} ({}) ended {} in {}: fetched {},"
						+ " skipped {}, new {}, ok {}, failed {}, dead {}, errors {}, retries {}, flex fallbacks {}",
						runId, run.getKind().value(), run.getStatus().value(), Durations.since(startedNanos),
						run.getFetched(), run.getSkipped(), run.getNewPosts(), run.getAssessedOk(),
						run.getAssessedFailed(), run.getDead(), run.getProcessingErrors(), run.getLlmRetries(),
						run.getFlexFallbacks()));
			}
		}
		catch (RuntimeException ex) {
			log.error("Pipeline run {} could not be closed; the next start-up recovery closes it", runId, ex);
		}
	}

	/** The action's own exception from inside the agent's future. */
	private static Throwable unwrap(Exception ex) {
		return (ex instanceof ExecutionException || ex instanceof CompletionException) && ex.getCause() != null
				? ex.getCause() : ex;
	}

	/**
	 * This instance stops: its own queued and running runs become {@code interrupted} (before the
	 * thread is stopped, so a run interrupted mid-step is not marked {@code failed}) and their posts are
	 * put back, in one transaction; then the admins are notified and the thread is stopped.
	 */
	@EventListener(ContextClosedEvent.class)
	void onClose() {
		List<UUID> interrupted = transaction.execute(status -> {
			List<UUID> ids = recorder.interrupt(List.copyOf(own), InterruptionCause.SHUTDOWN);
			ids.forEach(transitions::releaseAll);
			return ids;
		});
		// After the commit: the notifications read the interrupted runs.
		if (interrupted != null) {
			interrupted.forEach(notifications::runEnded);
		}
		executor.shutdownNow();
	}

}
