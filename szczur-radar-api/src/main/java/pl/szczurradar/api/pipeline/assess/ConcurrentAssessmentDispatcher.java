package pl.szczurradar.api.pipeline.assess;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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
 *       (a bug, a failed write) is a technical error of this post, and the other posts go on.
 * </ol>
 *
 * A stopped step makes the run {@code failed} ({@link AssessedBatch#stoppedBy}), so a bad key never
 * uses up the posts' attempts.
 */
@Component
public class ConcurrentAssessmentDispatcher implements AssessmentDispatcher, DisposableBean {

	private static final Logger log = LoggerFactory.getLogger(ConcurrentAssessmentDispatcher.class);

	private final PostRepository posts;

	private final AssessmentInputs inputs;

	private final Assessor assessor;

	private final PostTransitions transitions;

	private final RunRecorder recorder;

	private final LlmClient llm;

	private final ExecutorService executor;

	@Autowired
	ConcurrentAssessmentDispatcher(PostRepository posts, AssessmentInputs inputs, Assessor assessor,
			PostTransitions transitions, RunRecorder recorder, @Lazy LlmClient llm,
			@Value("${PIPELINE_LLM_CONCURRENCY:4}") int concurrency) {
		this(posts, inputs, assessor, transitions, recorder, llm, pool(concurrency));
	}

	ConcurrentAssessmentDispatcher(PostRepository posts, AssessmentInputs inputs, Assessor assessor,
			PostTransitions transitions, RunRecorder recorder, LlmClient llm, ExecutorService executor) {
		this.posts = posts;
		this.inputs = inputs;
		this.assessor = assessor;
		this.transitions = transitions;
		this.recorder = recorder;
		this.llm = llm;
		this.executor = executor;
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
		List<Future<Outcome>> outcomes = new ArrayList<>();
		for (UUID postId : postIds) {
			outcomes.add(executor.submit(() -> assess(runId, postId, runStopped, stoppedBy)));
		}
		int ok = 0;
		int failed = 0;
		int dead = 0;
		List<ProcessingError> errors = new ArrayList<>();
		for (int i = 0; i < outcomes.size(); i++) {
			switch (await(runId, outcomes, i)) {
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
		return new AssessedBatch(runId, ok, failed, dead, errors, stoppedBy.get());
	}

	private Outcome assess(UUID runId, UUID postId, AtomicBoolean runStopped, AtomicReference<@Nullable String> stoppedBy) {
		if (runStopped.get()) {
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
		try {
			AssessmentInputV1 input = inputs.of(posts.findById(postId).orElseThrow());
			AssessmentResult result = assessor.assess(input);
			stored = transitions.record(postId, runId, attempts.get(), new Assessment(postId, runId, input, result));
			if (!stored) {
				// The run lost the post (interrupted, post put back): not this run's error.
				return new Outcome.NotTaken();
			}
			return new Outcome.Stored(result.ok(), !result.ok() && attempts.get() + 1 >= Post.MAX_ASSESSMENT_ATTEMPTS);
		}
		catch (LlmConfigurationException ex) {
			// Every later call would fail the same way: stop taking posts, count no attempt.
			runStopped.set(true);
			stoppedBy.compareAndSet(null, "the LLM provider refused the configuration: " + ex.getMessage());
			return new Outcome.NotTaken();
		}
		catch (RuntimeException ex) {
			log.error("Assessing post {} in run {} failed; the post is put back without an attempt", postId, runId, ex);
			return new Outcome.Error(ex.getClass().getSimpleName() + ": " + ex.getMessage());
		}
		finally {
			if (!stored) {
				transitions.release(postId, runId);
			}
		}
	}

	private static Outcome await(UUID runId, List<Future<Outcome>> outcomes, int index) {
		try {
			return outcomes.get(index).get();
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			outcomes.forEach(outcome -> outcome.cancel(true));
			throw new IllegalStateException("interrupted while assessing run " + runId, ex);
		}
		catch (ExecutionException | CancellationException ex) {
			// assess() catches what it can; this is a task the pool never ran.
			return new Outcome.Error(String.valueOf(ex));
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

	/** What happened to one post. */
	private sealed interface Outcome {

		/** An assessment was stored. */
		record Stored(boolean ok, boolean dead) implements Outcome {
		}

		/** A technical error: no assessment, the post put back. */
		record Error(String message) implements Outcome {
		}

		/** Not taken by this run (not assessable, the run stopped, or the post lost to recovery). */
		record NotTaken() implements Outcome {
		}

	}

}
