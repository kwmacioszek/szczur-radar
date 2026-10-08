package pl.szczurradar.api.pipeline.assess;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import pl.szczurradar.api.agents.assessor.AssessmentResult;
import pl.szczurradar.api.agents.assessor.Assessor;
import pl.szczurradar.api.agents.assessor.Preparation;
import pl.szczurradar.api.agents.assessor.PreparedRequest;
import pl.szczurradar.api.agents.assessor.contract.AssessmentInputV1;
import pl.szczurradar.api.llm.LlmCallException;
import pl.szczurradar.api.llm.LlmClient;
import pl.szczurradar.api.llm.LlmResponse;
import pl.szczurradar.api.pipeline.PostTransitions;
import pl.szczurradar.api.pipeline.RunFlow;
import pl.szczurradar.api.pipeline.RunRecorder;
import pl.szczurradar.api.posts.Assessment;
import pl.szczurradar.api.posts.Post;
import pl.szczurradar.api.posts.PostRepository;
import pl.szczurradar.api.raw.RawPayloadStore;
import pl.szczurradar.api.sources.RawInputMapper;
import pl.szczurradar.api.sources.Source;

/**
 * {@link AssessmentDispatcher} on its own executor of {@code PIPELINE_LLM_CONCURRENCY} threads
 * (default 4; never {@code availableProcessors()}, which a container may report as 1): {@code
 * Assessor.prepare}, one {@link LlmClient#complete}, {@code Assessor.evaluate}, per post.
 *
 * <p>A post is taken ({@code assessing}, owned by the run) only when a thread picks it up, never the
 * whole batch at once. When taking fails because the run is no longer running, the remaining posts
 * are left alone. A failed call becomes a result only through {@code Assessor.failed}. Any other
 * exception (a bug, a failed write) puts the post back without an attempt and is reported as a
 * technical error; the other posts go on.
 */
@Component
public class ConcurrentAssessmentDispatcher implements AssessmentDispatcher, DisposableBean {

	private static final Logger log = LoggerFactory.getLogger(ConcurrentAssessmentDispatcher.class);

	private final PostRepository posts;

	private final RawPayloadStore rawPayloads;

	private final Map<Source, RawInputMapper> inputMappers;

	private final Assessor assessor;

	private final LlmClient llmClient;

	private final PostTransitions transitions;

	private final RunRecorder recorder;

	private final ExecutorService executor;

	private final Clock clock;

	@Autowired
	ConcurrentAssessmentDispatcher(PostRepository posts, RawPayloadStore rawPayloads, List<RawInputMapper> inputMappers,
			Assessor assessor, @Lazy LlmClient llmClient, PostTransitions transitions, RunRecorder recorder,
			@Value("${PIPELINE_LLM_CONCURRENCY:4}") int concurrency) {
		this(posts, rawPayloads, inputMappers, assessor, llmClient, transitions, recorder, executor(concurrency),
				Clock.systemUTC());
	}

	ConcurrentAssessmentDispatcher(PostRepository posts, RawPayloadStore rawPayloads, List<RawInputMapper> inputMappers,
			Assessor assessor, LlmClient llmClient, PostTransitions transitions, RunRecorder recorder,
			ExecutorService executor, Clock clock) {
		this.posts = posts;
		this.rawPayloads = rawPayloads;
		this.inputMappers = inputMappers.stream()
			.collect(Collectors.toUnmodifiableMap(RawInputMapper::source, Function.identity()));
		this.assessor = assessor;
		this.llmClient = llmClient;
		this.transitions = transitions;
		this.recorder = recorder;
		this.executor = executor;
		this.clock = clock;
	}

	private static ExecutorService executor(int concurrency) {
		if (concurrency < 1) {
			throw new IllegalStateException("PIPELINE_LLM_CONCURRENCY must be at least 1 (got " + concurrency + ")");
		}
		AtomicInteger thread = new AtomicInteger();
		return Executors.newFixedThreadPool(concurrency,
				task -> Thread.ofPlatform().name("pipeline-llm-" + thread.incrementAndGet()).unstarted(task));
	}

