package pl.szczurradar.api.pipeline.assess;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import pl.szczurradar.api.agents.assessor.AssessmentResult;
import pl.szczurradar.api.agents.assessor.Assessor;
import pl.szczurradar.api.agents.assessor.contract.AssessmentInputV1;
import pl.szczurradar.api.llm.LlmCallException;
import pl.szczurradar.api.llm.LlmClient;
import pl.szczurradar.api.llm.LlmConfigurationException;
import pl.szczurradar.api.pipeline.flow.RunFlow.AssessedBatch;
import pl.szczurradar.api.pipeline.flow.RunFlow.ProcessingError;
import pl.szczurradar.api.pipeline.state.PostTransitions;
import pl.szczurradar.api.pipeline.state.RunRecorder;
import pl.szczurradar.api.pipeline.state.RunRecorder.Counters;
import pl.szczurradar.api.posts.Assessment;
import pl.szczurradar.api.posts.Post;
import pl.szczurradar.api.posts.PostRepository;

/**
 * {@link AssessmentDispatcher} on its own pool of {@code PIPELINE_LLM_CONCURRENCY} threads (default 4;
 * never {@code availableProcessors()}, which a container may report as 1). First one tiny call checks
 * the LLM's key, model and credit ({@link LlmClient#check()}); when it fails, no post is taken and the
 * step stops. Then each post, on a pool thread:
 *
 * <ol>
 *   <li>taken for the run ({@link PostTransitions#claim}) — when that fails because the run is no
 *       longer running, the remaining posts are left alone;
 *   <li>assessed: {@link Assessor#assess} on the input rebuilt from its raw payload — a failed call is
 *       a {@code failed} result, never an exception; a refused configuration
 *       ({@link LlmConfigurationException}, e.g. a revoked key) stops the step: no attempt is stored
 *       and the remaining posts are not taken;
 *   <li>stored with its next stage ({@link PostTransitions#record}), only while the run owns it;
 *   <li>otherwise put back without an attempt ({@link PostTransitions#release}); any exception there
 *       (a bug, a failed write) is a technical error of this post, and the other posts go on: the post
 *       goes back counting one more technical failure with its message
 *       ({@link PostTransitions#releaseFailed}; at {@link Post#MAX_TECHNICAL_FAILURES} in a row it is on
 *       hold);
 *   <li>counted in the run's counters ({@link RunRecorder#count}): a stored result with its call's
 *       retries and flex fallback, or a technical error; a post not taken counts nothing. A failed count
 *       is logged and leaves the post's outcome as it is.
 * </ol>
 *
 * A stopped step makes the run {@code failed} ({@link AssessedBatch#stoppedBy}), so a bad key never
 * uses up the posts' attempts. The log gets a progress line at most every {@link #PROGRESS_INTERVAL}
 * as posts finish, and one after the last. {@link #cancel} (the watchdog stopped the run) interrupts the
 * run's post tasks, so a stopped run never keeps threads of the pool waiting.
 */
@Component
public class ConcurrentAssessmentDispatcher implements AssessmentDispatcher, DisposableBean {

	/** The shortest time between two progress lines in the log. */
	static final Duration PROGRESS_INTERVAL = Duration.ofMinutes(1);

	private static final Logger log = LoggerFactory.getLogger(ConcurrentAssessmentDispatcher.class);

	private final PostRepository posts;

	private final AssessmentInputs inputs;

	private final Assessor assessor;

	private final PostTransitions transitions;

	private final RunRecorder recorder;

	private final LlmClient llm;

	private final ExecutorService executor;

	/** {@link System#nanoTime()}, or a test's clock for the progress lines. */
	private final LongSupplier nanoTime;

	/** The post tasks of each run while {@link #dispatch} waits for them; {@link #cancel} interrupts them. */
	private final Map<UUID, Tasks> dispatching = new ConcurrentHashMap<>();

