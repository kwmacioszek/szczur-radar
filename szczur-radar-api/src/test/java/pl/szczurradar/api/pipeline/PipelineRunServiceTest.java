package pl.szczurradar.api.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import com.embabel.agent.core.AgentPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import pl.szczurradar.api.llm.LlmProviderProperties;
import pl.szczurradar.api.llm.Provider;
import pl.szczurradar.api.notifications.RunNotifications;
import pl.szczurradar.api.pipeline.assess.AssessmentDispatcher;
import pl.szczurradar.api.pipeline.run.PipelineRun;
import pl.szczurradar.api.pipeline.run.PipelineRunRepository;
import pl.szczurradar.api.pipeline.state.PostTransitions;
import pl.szczurradar.api.pipeline.state.RunRecorder;
import pl.szczurradar.api.pipeline.step.SourceIngest;
import pl.szczurradar.api.posts.PostRepository;
import pl.szczurradar.api.sources.Source;

/**
 * {@link PipelineRunService}'s clean-up of orphaned runs in the cases {@code OrphanedRunIT} cannot stage
 * on a real database: a failing clean-up, a refusal before the queue is asked, and this instance's own run
 * (on a database its fresh activity alone keeps the queue alive). Every clean-up on a real database is in
 * {@code OrphanedRunIT}.
 */
@ExtendWith(OutputCaptureExtension.class)
class PipelineRunServiceTest {

	private final PipelineRunRepository runs = mock(PipelineRunRepository.class);

	private final RunRecorder recorder = mock(RunRecorder.class);

	private final PostTransitions transitions = mock(PostTransitions.class);

	private final SourceIngest sources = mock(SourceIngest.class);

	/** Holds a run of this instance in its queue thread until the test ends. */
	private final CountDownLatch held = new CountDownLatch(1);

	private final TransactionTemplate transaction = mock(TransactionTemplate.class);

	@SuppressWarnings("unchecked")
	private final ObjectProvider<PipelineStallSettings> stall = mock(ObjectProvider.class);

	private final PipelineRunService service = new PipelineRunService(runs, mock(PostRepository.class),
			sources, recorder, transitions, mock(RunNotifications.class),
			mock(AssessmentDispatcher.class), mock(AgentPlatform.class), mock(JdbcClient.class, RETURNS_DEEP_STUBS),
			transaction, stall, null);

	PipelineRunServiceTest() {
		when(stall.getObject()).thenReturn(PipelineStallSettings.from(
				new MockEnvironment().withProperty(PipelineStallSettings.NAME, "20m"),
				new LlmProviderProperties(Provider.GEMINI, "model", "key")));
		when(transaction.execute(any())).thenAnswer(call -> call.<TransactionCallback<?>>getArgument(0)
			.doInTransaction(null));
	}

	@AfterEach
	void release() {
		held.countDown();
	}

	@Test
	void aFailedCleanUpIsLoggedAndTheRequestIsStillDecided(CapturedOutput output) {
		when(recorder.interruptOrphans(any())).thenThrow(new CannotAcquireLockException("deadlock detected"));
		when(runs.existsActive()).thenReturn(true);

		assertThatThrownBy(() -> service.startPosts(List.of(UUID.randomUUID()), UUID.randomUUID()))
			.isInstanceOf(PipelineBusyException.class);

		verify(runs).existsActive();
		assertThat(output).contains("ERROR")
			.contains("The clean-up of orphaned pipeline runs failed; the request is decided without it")
			.contains("deadlock detected");
	}

	@Test
	void aSourceNotConfiguredIsRefusedAfterTheCleanUp() {
		when(sources.resolve(any())).thenThrow(new SourceNotConfiguredException(List.of(Source.WYKOP)));

		assertThatThrownBy(() -> service.startRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2), List.of(),
				UUID.randomUUID()))
			.isInstanceOf(SourceNotConfiguredException.class);

		verify(recorder).interruptOrphans(any());
		verify(transitions).releaseOrphans();
	}

	@Test
	void anInstanceWithARunOfItsOwnInterruptsNoOrphan() {
		// The first request queues a run this instance then holds in its queue thread.
		PipelineRun own = mock(PipelineRun.class);
		when(own.getId()).thenReturn(UUID.randomUUID());
		when(runs.saveAll(any())).thenReturn(List.of(own));
		when(recorder.start(any())).thenAnswer(call -> {
			held.await();
			return false;
		});
		service.startPosts(List.of(UUID.randomUUID()), UUID.randomUUID());
		assertThat(service.ownRuns()).containsExactly(own.getId());

		when(runs.existsActive()).thenReturn(true);
		assertThatThrownBy(() -> service.startPosts(List.of(UUID.randomUUID()), UUID.randomUUID()))
			.isInstanceOf(PipelineBusyException.class);

		// Only the first request, with no run of its own, asked for orphans; both put posts back.
		verify(recorder, times(1)).interruptOrphans(any());
		verify(transitions, times(2)).releaseOrphans();
	}

}
