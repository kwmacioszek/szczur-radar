package pl.szczurradar.api.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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

import pl.szczurradar.api.alerts.AlertEvent;
import pl.szczurradar.api.alerts.OperatorAlertQueue;
import pl.szczurradar.api.llm.LlmProviderProperties;
import pl.szczurradar.api.llm.Provider;
import pl.szczurradar.api.notifications.RunNotifications;
import pl.szczurradar.api.pipeline.assess.AssessmentDispatcher;
import pl.szczurradar.api.pipeline.run.InterruptionCause;
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
 * {@code OrphanedRunIT}. And the operator alerts of its failing paths: the clean-up, a run's close and the
 * shutdown's interruption, each raised once to a mocked {@link OperatorAlertQueue}.
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

	private final RunNotifications notifications = mock(RunNotifications.class);

	private final OperatorAlertQueue alerts = mock(OperatorAlertQueue.class);

	@SuppressWarnings("unchecked")
	private final ObjectProvider<OperatorAlertQueue> alertQueue = mock(ObjectProvider.class);

	private final PipelineRunService service = new PipelineRunService(runs, mock(PostRepository.class),
			sources, recorder, transitions, notifications,
			mock(AssessmentDispatcher.class), mock(AgentPlatform.class), mock(JdbcClient.class, RETURNS_DEEP_STUBS),
			transaction, stall, alertQueue, null);

	PipelineRunServiceTest() {
		when(stall.getObject()).thenReturn(PipelineStallSettings.from(
				new MockEnvironment().withProperty(PipelineStallSettings.NAME, "20m"),
				new LlmProviderProperties(Provider.GEMINI, "model", "key")));
		when(transaction.execute(any())).thenAnswer(call -> call.<TransactionCallback<?>>getArgument(0)
			.doInTransaction(null));
		when(alertQueue.getIfAvailable()).thenReturn(alerts);
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
		// Raised once, with the exception's class only.
		verify(alerts).raise(eq(AlertEvent.ORPHAN_CLEANUP_FAILED), isNull(),
				argThat(detail -> detail.contains(CannotAcquireLockException.class.getName())
						&& detail.contains("PIPELINE_STALL_TIMEOUT") && !detail.contains("deadlock detected")));
		verify(alerts, times(1)).raise(any(), any(), any());
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

	@Test
	void aRunThatCannotBeClosedIsRaisedAndStillReported(CapturedOutput output) {
		UUID run = UUID.randomUUID();
		doThrow(new CannotAcquireLockException("the database is gone")).when(recorder).fail(eq(run), any());

		service.execute(run);

		assertThat(output).contains("ERROR", "Pipeline run " + run + " could not be closed");
		verify(alerts).raise(eq(AlertEvent.RUN_CLOSE_FAILED), eq(run),
				argThat(detail -> detail.contains(CannotAcquireLockException.class.getName())
						&& detail.contains("/admin/runs/" + run) && !detail.contains("the database is gone")));
		verify(alerts, times(1)).raise(any(), any(), any());
		verify(notifications).runEnded(run);
	}

	@Test
	void aFailedShutdownInterruptionIsRaisedAndTheQueueThreadIsStillStopped(CapturedOutput output)
			throws InterruptedException {
		// A run of this instance, held in its queue thread until that thread is interrupted.
		PipelineRun own = mock(PipelineRun.class);
		UUID ownId = UUID.randomUUID();
		when(own.getId()).thenReturn(ownId);
		when(runs.saveAll(any())).thenReturn(List.of(own));
		when(recorder.interrupt(List.of(ownId), InterruptionCause.SHUTDOWN))
			.thenThrow(new CannotAcquireLockException("the database is gone"));
		CountDownLatch holding = new CountDownLatch(1);
		CountDownLatch stopped = new CountDownLatch(1);
		when(recorder.start(any())).thenAnswer(call -> {
			holding.countDown();
			try {
				held.await();
			}
			catch (InterruptedException ex) {
				stopped.countDown();
			}
			return false;
		});
		service.startPosts(List.of(UUID.randomUUID()), UUID.randomUUID());
		assertThat(holding.await(10, TimeUnit.SECONDS)).as("the run is held in the queue thread").isTrue();

		service.onClose();

		assertThat(stopped.await(10, TimeUnit.SECONDS)).as("the queue thread was interrupted").isTrue();
		assertThat(output).contains("ERROR", "could not be interrupted on shutdown", ownId.toString());
		verify(alerts).raise(eq(AlertEvent.RUN_CLOSE_FAILED), isNull(),
				argThat(detail -> detail.contains(ownId.toString())
						&& detail.contains(CannotAcquireLockException.class.getName())));
		verify(alerts, times(1)).raise(any(), any(), any());
	}

}