	@Autowired
	ConcurrentAssessmentDispatcher(PostRepository posts, AssessmentInputs inputs, Assessor assessor,
			PostTransitions transitions, RunRecorder recorder, @Lazy LlmClient llm,
			@Value("${PIPELINE_LLM_CONCURRENCY:4}") int concurrency) {
		this(posts, inputs, assessor, transitions, recorder, llm, pool(concurrency), System::nanoTime);
	}

	ConcurrentAssessmentDispatcher(PostRepository posts, AssessmentInputs inputs, Assessor assessor,
			PostTransitions transitions, RunRecorder recorder, LlmClient llm, ExecutorService executor,
			LongSupplier nanoTime) {
		this.posts = posts;
		this.inputs = inputs;
		this.assessor = assessor;
		this.transitions = transitions;
		this.recorder = recorder;
		this.llm = llm;
		this.executor = executor;
		this.nanoTime = nanoTime;
	}

	@Override
	public AssessedBatch dispatch(UUID runId, List<UUID> postIds) {
		if (postIds.isEmpty()) {
			return new AssessedBatch(runId, 0, 0, 0, List.of(), null);
		}
		try {
			llm.check();
		}
		catch (LlmCallException ex) {
			log.error("The LLM check before run {} failed; no post is assessed", runId, ex);
			return new AssessedBatch(runId, 0, 0, 0, List.of(), "the LLM check failed: " + ex.getMessage());
		}
		AtomicBoolean runStopped = new AtomicBoolean();
		AtomicReference<@Nullable String> stoppedBy = new AtomicReference<>();
		Progress progress = new Progress(runId, postIds.size());
		// Registered before the first task, so a cancel while they are submitted reaches every one.
		Tasks tasks = new Tasks(new CopyOnWriteArrayList<>(), new AtomicBoolean());
		dispatching.put(runId, tasks);
		try {
			for (UUID postId : postIds) {
				tasks.futures().add(executor.submit(() -> {
					Outcome outcome = assess(runId, postId, runStopped, stoppedBy, tasks.cancelled());
					count(runId, postId, outcome);
					progress.finished(outcome);
					return outcome;
				}));
			}
			int ok = 0;
			int failed = 0;
			int dead = 0;
			List<ProcessingError> errors = new ArrayList<>();
			for (int i = 0; i < postIds.size(); i++) {
				switch (await(runId, postIds.get(i), tasks, i, progress)) {
					case Outcome.Stored stored when stored.ok() -> ok++;
					case Outcome.Stored stored -> {
						failed++;
						dead += stored.dead() ? 1 : 0;
					}
					case Outcome.Error error -> errors.add(new ProcessingError(postIds.get(i), error.message()));
					case Outcome.NotTaken notTaken -> {
					}
				}
			}
			progress.line();
			return new AssessedBatch(runId, ok, failed, dead, errors, stoppedBy.get());
		}
		finally {
			dispatching.remove(runId, tasks);
		}
	}

	/**
	 * Interrupts the post tasks of {@code runId}: one not started yet never runs, and one in flight gets
	 * an interrupt — an LLM call waiting for its answer or between attempts ends at once, one blocked in a
	 * socket read at the latest when its attempt's {@code LLM_TIMEOUT} runs out, and it is not tried again
	 * ({@code TransportRetry}'s wait fails on the pending interrupt). Its result is never stored (the
	 * stopped run no longer holds its posts), a failure it ends with is not the post's technical error, and
	 * nothing is counted. Called by {@code PipelineRunService.stopStalled} after the stop is written.
	 */
	@Override
	public void cancel(UUID runId) {
		Tasks tasks = dispatching.get(runId);
		if (tasks == null) {
			return;
		}
		tasks.cancelled().set(true);
		long cancelled = tasks.futures().stream().filter(future -> future.cancel(true)).count();
		log.info("Run {} assess: stopped, {} post task(s) not finished were cancelled", runId, cancelled);
	}

