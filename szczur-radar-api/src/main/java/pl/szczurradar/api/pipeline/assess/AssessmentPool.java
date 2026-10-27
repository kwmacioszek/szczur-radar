package pl.szczurradar.api.pipeline.assess;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The LLM threads of the assess step: {@code PIPELINE_LLM_CONCURRENCY} of them (default 4), named
 * {@code pipeline-llm-N}, one pool per context, shut down with it (an assessment in flight is interrupted).
 * {@link ConcurrentAssessmentDispatcher} gets the pool passed in (code that runs work in parallel takes its
 * executor from outside), and the integration tests read {@link #idle()} to know when a run's post tasks,
 * which may outlive the run's end, are over. Its own type, not an {@code Executor} bean, so Spring Boot's
 * own task executor stays as it is.
 */
@Component
public class AssessmentPool implements DisposableBean {

	private final ThreadPoolExecutor executor;

	AssessmentPool(@Value("${PIPELINE_LLM_CONCURRENCY:4}") int concurrency) {
		if (concurrency < 1) {
			throw new IllegalStateException("PIPELINE_LLM_CONCURRENCY must be at least 1 (got " + concurrency + ")");
		}
		AtomicInteger thread = new AtomicInteger();
		// As Executors.newFixedThreadPool, typed as the pool it is.
		this.executor = new ThreadPoolExecutor(concurrency, concurrency, 0L, TimeUnit.MILLISECONDS,
				new LinkedBlockingQueue<>(),
				task -> Thread.ofPlatform().name("pipeline-llm-" + thread.incrementAndGet()).unstarted(task));
	}

	/** The pool the dispatcher submits its post tasks to. */
	ExecutorService executor() {
		return executor;
	}

	/** Tasks in progress. */
	public int activeTasks() {
		return executor.getActiveCount();
	}

	/** Tasks waiting for a thread. */
	public int queuedTasks() {
		return executor.getQueue().size();
	}

	/** No task in progress or waiting. */
	public boolean idle() {
		return activeTasks() == 0 && queuedTasks() == 0;
	}

	/** Stops the threads when the context closes; an assessment in flight is interrupted. */
	@Override
	public void destroy() {
		executor.shutdownNow();
	}

}
