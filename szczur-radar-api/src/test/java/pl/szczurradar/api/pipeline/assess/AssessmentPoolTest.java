package pl.szczurradar.api.pipeline.assess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * {@link AssessmentPool} without Spring: its size, its threads' names, and what {@link AssessmentPool#idle()}
 * (which the integration tests wait on) counts. The tests wait for events (latches), never for a time of their own.
 */
@Timeout(60)
class AssessmentPoolTest {

	private AssessmentPool pool = new AssessmentPool(1);

	/** Lets the tasks that wait on it end. */
	private final CountDownLatch release = new CountDownLatch(1);

	@AfterEach
	void stop() {
		release.countDown();
		pool.destroy();
	}

	@Test
	void aConcurrencyBelowOneIsRefused() {
		assertThatThrownBy(() -> new AssessmentPool(0)).isInstanceOf(IllegalStateException.class)
			.hasMessage("PIPELINE_LLM_CONCURRENCY must be at least 1 (got 0)");
	}

	@Test
	void itsThreadsAreNamedForTheLog() throws Exception {
		pool.destroy();
		pool = new AssessmentPool(2);

		Future<String> name = pool.executor().submit(() -> Thread.currentThread().getName());

		assertThat(name.get()).isEqualTo("pipeline-llm-1");
	}

	@Test
	void aNewPoolIsIdle() {
		assertThat(pool.activeTasks()).isZero();
		assertThat(pool.queuedTasks()).isZero();
		assertThat(pool.idle()).isTrue();
	}

	@Test
	void aTaskInProgressAndOneWaitingForTheThreadAreNotIdle() throws Exception {
		CountDownLatch started = new CountDownLatch(1);
		ExecutorService executor = pool.executor();
		executor.submit(() -> {
			started.countDown();
			release.await();
			return null;
		});
		started.await();
		executor.submit(() -> null);

		assertThat(pool.activeTasks()).isOne();
		assertThat(pool.queuedTasks()).isOne();
		assertThat(pool.idle()).isFalse();

		release.countDown();
		executor.shutdown();
		assertThat(executor.awaitTermination(1, TimeUnit.DAYS)).isTrue();
		assertThat(pool.idle()).isTrue();
	}

	@Test
	void aTaskInProgressAloneIsNotIdle() throws Exception {
		CountDownLatch started = new CountDownLatch(1);
		pool.executor().submit(() -> {
			started.countDown();
			release.await();
			return null;
		});
		started.await();

		assertThat(pool.queuedTasks()).isZero();
		assertThat(pool.idle()).isFalse();
	}

	@Test
	void aTaskWaitingWithNoThreadAtWorkIsNotIdle() {
		// No task has started a thread yet: one put straight in the queue waits with none in progress, as one does
		// between a thread's two tasks.
		((ThreadPoolExecutor) pool.executor()).getQueue().add(() -> {
		});

		assertThat(pool.activeTasks()).isZero();
		assertThat(pool.queuedTasks()).isOne();
		assertThat(pool.idle()).isFalse();
	}

}