	private Outcome assess(UUID runId, UUID postId, AtomicBoolean runStopped, AtomicReference<@Nullable String> stoppedBy,
			AtomicBoolean cancelled) {
		if (runStopped.get() || cancelled.get()) {
			return new Outcome.NotTaken();
		}
		Optional<Integer> attempts = transitions.claim(postId, runId);
		if (attempts.isEmpty()) {
			if (!recorder.isRunning(runId)) {
				runStopped.set(true);
			}
			return new Outcome.NotTaken();
		}
		boolean stored = false;
		// A technical error of this post: put back counting it, with the same message as its processing error.
		@Nullable RuntimeException failure = null;
		Outcome.@Nullable Error error = null;
		try {
			AssessmentInputV1 input = inputs.of(posts.findById(postId).orElseThrow());
			AssessmentResult result = assessor.assess(input);
			stored = transitions.record(postId, runId, attempts.get(), new Assessment(postId, runId, input, result));
			if (!stored) {
				// The run lost the post (interrupted, post put back): not this run's error.
				return new Outcome.NotTaken();
			}
			// The prefilter makes no call (attempts 0).
			return new Outcome.Stored(result.ok(), !result.ok() && attempts.get() + 1 >= Post.MAX_ASSESSMENT_ATTEMPTS,
					Math.max(result.provenance().attempts() - 1, 0), result.provenance().flexFallback());
		}
		catch (LlmConfigurationException ex) {
			// Every later call would fail the same way: stop taking posts, count no attempt.
			runStopped.set(true);
			if (stoppedBy.compareAndSet(null, "the LLM provider refused the configuration: " + ex.getMessage())) {
				log.warn("The LLM provider refused the configuration in run {}; no further post is taken: {}", runId,
						ex.getMessage());
			}
			return new Outcome.NotTaken();
		}
		catch (RuntimeException ex) {
			if (cancelled.get()) {
				// cancel() interrupted it: the run was stopped, this is not the post's technical error.
				log.info("Assessing post {} in run {} ended with its stopped run: {}", postId, runId, ex.toString());
				return new Outcome.NotTaken();
			}
			failure = ex;
			error = new Outcome.Error(ex.getClass().getSimpleName() + ": " + ex.getMessage());
			return error;
		}
		finally {
			if (!stored) {
				if (error == null) {
					transitions.release(postId, runId);
				}
				else {
					Optional<Integer> failures = Optional.empty();
					try {
						failures = transitions.releaseFailed(postId, runId, error.message());
					}
					finally {
						// Logged even when putting the post back fails, which then fails the task.
						log.error("Assessing post {} in run {} failed; the post is put back without an attempt,"
								+ " technical failure {}", postId, runId,
								failures.map(String::valueOf).orElse("not counted"), failure);
					}
				}
			}
		}
	}

	/**
	 * Adds a post's outcome to the run's counters. A failure (e.g. the database for a moment) is logged
	 * and changes nothing else: a stored result stays stored, never a technical error.
	 */
	private void count(UUID runId, UUID postId, Outcome outcome) {
		try {
			switch (outcome) {
				case Outcome.Stored stored when stored.ok() ->
					recorder.count(runId, Counters.assessedOk(stored.llmRetries(), stored.flexFallback()));
				case Outcome.Stored stored ->
					recorder.count(runId,
							Counters.assessedFailed(stored.dead(), stored.llmRetries(), stored.flexFallback()));
				case Outcome.Error error -> recorder.count(runId, Counters.processingError());
				case Outcome.NotTaken notTaken -> {
				}
			}
		}
		catch (RuntimeException ex) {
			log.warn("The outcome of post {} could not be added to the counters of run {}; the post keeps it", postId,
					runId, ex);
		}
	}

	private Outcome await(UUID runId, UUID postId, Tasks tasks, int index, Progress progress) {
		try {
			return tasks.futures().get(index).get();
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			tasks.futures().forEach(outcome -> outcome.cancel(true));
			throw new IllegalStateException("interrupted while assessing run " + runId, ex);
		}
		catch (ExecutionException | CancellationException ex) {
			if (tasks.cancelled().get()) {
				// cancel(): the run was stopped and its posts put back; nothing to count.
				return new Outcome.NotTaken();
			}
			// assess() catches what it can; this is a task the pool never ran, or one that failed outside
			// it (e.g. taking the post), so it was not counted.
			Outcome error = new Outcome.Error(String.valueOf(ex));
			count(runId, postId, error);
			progress.finished(error);
			return error;
		}
	}

