package pl.szczurradar.api.pipeline;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import com.embabel.agent.api.invocation.AgentInvocation;
import com.embabel.agent.core.AgentPlatform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import pl.szczurradar.api.posts.Post;
import pl.szczurradar.api.sources.Source;

/**
 * Creates pipeline runs and executes them one at a time, in order, on one background thread.
 *
 * <ul>
 *   <li>A range request creates up to two queued runs in one transaction: a {@link RunKind#RETRY}
 *       run when there are leftovers, then the {@link RunKind#RANGE} run. A request while a run is
 *       queued or running is refused ({@link PipelineBusyException}); the check and the insert hold
 *       a PostgreSQL advisory lock, so two concurrent requests never create two sets of runs.
 *   <li>A run starts only from {@code queued} (a conditional update); it ends with the agent's
 *       summary, as {@code failed} on an exception, and in every case its posts still
 *       {@code assessing} are put back.
 *   <li>When the context closes, this instance marks its own queued and running runs
 *       {@code interrupted} first, then stops its thread; a newer deployment's start-up recovery
 *       does the same for runs nobody finished ({@link PipelineStartupRecovery}).
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

	private final SourceIngest sources;

	private final RunRecorder recorder;

	private final PostTransitions transitions;

	private final AgentPlatform agentPlatform;

	private final JdbcClient jdbc;

	private final TransactionTemplate transaction;

	private final ExecutorService executor = Executors
		.newSingleThreadExecutor(task -> Thread.ofPlatform().name("pipeline-run").unstarted(task));

	/** The runs this instance queued and has not finished. */
	private final Set<UUID> own = ConcurrentHashMap.newKeySet();

	PipelineRunService(PipelineRunRepository runs, SourceIngest sources, RunRecorder recorder, PostTransitions transitions,
			AgentPlatform agentPlatform, JdbcClient jdbc, TransactionTemplate transaction) {
		this.runs = runs;
		this.sources = sources;
		this.recorder = recorder;
		this.transitions = transitions;
		this.agentPlatform = agentPlatform;
		this.jdbc = jdbc;
		this.transaction = transaction;
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
				requestedBy);
		return queue(() -> {
			List<PipelineRun> created = new ArrayList<>();
			if (hasLeftovers()) {
				created.add(runs.save(PipelineRun.retry(requestedBy)));
			}
			created.add(runs.save(range));
			return created;
		});
	}

	/**
	 * Queues a run over {@code postIds}.
	 *
	 * @throws PostDeadException when one of them is dead
	 * @throws PipelineBusyException when a run is queued or running
	 */
	public List<PipelineRun> startPosts(List<UUID> postIds, UUID requestedBy) {
		return queue(() -> {
			List<UUID> dead = jdbc.sql("SELECT id FROM posts WHERE id = ANY(:ids) AND stage = 'dead' ORDER BY id")
				.param("ids", postIds.toArray(UUID[]::new))
				.query(UUID.class)
				.list();
			if (!dead.isEmpty()) {
				throw new PostDeadException(dead);
			}
			return List.of(runs.save(PipelineRun.posts(postIds, requestedBy)));
		});
	}

	private List<PipelineRun> queue(Supplier<List<PipelineRun>> create) {
		List<PipelineRun> created = transaction.execute(status -> {
			jdbc.sql("SELECT pg_advisory_xact_lock(:lock)").param("lock", LOCK_ID).query((rs, row) -> true).single();
			boolean busy = jdbc
				.sql("SELECT EXISTS (SELECT 1 FROM pipeline_runs WHERE status IN ('queued', 'running'))")
				.query(Boolean.class)
				.single();
			if (busy) {
				throw new PipelineBusyException();
			}
			return create.get();
		});
		// Submitted only after the commit: the run thread must see the queued rows.
		created.forEach(run -> submit(run.getId()));
		return created;
	}

	private boolean hasLeftovers() {
		return jdbc.sql("SELECT EXISTS (SELECT 1 FROM posts WHERE stage = 'stored'"
				+ " OR (stage = 'failed' AND assessment_attempts < :max))")
			.param("max", Post.MAX_ASSESSMENT_ATTEMPTS)
			.query(Boolean.class)
			.single();
	}

	private void submit(UUID runId) {
		own.add(runId);
		executor.execute(() -> execute(runId));
	}

	/** Runs one queued run to its end; package-private for tests. */
	void execute(UUID runId) {
		try {
			if (!recorder.start(runId)) {
				return;
			}
			PipelineRun run = runs.findById(runId).orElseThrow();
			try {
				AgentInvocation.create(agentPlatform, RunFlow.RunSummary.class).invoke(RunFlow.RunRequest.of(run));
			}
			catch (RuntimeException ex) {
				log.error("Pipeline run {} failed", runId, ex);
				recorder.finish(runId, RunStatus.FAILED, ex.getClass().getSimpleName() + ": " + ex.getMessage());
			}
			if (recorder.isRunning(runId)) {
				recorder.finish(runId, RunStatus.FAILED, "the run ended without a summary");
			}
		}
		finally {
			transitions.releaseAll(runId);
			own.remove(runId);
		}
	}

	/**
	 * This instance stops: its own queued and running runs become {@code interrupted} (before the
	 * thread is stopped, so a run interrupted mid-step is not marked {@code failed}), their posts are
	 * put back, then the thread is stopped.
	 */
	@EventListener(ContextClosedEvent.class)
	void onClose() {
		for (UUID runId : recorder.interrupt(List.copyOf(own), "interrupted: the application stopped")) {
			transitions.releaseAll(runId);
		}
		executor.shutdownNow();
	}

}
