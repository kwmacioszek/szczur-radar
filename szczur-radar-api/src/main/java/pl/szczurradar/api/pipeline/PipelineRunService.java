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
import org.springframework.beans.factory.ObjectProvider;
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
 *   <li>A run nobody executes any more (its instance was lost without closing it, a run queued on an old
 *       instance after the new one's recovery, a failed start-up recovery) would refuse every request.
 *       So each request to start a run first, under the same advisory lock: when this instance has no
 *       run of its own, marks every queued and running run {@code interrupted}
 *       ({@link InterruptionCause#LOST}) if none of them showed activity for {@code PIPELINE_STALL_TIMEOUT}
 *       — all of them or none, the queue being one ({@link RunRecorder#interruptOrphans}); and puts back
 *       every post still {@code assessing} without a queued or running run
 *       ({@link PostTransitions#releaseOrphans}). This clean-up commits on its own, also when the request
 *       is then refused. The API asks the database nothing more while idle: only a request does it.
 *   <li>When the context closes, this instance marks its own queued and running runs
 *       {@code interrupted} ({@link InterruptionCause#SHUTDOWN}) first, then stops its thread; a newer
 *       deployment's start-up recovery does the same for runs nobody finished
 *       ({@link PipelineStartupRecovery}).
 *   <li>Every way a run ends here — its own end, the watchdog's stop, the instance closing, the clean-up
 *       of orphaned runs — reports it
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

	/**
	 * {@code PIPELINE_STALL_TIMEOUT}, the server's bean ({@link PipelineStallConfiguration}); resolved only
	 * when a run is requested, so an operator command's context (no web server, no bean) never reads it.
	 */
	private final ObjectProvider<PipelineStallSettings> stall;

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
			ObjectProvider<PipelineStallSettings> stall,
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
		this.stall = stall;
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
		cleanUpOrphans();
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
		cleanUpOrphans();
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
	 *
	 * <p>Every request first cleans up orphans ({@link #cleanUpOrphans}, called by {@link #startRange} and
	 * {@link #startPosts} before anything can refuse them), in a transaction of its own under the same lock,
	 * because a refusal ({@link SourceNotConfiguredException}, {@link PipelineBusyException}, or
	 * {@link PostDeadException} from {@code toCreate}) would roll back the one it is thrown in: the clean-up
	 * stays committed whatever refuses the request, and the refusals keep their order.
	 */
	private List<PipelineRun> queue(Supplier<List<PipelineRun>> toCreate) {
		List<PipelineRun> created = transaction.execute(status -> {
			lock();
			if (runs.existsActive()) {
				throw new PipelineBusyException();
			}
			return runs.saveAll(toCreate.get());
		});
		created.forEach(run -> submit(run.getId()));
		return created;
	}

	/**
	 * In one transaction under the advisory lock, before a request to start runs is decided: when this
	 * instance has no run of its own (its own runs are its watchdog's), marks every queued and running
	 * run {@code interrupted} ({@link InterruptionCause#LOST}) if none of them showed activity for
	 * {@code PIPELINE_STALL_TIMEOUT} ({@link RunRecorder#interruptOrphans}: all or none); then, in every
	 * case, puts back the posts still {@code assessing} without a queued or running run
	 * ({@link PostTransitions#releaseOrphans}), e.g. one claimed just as its run was interrupted. After the
	 * commit: a WARN for each, and the interrupted runs reported to {@link RunNotifications#runEnded}. A
	 * failure (e.g. a deadlock with another instance's recovery) is logged and the request goes on, as in
	 * start-up recovery and the watchdog: a clean-up must never block every start, and the decision after it
	 * still refuses while a run is active.
	 */
	private void cleanUpOrphans() {
		Duration threshold = stall.getObject().timeout();
		Orphans orphans;
		try {
			orphans = transaction.execute(status -> {
				lock();
				List<UUID> lost = own.isEmpty() ? recorder.interruptOrphans(threshold) : List.of();
				return new Orphans(lost, transitions.releaseOrphans());
			});
		}
		catch (RuntimeException ex) {
			log.error("The clean-up of orphaned pipeline runs failed; the request is decided without it", ex);
			return;
		}
		if (orphans == null) {
			return;
		}
		if (!orphans.runs().isEmpty()) {
			log.warn("Marked {} orphaned pipeline run(s) interrupted: no queued or running run showed activity for {}"
					+ " and none runs on this instance: {}", orphans.runs().size(), Durations.format(threshold),
					orphans.runs());
		}
		if (orphans.posts() > 0) {
			log.warn("Put back {} post(s) left assessing by ended runs", orphans.posts());
		}
		// After the commit: the notifications read the interrupted runs.
		orphans.runs().forEach(notifications::runEnded);
	}

	/** Creating runs and cleaning up orphans wait for each other, on every instance (one database). */
	private void lock() {
		jdbc.sql("SELECT pg_advisory_xact_lock(:lock)").param("lock", LOCK_ID).query((rs, row) -> true).single();
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

	/**
	 * What {@link #cleanUpOrphans} did.
	 *
	 * @param runs the orphaned runs marked interrupted
	 * @param posts how many posts were put back
	 */
	private record Orphans(List<UUID> runs, int posts) {
	}

}