	private static ExecutorService pool(int concurrency) {
		if (concurrency < 1) {
			throw new IllegalStateException("PIPELINE_LLM_CONCURRENCY must be at least 1 (got " + concurrency + ")");
		}
		AtomicInteger thread = new AtomicInteger();
		return Executors.newFixedThreadPool(concurrency,
				task -> Thread.ofPlatform().name("pipeline-llm-" + thread.incrementAndGet()).unstarted(task));
	}

	/** Stops the threads when the context closes; an assessment in flight is interrupted. */
	@Override
	public void destroy() {
		executor.shutdownNow();
	}

	/**
	 * A run's post tasks while {@link #dispatch} waits for them.
	 *
	 * @param cancelled whether {@link #cancel} stopped them
	 */
	private record Tasks(List<Future<Outcome>> futures, AtomicBoolean cancelled) {
	}

	/** What happened to one post. */
	private sealed interface Outcome {

		/**
		 * An assessment was stored.
		 *
		 * @param llmRetries HTTP attempts of its call beyond the first
		 * @param flexFallback whether its call fell back from the flex tier to the default one
		 */
		record Stored(boolean ok, boolean dead, int llmRetries, boolean flexFallback) implements Outcome {
		}

		/** A technical error: no assessment, the post put back. */
		record Error(String message) implements Outcome {
		}

		/** Not taken by this run (not assessable, the run stopped, or the post lost to recovery). */
		record NotTaken() implements Outcome {
		}

	}

	/**
	 * The step's progress for the log: what its posts did so far, as they finish. A post finishing
	 * writes a line when {@link #PROGRESS_INTERVAL} has passed since the last one (or the step's
	 * start); {@link #line()} writes one at the end. Never fails a post.
	 */
	private final class Progress {

		private final UUID runId;

		private final int total;

		private final AtomicInteger ok = new AtomicInteger();

		private final AtomicInteger failed = new AtomicInteger();

		private final AtomicInteger dead = new AtomicInteger();

		private final AtomicInteger errors = new AtomicInteger();

		private final AtomicInteger retries = new AtomicInteger();

		private final AtomicInteger fallbacks = new AtomicInteger();

		/** When the last line was written ({@link #nanoTime}). */
		private final AtomicLong lastLine;

		Progress(UUID runId, int total) {
			this.runId = runId;
			this.total = total;
			this.lastLine = new AtomicLong(nanoTime.getAsLong());
		}

		void finished(Outcome outcome) {
			switch (outcome) {
				case Outcome.Stored stored -> {
					(stored.ok() ? ok : failed).incrementAndGet();
					dead.addAndGet(stored.dead() ? 1 : 0);
					retries.addAndGet(stored.llmRetries());
					fallbacks.addAndGet(stored.flexFallback() ? 1 : 0);
				}
				case Outcome.Error error -> errors.incrementAndGet();
				case Outcome.NotTaken notTaken -> {
				}
			}
			long now = nanoTime.getAsLong();
			long last = lastLine.get();
			// One thread writes the line of each interval.
			if (now - last >= PROGRESS_INTERVAL.toNanos() && lastLine.compareAndSet(last, now)) {
				line();
			}
		}

		/**
		 * {@code Run <id> assess: <done>/<N> (ok …, failed …, dead …, errors …), in flight …, retries …,
		 * flex fallbacks …}; no post content.
		 */
		void line() {
			try {
				int done = ok.get() + failed.get() + errors.get();
				log.info("Run {} assess: {}/{} (ok {}, failed {}, dead {}, errors {}), in flight {}, retries {},"
						+ " flex fallbacks {}", runId, done, total, ok.get(), failed.get(), dead.get(), errors.get(),
						recorder.inFlight(runId), retries.get(), fallbacks.get());
			}
			catch (RuntimeException ex) {
				log.warn("The progress of run {} could not be logged", runId, ex);
			}
		}

	}

}
