package pl.szczurradar.api.pipeline.assess;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import pl.szczurradar.api.agents.assessor.AssessmentResult;
import pl.szczurradar.api.agents.assessor.Assessor;
import pl.szczurradar.api.agents.assessor.contract.AssessmentInputV1;
import pl.szczurradar.api.alerts.AlertEvent;
import pl.szczurradar.api.alerts.OperatorAlertQueue;
import pl.szczurradar.api.llm.LlmCallException;
import pl.szczurradar.api.llm.LlmClient;
import pl.szczurradar.api.llm.LlmClientException;
import pl.szczurradar.api.llm.LlmConfigurationException;
import pl.szczurradar.api.llm.LlmProviderProperties;
import pl.szczurradar.api.llm.LlmUnavailableException;
import pl.szczurradar.api.llm.transport.ProviderMessages;
import pl.szczurradar.api.pipeline.flow.RunFlow.AssessedBatch;
import pl.szczurradar.api.pipeline.flow.RunFlow.ProcessingError;
import pl.szczurradar.api.pipeline.run.RunStep;
import pl.szczurradar.api.pipeline.state.PostTransitions;
import pl.szczurradar.api.pipeline.state.RunInterruptedException;
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
 *   <li>assessed: {@link Assessor#assess} on the input rebuilt from its raw payload — a call the
 *       provider refused is a {@code failed} result, never an exception; a refused configuration
 *       ({@link LlmConfigurationException}, e.g. a revoked key) stops the step: no attempt is stored
 *       and the remaining posts are not taken; a provider unavailable after the client's retries
 *       ({@link LlmUnavailableException}) stores no attempt and puts the post back, and
 *       {@value #OUTAGE_STREAK} such calls in a row (in the order they end, with no call answered by
 *       the model between them; the prefilter makes no call) stop the step the same way; so do
 *       {@value #CLIENT_ERROR_STREAK} calls in a row that leave the client with an error it could not
 *       classify ({@link LlmClientException}, e.g. after an SDK change), each still a technical error of
 *       its post (below);
 *   <li>stored with its next stage ({@link PostTransitions#record}), only while the run owns it;
 *   <li>otherwise put back without an attempt ({@link PostTransitions#release}); any exception there
 *       (a bug, a failed write) is a technical error of this post, and the other posts go on: the post
 *       goes back counting one more technical failure with its message
 *       ({@link PostTransitions#releaseFailed}; at {@link Post#MAX_TECHNICAL_FAILURES} in a row it is on
 *       hold);
 *   <li>counted in the run's counters ({@link RunRecorder#count}): a stored result with its call's
 *       retries and flex fallback, or a technical error; a post not taken counts nothing. A failed count
 *       is logged (WARN) and leaves the post's outcome as it is; once every post has finished, the step's
 *       failed counts give one ERROR line (the run's counters are understated, so its status may not show
 *       that it needs attention) and one operator alert ({@link AlertEvent#RUN_COUNTERS_LOST}, through
 *       the server's {@link OperatorAlertQueue}; none in an operator command's context).
 * </ol>
 *
 * A stopped step makes the run {@code failed} ({@link AssessedBatch#stoppedBy}), so neither a bad key
 * nor a provider outage uses up the posts' attempts, and a client error every call ends with costs
 * {@value #CLIENT_ERROR_STREAK} posts a technical failure each, not every post of the run. The client's
 * streak counts like the outages' (in the order the calls end, reset only by a stored result whose call
 * reached the model); an outage, the prefilter and an error outside the client (a mapper, a write) leave
 * it as it is, and an error of a cancelled task counts nothing. A single outage the step went on after is a
 * technical error of its post, counted only once every post has finished, and only when nothing stopped
 * the step: the stop may come after the post was put back. Its post counts a technical failure
 * ({@link PostTransitions#countTechnicalFailure}, only while the run is still running and only for a
 * post this step put back itself), then the run a processing error. The log gets a progress line at
 * most every {@link #PROGRESS_INTERVAL} as posts finish, and one after the last. A stopped run never
 * keeps threads of the pool waiting, by either of two ways: the thread of {@link #dispatch} interrupted
 * while it waits for the posts (the watchdog's stop, a stopping instance) cancels the run's post tasks and
 * throws, and {@link #cancel} (the watchdog's stop) cancels them from outside, also while that thread is
 * where an interrupt does not reach it (the LLM check on its HTTP client). Either way a cancelled task
 * counts nothing and is never a post's technical error or an outage.
 */
@Component
public class ConcurrentAssessmentDispatcher implements AssessmentDispatcher {

	/** The shortest time between two progress lines in the log. */
	static final Duration PROGRESS_INTERVAL = Duration.ofMinutes(1);

	/** Calls in a row that found the provider unavailable after their retries and stop the step. */
	static final int OUTAGE_STREAK = 3;

	/**
	 * Calls in a row that left the LLM client with an error it could not classify ({@link LlmClientException})
	 * and stop the step: such an error is most likely systematic, so it would go through every post.
	 */
	static final int CLIENT_ERROR_STREAK = 3;

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

	/** The server's queue ({@code AlertsConfiguration}), resolved when used; none in an operator command. */
	private final ObjectProvider<OperatorAlertQueue> alerts;

	/** Cleans the key out of a failed check that is not the provider's: the run's error goes to the operator. */
	private final ProviderMessages messages;

	/** The post tasks of each run while {@link #dispatch} waits for them; {@link #cancel} interrupts them. */
	private final Map<UUID, Tasks> dispatching = new ConcurrentHashMap<>();

	@Autowired
	ConcurrentAssessmentDispatcher(PostRepository posts, AssessmentInputs inputs, Assessor assessor,
			PostTransitions transitions, RunRecorder recorder, @Lazy LlmClient llm, AssessmentPool pool,
			ObjectProvider<OperatorAlertQueue> alerts, LlmProviderProperties llmSettings) {
		this(posts, inputs, assessor, transitions, recorder, llm, pool.executor(), System::nanoTime, alerts,
				new ProviderMessages(llmSettings.apiKey()));
	}

	ConcurrentAssessmentDispatcher(PostRepository posts, AssessmentInputs inputs, Assessor assessor,
			PostTransitions transitions, RunRecorder recorder, LlmClient llm, ExecutorService executor,
			LongSupplier nanoTime, ObjectProvider<OperatorAlertQueue> alerts, ProviderMessages messages) {
		this.posts = posts;
		this.inputs = inputs;
		this.assessor = assessor;
		this.transitions = transitions;
		this.recorder = recorder;
		this.llm = llm;
		this.executor = executor;
		this.nanoTime = nanoTime;
		this.alerts = alerts;
		this.messages = messages;
	}

	@Override
	public AssessedBatch dispatch(UUID runId, List<UUID> postIds) {
		if (postIds.isEmpty()) {
			return new AssessedBatch(runId, 0, 0, 0, List.of(), null);
		}
		try {
			llm.check();
		}
		catch (RuntimeException ex) {
			if (Thread.currentThread().isInterrupted()) {
				// The run's thread was interrupted during the check (the watchdog's cancel(true), a stopping
				// instance's shutdownNow()): whoever stopped the run wrote its end; not the check's failure.
				log.info("Run {} assess: the LLM check was interrupted by the run's stop", runId);
				throw new RunInterruptedException(runId, RunStep.ASSESS);
			}
			// A provider's refusal or outage, or an error that is not the provider's (LlmClient#complete): its
			// message and causes may come from outside the clients (e.g. building one), so they are cleaned here.
			log.error("The LLM check before run {} failed; no post is assessed", runId, messages.cleanCause(ex));
			return new AssessedBatch(runId, 0, 0, 0, List.of(),
					"the LLM check failed: " + messages.clean(String.valueOf(ex.getMessage())));
		}
		AtomicBoolean runStopped = new AtomicBoolean();
		AtomicReference<@Nullable String> stoppedBy = new AtomicReference<>();
		// Calls in a row, in the order they end, that found the provider unavailable.
		AtomicInteger outages = new AtomicInteger();
		// Calls in a row, in the order they end, that left the client with an error it could not classify.
		AtomicInteger clientErrors = new AtomicInteger();
		Progress progress = new Progress(runId, postIds.size());
		// The results whose count fails; reported once every post has finished.
		LostCounts lost = new LostCounts();
		// Registered before the first task, so a cancel while they are submitted reaches every one.
		Tasks tasks = new Tasks(new CopyOnWriteArrayList<>(), new AtomicBoolean());
		dispatching.put(runId, tasks);
		try {
			for (UUID postId : postIds) {
				tasks.futures().add(executor.submit(() -> {
					Outcome outcome = assess(runId, postId, runStopped, stoppedBy, outages, clientErrors,
							tasks.cancelled());
					count(runId, postId, outcome, lost);
					progress.finished(outcome);
					return outcome;
				}));
			}
			int ok = 0;
			int failed = 0;
			int dead = 0;
			List<ProcessingError> errors = new ArrayList<>();
			Map<UUID, Outcome.Unavailable> unavailable = new LinkedHashMap<>();
			for (int i = 0; i < postIds.size(); i++) {
				switch (await(runId, postIds.get(i), tasks, i, progress, lost)) {
					case Outcome.Stored stored when stored.ok() -> ok++;
					case Outcome.Stored stored -> {
						failed++;
						dead += stored.dead() ? 1 : 0;
					}
					case Outcome.Error error -> errors.add(new ProcessingError(postIds.get(i), error.message()));
					case Outcome.Unavailable outage -> unavailable.put(postIds.get(i), outage);
					case Outcome.NotTaken notTaken -> {
					}
				}
			}
			// Only now: the step may have stopped after an earlier outage's post was put back.
			if (stoppedBy.get() == null) {
				unavailable.forEach((postId, outage) -> {
					if (countOutage(runId, postId, outage, lost)) {
						errors.add(new ProcessingError(postId, outage.message()));
					}
				});
			}
			progress.line();
			lost.report(runId);
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
	 * nothing is counted. Called by {@code PipelineRunService.stopStalled} after the stop is written and
	 * after it interrupted the run's thread (which, waiting in {@link #dispatch}, cancels the same tasks
	 * itself): this reaches them also when that thread is not waiting yet, e.g. in the LLM check.
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
			AtomicInteger outages, AtomicInteger clientErrors, AtomicBoolean cancelled) {
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
		// Already put back here (after an outage), so that its outcome knows whether this task did it.
		boolean putBack = false;
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
			if (result.provenance().attempts() >= 1) {
				// The provider answered (the prefilter makes no call): neither the outages nor the client's
				// errors are in a row any more.
				outages.set(0);
				clientErrors.set(0);
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
		catch (LlmUnavailableException ex) {
			if (cancelled.get()) {
				// cancel() interrupted it: the run was stopped, this is not an outage of the provider.
				log.info("Assessing post {} in run {} ended with its stopped run: {}", postId, runId, ex.toString());
				return new Outcome.NotTaken();
			}
			if (outages.incrementAndGet() >= OUTAGE_STREAK) {
				// Every later call would most likely fail the same way: stop taking posts, count no attempt.
				runStopped.set(true);
				if (stoppedBy.compareAndSet(null, "the LLM provider is unavailable: " + OUTAGE_STREAK
						+ " calls in a row failed after their retries; the last: " + ex.getMessage())) {
					log.warn("The LLM provider is unavailable in run {}: {} calls in a row failed after their retries;"
							+ " no further post is taken: {}", runId, OUTAGE_STREAK, ex.getMessage());
				}
			}
			boolean released = transitions.release(postId, runId);
			// Only once it is back: when putting it back fails, the finally block below tries again.
			putBack = true;
			return new Outcome.Unavailable(ex.getClass().getSimpleName() + ": " + ex.getMessage(), released);
		}
		catch (RuntimeException ex) {
			if (cancelled.get()) {
				// cancel() interrupted it: the run was stopped, this is not the post's technical error.
				log.info("Assessing post {} in run {} ended with its stopped run: {}", postId, runId, ex.toString());
				return new Outcome.NotTaken();
			}
			if (ex instanceof LlmClientException && clientErrors.incrementAndGet() >= CLIENT_ERROR_STREAK) {
				// Most likely every later call would fail the same way: stop taking posts. This post is still
				// put back as a technical error (below), as each of the streak's posts.
				runStopped.set(true);
				if (stoppedBy.compareAndSet(null, "the LLM client failed " + CLIENT_ERROR_STREAK
						+ " calls in a row with an error that is not the provider's; the last: "
						+ ex.getClass().getSimpleName() + ": " + ex.getMessage())) {
					log.warn("The LLM client failed {} calls in a row in run {} with an error that is not the provider's;"
							+ " no further post is taken: {}", CLIENT_ERROR_STREAK, runId, ex.getMessage());
				}
			}
			failure = ex;
			error = new Outcome.Error(ex.getClass().getSimpleName() + ": " + ex.getMessage());
			return error;
		}
		finally {
			if (!stored && !putBack) {
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
	 * Adds a post's outcome to the run's counters. A failure (e.g. the database for a moment) is logged,
	 * kept in {@code lost} when the outcome was a result for the counters (a stored result or a technical
	 * error), and changes nothing else: a stored result stays stored, never a technical error.
	 */
	private void count(UUID runId, UUID postId, Outcome outcome, LostCounts lost) {
		try {
			switch (outcome) {
				case Outcome.Stored stored when stored.ok() ->
					recorder.count(runId, Counters.assessedOk(stored.llmRetries(), stored.flexFallback()));
				case Outcome.Stored stored ->
					recorder.count(runId,
							Counters.assessedFailed(stored.dead(), stored.llmRetries(), stored.flexFallback()));
				case Outcome.Error error -> recorder.count(runId, Counters.processingError());
				// Counted, if at all, once the step has finished (countOutage); its end is progress already.
				case Outcome.Unavailable outage -> recorder.touch(runId);
				case Outcome.NotTaken notTaken -> {
				}
			}
		}
		catch (RuntimeException ex) {
			log.warn("The outcome of post {} could not be added to the counters of run {}; the post keeps it", postId,
					runId, ex);
			lost.add(outcome);
		}
	}

	/**
	 * A single outage of the provider the step went on after, once every post has finished: a technical
	 * failure of the post this task put back ({@link PostTransitions#countTechnicalFailure}, only while
	 * the run is still running), then a processing error of the run. Otherwise (the post was not put back
	 * by this task, the run no longer runs, or the write failed) it counts like a post not taken.
	 *
	 * @return whether it was counted
	 */
	private boolean countOutage(UUID runId, UUID postId, Outcome.Unavailable outage, LostCounts lost) {
		if (!outage.putBack()) {
			return false;
		}
		boolean counted;
		try {
			counted = transitions.countTechnicalFailure(postId, runId, outage.message());
		}
		catch (RuntimeException ex) {
			log.warn("The outage of post {} in run {} could not be counted as its technical failure", postId, runId, ex);
			return false;
		}
		if (counted) {
			log.warn("Assessing post {} in run {} found the LLM provider unavailable; the post was put back without an"
					+ " attempt, counting a technical failure: {}", postId, runId, outage.message());
			count(runId, postId, new Outcome.Error(outage.message()), lost);
		}
		return counted;
	}

	private Outcome await(UUID runId, UUID postId, Tasks tasks, int index, Progress progress, LostCounts lost) {
		try {
			return tasks.futures().get(index).get();
		}
		catch (InterruptedException ex) {
			// The run's thread was stopped (the watchdog, a stopping instance): its tasks end as cancel() ends
			// them, so marked first — an interrupted call is then neither an outage nor a technical error.
			tasks.cancelled().set(true);
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
			count(runId, postId, error, lost);
			progress.finished(error);
			return error;
		}
	}

	/**
	 * A run's post tasks while {@link #dispatch} waits for them.
	 *
	 * @param cancelled whether {@link #cancel}, or an interrupt of the thread waiting for them, stopped them
	 */
	private record Tasks(List<Future<Outcome>> futures, AtomicBoolean cancelled) {
	}

	/**
	 * The post results of one {@link #dispatch} that did not reach the run's counters (a failed
	 * {@link RunRecorder#count}): how many, how many of them dead and how many technical errors. Added to
	 * from the pool's threads, reported once every post has finished.
	 */
	private final class LostCounts {

		private final AtomicInteger results = new AtomicInteger();

		private final AtomicInteger dead = new AtomicInteger();

		private final AtomicInteger errors = new AtomicInteger();

		/** A result whose count failed; an outage's activity ({@code touch}) is no result and is not kept. */
		void add(Outcome outcome) {
			switch (outcome) {
				case Outcome.Stored stored -> {
					results.incrementAndGet();
					dead.addAndGet(stored.dead() ? 1 : 0);
				}
				case Outcome.Error error -> {
					results.incrementAndGet();
					errors.incrementAndGet();
				}
				case Outcome.Unavailable outage -> {
				}
				case Outcome.NotTaken notTaken -> {
				}
			}
		}

		/** When a result was lost: one ERROR line and one {@link AlertEvent#RUN_COUNTERS_LOST}. */
		void report(UUID runId) {
			int lost = results.get();
			if (lost == 0) {
				return;
			}
			log.error("{} post result(s) of run {} could not be added to its counters ({} dead, {} technical"
					+ " error(s)); its counters are understated and needsAttention may be false", lost, runId,
					dead.get(), errors.get());
			OperatorAlertQueue.raise(alerts, AlertEvent.RUN_COUNTERS_LOST, runId, lost + " post result(s) of"
					+ " pipeline run " + runId + " did not reach its counters (" + dead.get() + " dead, "
					+ errors.get() + " technical error(s)); its status may hide them; /admin/runs/" + runId
					+ "; details in the API log");
		}

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

		/**
		 * The provider was unavailable after the call's retries: no assessment, no attempt, the post put
		 * back. Counted only once the step has finished ({@link #countOutage}).
		 *
		 * @param putBack whether this task put the post back (the run still held it)
		 */
		record Unavailable(String message, boolean putBack) implements Outcome {
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

		private final AtomicInteger unavailable = new AtomicInteger();

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
				case Outcome.Unavailable outage -> unavailable.incrementAndGet();
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
		 * {@code Run <id> assess: <done>/<N> (ok …, failed …, dead …, errors …, unavailable …), in flight …,
		 * retries …, flex fallbacks …}; no post content. {@code unavailable} are the posts whose call found
		 * the provider unavailable: the run's processing errors are {@code errors} and those of them counted
		 * at the step's end.
		 */
		void line() {
			try {
				int done = ok.get() + failed.get() + errors.get() + unavailable.get();
				log.info("Run {} assess: {}/{} (ok {}, failed {}, dead {}, errors {}, unavailable {}), in flight {},"
						+ " retries {}, flex fallbacks {}", runId, done, total, ok.get(), failed.get(), dead.get(),
						errors.get(), unavailable.get(), recorder.inFlight(runId), retries.get(), fallbacks.get());
			}
			catch (RuntimeException ex) {
				log.warn("The progress of run {} could not be logged", runId, ex);
			}
		}

	}

}