	@Override
	public RunFlow.AssessedBatch dispatch(UUID runId, List<UUID> postIds) {
		AtomicBoolean runStopped = new AtomicBoolean();
		List<Future<Result>> futures = new ArrayList<>();
		for (UUID postId : postIds) {
			futures.add(executor.submit(() -> assess(runId, postId, runStopped)));
		}
		int ok = 0;
		int failed = 0;
		int dead = 0;
		List<RunFlow.ProcessingError> errors = new ArrayList<>();
		for (int i = 0; i < futures.size(); i++) {
			Result result;
			try {
				result = futures.get(i).get();
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				futures.forEach(future -> future.cancel(true));
				throw new IllegalStateException("interrupted while assessing run " + runId, ex);
			}
			catch (ExecutionException | CancellationException ex) {
				// assess() catches everything it can; this is a task the executor never ran.
				result = new Result.Error(String.valueOf(ex));
			}
			switch (result) {
				case Result.Stored stored when stored.ok() -> ok++;
				case Result.Stored stored -> {
					failed++;
					dead += stored.dead() ? 1 : 0;
				}
				case Result.Error error -> errors.add(new RunFlow.ProcessingError(postIds.get(i), error.message()));
				case Result.NotTaken notTaken -> {
				}
			}
		}
		return new RunFlow.AssessedBatch(runId, ok, failed, dead, errors);
	}

	private Result assess(UUID runId, UUID postId, AtomicBoolean runStopped) {
		if (runStopped.get()) {
			return new Result.NotTaken();
		}
		Optional<Integer> attempts = transitions.claim(postId, runId);
		if (attempts.isEmpty()) {
			if (!recorder.isRunning(runId)) {
				runStopped.set(true);
			}
			return new Result.NotTaken();
		}
		boolean recorded = false;
		try {
			Post post = posts.findById(postId).orElseThrow();
			AssessmentInputV1 input = inputMapper(post.getSource()).map(rawPayloads.load(post.getRawPayload()));
			AssessmentResult result = switch (assessor.prepare(input)) {
				case Preparation.Decided decided -> decided.result();
				case Preparation.Prepared prepared -> call(input, prepared.request());
			};
			Assessment assessment = new Assessment(postId, runId, input, result);
			recorded = transitions.record(postId, runId, attempts.get(), assessment);
			if (!recorded) {
				// The run lost the post (interrupted, the post put back): not this run's error.
				return new Result.NotTaken();
			}
			boolean ok = result.ok();
			return new Result.Stored(ok, !ok && attempts.get() + 1 >= Post.MAX_ASSESSMENT_ATTEMPTS);
		}
		catch (RuntimeException ex) {
			log.error("Assessing post {} in run {} failed; the post is put back without an attempt", postId, runId, ex);
			return new Result.Error(ex.getClass().getSimpleName() + ": " + ex.getMessage());
		}
		finally {
			if (!recorded) {
				transitions.release(postId, runId);
			}
		}
	}

	private AssessmentResult call(AssessmentInputV1 input, PreparedRequest request) {
		Instant start = clock.instant();
		LlmResponse response;
		try {
			response = llmClient.complete(request.request());
		}
		catch (LlmCallException ex) {
			return assessor.failed(request, ex, Duration.between(start, clock.instant()));
		}
		Instant end = clock.instant();
		return assessor.evaluate(input, request, response, Duration.between(start, end), end);
	}

	private RawInputMapper inputMapper(Source source) {
		RawInputMapper mapper = inputMappers.get(source);
		if (mapper == null) {
			throw new IllegalStateException("no RawInputMapper bean for source " + source.value());
		}
		return mapper;
	}

	/** Stops the threads when the context closes; an assessment in flight is interrupted. */
	@Override
	public void destroy() {
		executor.shutdownNow();
	}

	/** What happened to one post. */
	private sealed interface Result {

		/** An assessment was stored. */
		record Stored(boolean ok, boolean dead) implements Result {
		}

		/** A technical error; no assessment, the post put back. */
		record Error(String message) implements Result {
		}

		/** Not taken by this run (not assessable, run stopped, or lost to recovery). */
		record NotTaken() implements Result {
		}

	}

}
