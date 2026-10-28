package pl.szczurradar.api.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
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
import pl.szczurradar.api.pipeline.flow.RunFlow.RunSummary;
import pl.szczurradar.api.pipeline.flow.RunSequence;
import pl.szczurradar.api.pipeline.run.InterruptionCause;
import pl.szczurradar.api.pipeline.run.PipelineRun;
import pl.szczurradar.api.pipeline.run.PipelineRunRepository;
import pl.szczurradar.api.pipeline.run.RunKind;
import pl.szczurradar.api.pipeline.run.RunStatus;
import pl.szczurradar.api.pipeline.run.RunStep;
import pl.szczurradar.api.pipeline.state.PostTransitions;
import pl.szczurradar.api.pipeline.state.RunNoLongerRunningException;
import pl.szczurradar.api.pipeline.state.RunRecorder;
import pl.szczurradar.api.pipeline.step.SourceIngest;
import pl.szczurradar.api.posts.PostRepository;
import pl.szczurradar.api.sources.Source;

/**
 * {@link PipelineRunService}'s clean-up of orphaned runs in the cases {@code OrphanedRunIT} cannot stage
 * on a real database: a failing clean-up, a refusal before the queue is asked, and this instance's own run
 * (on a database its fresh activity alone keeps the queue alive). Every clean-up on a real database is in
 * {@code OrphanedRunIT}. The operator alerts of its failing paths: the clean-up, a run's close and the
 * shutdown's interruption, each raised once to a mocked {@link OperatorAlertQueue}. And how a run ends
 * on its queue thread with a mocked {@link RunSequence} — its own failure an ERROR, a run someone else
 * ended or a stopping instance an INFO line, an {@link Error} logged although its task keeps it, a close
 * the watchdog's late interrupt cut short an INFO line, as is a step's own failure when that interrupt cuts
 * short the check of its run (read once more with the flag cleared; a second failed read is still the
 * step's ERROR), and an interrupt the notification restores not kept for the next run, unless the instance
 * began to stop meanwhile — the stopping instance known from the start of
 * {@code onClose}, which reports every run of its own; and the watchdog's stop, its writes first, then the
 * interrupt of the run's thread and of its post tasks, then the notification. The tests wait for events
 * (a latch a mock counts down, or the queue thread's own state polled), never for a time of their own: a
 * test that hangs ends at its {@link Timeout}, with a dump of every thread in the log
 * ({@code junit-platform.properties}).
 */
@ExtendWith(OutputCaptureExtension.class)
@Timeout(60)
class PipelineRunServiceTest {

	/** How often a test polls what no mock can signal (milliseconds); the class's timeout bounds the polling. */
	private static final long POLL = 10;

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

	private final AssessmentDispatcher dispatcher = mock(AssessmentDispatcher.class);

	private final RunSequence sequence = mock(RunSequence.class);

	private final OperatorAlertQueue alerts = mock(OperatorAlertQueue.class);

	@SuppressWarnings("unchecked")
	private final ObjectProvider<OperatorAlertQueue> alertQueue = mock(ObjectProvider.class);

	private final PipelineRunService service = new PipelineRunService(runs, mock(PostRepository.class),
			sources, recorder, transitions, notifications,
			dispatcher, sequence, mock(JdbcClient.class, RETURNS_DEEP_STUBS),
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
		service.onClose();
		// A test that calls execute() on its own thread may leave the flag set for the next one.
		Thread.interrupted();
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
		// Nobody else ended it: the close's failure leaves it running.
		when(recorder.isRunning(run)).thenReturn(true);

		service.execute(run);

		verify(recorder).isRunning(run);
		assertThat(output).contains("ERROR", "Pipeline run " + run + " could not be closed");
		verify(alerts).raise(eq(AlertEvent.RUN_CLOSE_FAILED), eq(run),
				argThat(detail -> detail.contains(CannotAcquireLockException.class.getName())
						&& detail.contains("/admin/runs/" + run) && !detail.contains("the database is gone")));
		verify(alerts, times(1)).raise(any(), any(), any());
		verify(notifications).runEnded(run);
	}

	@Test
	void aCloseFailureWhoseRunCannotBeReadIsStillRaised(CapturedOutput output) {
		UUID run = UUID.randomUUID();
		doThrow(new CannotAcquireLockException("the database is gone")).when(recorder).fail(eq(run), any());
		when(recorder.isRunning(run)).thenThrow(new CannotAcquireLockException("still gone"));

		service.execute(run);

		assertThat(errors(output, run)).singleElement()
			.satisfies(line -> assertThat(line).contains("Pipeline run " + run + " could not be closed"));
		verify(alerts).raise(eq(AlertEvent.RUN_CLOSE_FAILED), eq(run), any());
	}

	@Test
	void aLateInterruptOfTheWatchdogCuttingTheCloseShortIsAnInfoWithoutAnAlert(CapturedOutput output) {
		UUID run = started(RunKind.RETRY, RunStatus.FAILED);
		// The stalled step ended by itself while stopStalled was committing: its cancel(true) lands in the close,
		// and the connection pool refuses the interrupted thread.
		when(sequence.run(any())).thenReturn(new RunSummary(run, RunStatus.SUCCEEDED));
		doAnswer(call -> {
			Thread.currentThread().interrupt();
			throw new DataAccessResourceFailureException("Interrupted during connection acquisition");
		}).when(recorder).fail(eq(run), any());
		AtomicBoolean checkedInterrupted = new AtomicBoolean(true);
		when(recorder.isRunning(run)).thenAnswer(call -> {
			checkedInterrupted.set(Thread.currentThread().isInterrupted());
			// The watchdog ended it failed.
			return false;
		});

		service.execute(run);

		assertThat(checkedInterrupted).as("the check gets its connection: the flag is cleared first").isFalse();
		assertThat(output.getAll()).contains("Pipeline run " + run + " had already ended (its summary, the watchdog or a newer instance); its clean-up here"
				+ " was cut short: " + DataAccessResourceFailureException.class.getName()
				+ ": Interrupted during connection acquisition");
		assertThat(errors(output, run)).isEmpty();
		verify(alerts, never()).raise(any(), any(), any());
		verify(notifications).runEnded(run);
		assertThat(Thread.interrupted()).as("the watchdog's interrupt is not kept for the next run").isFalse();
	}

	@Test
	void aLateInterruptOfTheWatchdogCuttingTheStepsCheckShortIsAnInfoWithoutAnAlert(CapturedOutput output) {
		UUID run = started(RunKind.RETRY, RunStatus.FAILED);
		// The stalled step ended by itself with its own exception while stopStalled was committing: its
		// cancel(true) lands in the check whether the run is still running, after the catch cleared the flag,
		// and the connection pool refuses the interrupted thread.
		when(sequence.run(any())).thenThrow(new IllegalStateException("the mapper broke"));
		AtomicBoolean checkedInterrupted = new AtomicBoolean(true);
		when(recorder.isRunning(run)).thenAnswer(call -> {
			Thread.currentThread().interrupt();
			throw new DataAccessResourceFailureException("Interrupted during connection acquisition");
		}).thenAnswer(call -> {
			checkedInterrupted.set(Thread.currentThread().isInterrupted());
			// The watchdog ended it failed.
			return false;
		});

		service.execute(run);

		assertThat(checkedInterrupted).as("the second read gets its connection: the flag is cleared first").isFalse();
		verify(recorder, times(2)).isRunning(run);
		assertThat(output.getAll()).contains("Pipeline run " + run
				+ " ended by the watchdog or an interruption; its step ended with IllegalStateException: the mapper broke");
		assertThat(errors(output, run)).isEmpty();
		// Closed with the default error, not the step's: the watchdog wrote the run's end.
		verify(recorder).fail(run, "the run ended without a summary");
		verify(recorder, never()).fail(run, "IllegalStateException: the mapper broke");
		verify(alerts, never()).raise(any(), any(), any());
		verify(notifications).runEnded(run);
		assertThat(Thread.interrupted()).as("the watchdog's interrupt is not kept for the next run").isFalse();
	}

	@Test
	void anInterruptTheNotificationRestoresIsNotKeptForTheNextRun() {
		UUID run = started(RunKind.RETRY, RunStatus.FAILED);
		when(sequence.run(any())).thenReturn(new RunSummary(run, RunStatus.SUCCEEDED));
		// The watchdog's late cancel(true) landed in the notification, which restores it after its second attempt.
		AtomicBoolean notifiedInterrupted = new AtomicBoolean(true);
		doAnswer(call -> {
			notifiedInterrupted.set(Thread.currentThread().isInterrupted());
			Thread.currentThread().interrupt();
			return null;
		}).when(notifications).runEnded(run);

		service.execute(run);

		assertThat(notifiedInterrupted).as("the notification starts with the flag cleared").isFalse();
		assertThat(Thread.interrupted()).as("the watchdog's interrupt is not kept for the next run").isFalse();
	}

	@Test
	void anInterruptTheNotificationRestoresIsKeptWhenTheInstanceBeganToStopMeanwhile() {
		UUID run = started(RunKind.RETRY, RunStatus.FAILED);
		when(sequence.run(any())).thenReturn(new RunSummary(run, RunStatus.SUCCEEDED));
		// onClose began while the run was being reported (its shutdownNow() lands in the notification, which
		// restores it): the queue thread is stopping too, so the interrupt stays for it.
		doAnswer(call -> {
			service.onClose();
			Thread.currentThread().interrupt();
			return null;
		}).when(notifications).runEnded(run);

		service.execute(run);

		assertThat(Thread.interrupted()).as("the stop's interrupt is kept for the stopping queue thread").isTrue();
	}

	@Test
	void aStepsCheckThatFailsTwiceOnAnInterruptedThreadIsStillTheStepsError(CapturedOutput output) {
		UUID run = started(RunKind.RETRY, RunStatus.FAILED);
		when(sequence.run(any())).thenThrow(new IllegalStateException("the mapper broke"));
		// The interrupt lands in the check, and the second read fails too (the database is gone): the run is
		// taken as running, so the failure is the step's own.
		when(recorder.isRunning(run)).thenAnswer(call -> {
			Thread.currentThread().interrupt();
			throw new DataAccessResourceFailureException("Interrupted during connection acquisition");
		}).thenThrow(new CannotAcquireLockException("the database is gone"));

		service.execute(run);

		verify(recorder, times(2)).isRunning(run);
		assertThat(errors(output, run)).singleElement()
			.satisfies(line -> assertThat(line).contains("Pipeline run " + run + " failed"));
		// Both reads' failures are kept with the step's exception.
		assertThat(output.getAll()).contains("Interrupted during connection acquisition", "the database is gone");
		verify(recorder).fail(run, "IllegalStateException: the mapper broke");
		assertThat(Thread.interrupted()).as("the watchdog's interrupt is not kept for the next run").isFalse();
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
		// The run is held in the queue thread.
		holding.await();

		service.onClose();

		// The queue thread was interrupted.
		stopped.await();
		assertThat(output).contains("ERROR", "could not be interrupted on shutdown", ownId.toString());
		verify(alerts).raise(eq(AlertEvent.RUN_CLOSE_FAILED), isNull(),
				argThat(detail -> detail.contains(ownId.toString())
						&& detail.contains(CannotAcquireLockException.class.getName())));
		verify(alerts, times(1)).raise(any(), any(), any());
	}


	@Test
	void aRunThatEndsWithItsSummaryLogsNoError(CapturedOutput output) {
		UUID run = started(RunKind.POSTS, RunStatus.SUCCEEDED);
		when(sequence.run(any())).thenReturn(new RunSummary(run, RunStatus.SUCCEEDED));

		service.execute(run);

		verify(sequence).run(any());
		// The close changes nothing on a run its summary ended: its failure is conditional on queued/running.
		verify(recorder).fail(run, "the run ended without a summary");
		verify(transitions).releaseAll(run);
		verify(notifications).runEnded(run);
		assertThat(output.getAll()).contains("Pipeline run " + run + " (posts) ended succeeded in");
		assertThat(errors(output, run)).isEmpty();
	}

	@Test
	void aStepsOwnExceptionFailsTheRunWithItsClassAndMessage(CapturedOutput output) {
		UUID run = started(RunKind.RANGE, RunStatus.FAILED);
		when(sequence.run(any())).thenThrow(new IllegalStateException("the mapper broke"));
		when(recorder.isRunning(run)).thenReturn(true);

		service.execute(run);

		verify(recorder).fail(run, "IllegalStateException: the mapper broke");
		verify(notifications).runEnded(run);
		assertThat(errors(output, run)).singleElement()
			.satisfies(line -> assertThat(line).contains("Pipeline run " + run + " failed"));
	}

	@Test
	void aStepEndingAfterSomeoneElseEndedItsRunIsAnInfoNotAFailure(CapturedOutput output) {
		UUID run = started(RunKind.RETRY, RunStatus.FAILED);
		when(sequence.run(any())).thenThrow(new IllegalStateException("interrupted while assessing run " + run));
		when(recorder.isRunning(run)).thenReturn(false);

		service.execute(run);

		assertThat(output.getAll()).contains("Pipeline run " + run
				+ " ended by the watchdog or an interruption; its step ended with IllegalStateException:"
				+ " interrupted while assessing run " + run);
		assertThat(errors(output, run)).isEmpty();
		// Not failed with the step's exception: whoever ended the run wrote its end.
		verify(recorder, never()).fail(run, "IllegalStateException: interrupted while assessing run " + run);
		verify(notifications).runEnded(run);
	}

	@Test
	void aRunTheInstanceStopsIsNeitherClosedNorReportedAndTheInterruptStays(CapturedOutput output) {
		assertStoppingInstance(new IllegalStateException("interrupted while assessing"), output);
	}

	@Test
	void aStepRefusedWhileTheInstanceStopsIsTheStopToo(CapturedOutput output) {
		// onClose interrupts the run first: its next step may be refused before the thread's interrupt comes.
		assertStoppingInstance(new RunNoLongerRunningException(UUID.randomUUID(), RunStep.ASSESS), output);
	}

	@Test
	void aLateInterruptOfTheWatchdogDoesNotReachTheCloseAndIsNotKept(CapturedOutput output) {
		UUID run = started(RunKind.RETRY, RunStatus.FAILED);
		// The watchdog's cancel(true) landing after the step returned (or after the catch cleared the flag).
		when(sequence.run(any())).thenAnswer(call -> {
			Thread.currentThread().interrupt();
			return new RunSummary(run, RunStatus.INTERRUPTED);
		});

		service.execute(run);

		assertThat(Thread.interrupted()).as("the flag is cleared for the next run").isFalse();
		verify(recorder).fail(run, "the run ended without a summary");
		verify(notifications).runEnded(run);
		assertThat(errors(output, run)).isEmpty();
	}

	@Test
	void aLateInterruptOfTheStoppingInstanceIsRestoredAndTheRunLeftToOnClose(CapturedOutput output) {
		UUID run = started(RunKind.RETRY, RunStatus.SUCCEEDED);
		// onClose's shutdownNow() landing after the summary: the run ended by itself, and onClose reports it
		// with every run of its own (onCloseReportsARunOfItsOwnThatHadAlreadyEnded); this one is not queued here.
		when(sequence.run(any())).thenAnswer(call -> {
			service.onClose();
			Thread.currentThread().interrupt();
			return new RunSummary(run, RunStatus.SUCCEEDED);
		});

		service.execute(run);

		assertThat(Thread.interrupted()).as("the interrupt flag is restored").isTrue();
		verify(recorder, never()).fail(any(), any());
		verify(notifications, never()).runEnded(run);
		assertThat(errors(output, run)).isEmpty();
	}

	@Test
	void aRunEndingWhileOnCloseStillWritesIsTheStopNotAFailure(CapturedOutput output) throws InterruptedException {
		// A run of this instance whose step starts only once onClose began: the instance is stopping, but its
		// executor is not shut down yet (that is onClose's last statement).
		PipelineRun own = mock(PipelineRun.class);
		UUID run = UUID.randomUUID();
		when(own.getId()).thenReturn(run);
		when(own.getKind()).thenReturn(RunKind.POSTS);
		when(own.getSources()).thenReturn(List.of());
		when(runs.saveAll(any())).thenReturn(List.of(own));
		when(runs.findById(run)).thenReturn(Optional.of(own));
		CountDownLatch closing = new CountDownLatch(1);
		CountDownLatch written = new CountDownLatch(1);
		when(recorder.start(run)).thenAnswer(call -> {
			closing.await();
			return true;
		});
		// onClose's interruption is written: the run's next step is refused.
		when(sequence.run(any())).thenThrow(new RunNoLongerRunningException(run, RunStep.ASSESS));
		when(recorder.interrupt(List.of(run), InterruptionCause.SHUTDOWN)).thenAnswer(call -> {
			closing.countDown();
			// Held until the queue thread has finished the run (the notifications' round trip, in production).
			written.await();
			return List.of(run);
		});
		service.startPosts(List.of(UUID.randomUUID()), UUID.randomUUID());

		Thread closer = Thread.ofPlatform().start(service::onClose);
		try {
			awaitNoRunOfItsOwn();

			// The queue thread neither closed nor reported it: no false ERROR, no RUN_CLOSE_FAILED.
			assertThat(output.getAll()).contains("Pipeline run " + run + ": stopped, the application is stopping");
			assertThat(errors(output, run)).isEmpty();
			verify(recorder, never()).isRunning(any());
			verify(recorder, never()).fail(any(), any());
			verify(notifications, never()).runEnded(run);
		}
		finally {
			// On every path: a test cut short must not leave onClose held, here or in @AfterEach (whose own
			// onClose enters the same answer, outside the class's @Timeout).
			written.countDown();
		}
		closer.join();
		assertThat(closer.isAlive()).isFalse();
		// onClose reported it.
		verify(notifications, times(1)).runEnded(run);
		verify(transitions).releaseAll(run);
		verify(alerts, never()).raise(any(), any(), any());
	}

	@Test
	void onCloseReportsARunOfItsOwnThatHadAlreadyEnded() throws InterruptedException {
		// A run of this instance whose summary committed just before: no longer queued or running, so the
		// interruption changes nothing, but execute no longer reports it.
		PipelineRun own = mock(PipelineRun.class);
		UUID run = UUID.randomUUID();
		when(own.getId()).thenReturn(run);
		when(runs.saveAll(any())).thenReturn(List.of(own));
		CountDownLatch holding = new CountDownLatch(1);
		when(recorder.start(run)).thenAnswer(call -> {
			holding.countDown();
			try {
				held.await();
			}
			catch (InterruptedException ex) {
				// onClose's shutdownNow().
			}
			return false;
		});
		when(recorder.interrupt(List.of(run), InterruptionCause.SHUTDOWN)).thenReturn(List.of());
		service.startPosts(List.of(UUID.randomUUID()), UUID.randomUUID());
		// The run is held in the queue thread.
		holding.await();

		service.onClose();

		awaitNoRunOfItsOwn();
		verify(notifications, times(1)).runEnded(run);
		verify(transitions, never()).releaseAll(any());
		verify(recorder, never()).fail(any(), any());
	}

	@Test
	void aRunNoLongerRunningHereIsAnInfo(CapturedOutput output) {
		UUID run = started(RunKind.POSTS, RunStatus.INTERRUPTED);
		when(sequence.run(any())).thenThrow(new RunNoLongerRunningException(run, RunStep.SUMMARIZE));

		service.execute(run);

		assertThat(output.getAll()).contains("Pipeline run " + run
				+ " is no longer running here; its remaining steps were not started");
		assertThat(errors(output, run)).isEmpty();
		verify(recorder, never()).isRunning(run);
		verify(recorder).fail(run, "the run ended without a summary");
		verify(notifications).runEnded(run);
	}

	@Test
	void anErrorEscapingTheRunIsLoggedThoughItsTaskKeepsIt(CapturedOutput output) throws InterruptedException {
		PipelineRun queued = mock(PipelineRun.class);
		UUID run = UUID.randomUUID();
		when(queued.getId()).thenReturn(run);
		when(runs.saveAll(any())).thenReturn(List.of(queued));
		// The close catches only exceptions: an Error leaves execute().
		doThrow(new NoClassDefFoundError("pl/szczurradar/Missing")).when(recorder).fail(eq(run), any());

		service.startPosts(List.of(UUID.randomUUID()), UUID.randomUUID());

		// Until the crash is logged: the line comes after the last call any mock sees.
		while (!output.getAll().contains("Pipeline run " + run + " crashed its queue thread")) {
			Thread.sleep(POLL);
		}
		assertThat(errors(output, run)).singleElement()
			.satisfies(line -> assertThat(line).contains("crashed its queue thread"));
		assertThat(output.getAll()).contains("NoClassDefFoundError");
	}

	@Test
	void theWatchdogsStopWritesFirstThenInterruptsTheRunsThreadAndItsPostTasksThenNotifies()
			throws InterruptedException {
		List<String> events = new CopyOnWriteArrayList<>();
		CountDownLatch holding = new CountDownLatch(1);
		CountDownLatch interrupted = new CountDownLatch(1);
		// A run of this instance, held in its queue thread; after the interrupt it waits until the test ends.
		PipelineRun own = mock(PipelineRun.class);
		UUID run = UUID.randomUUID();
		when(own.getId()).thenReturn(run);
		when(runs.saveAll(any())).thenReturn(List.of(own));
		when(recorder.start(run)).thenAnswer(call -> {
			holding.countDown();
			try {
				held.await();
			}
			catch (InterruptedException ex) {
				events.add("queue thread interrupted");
				interrupted.countDown();
				// Until the test ends, whatever interrupts it then (onClose).
				while (held.getCount() > 0) {
					try {
						held.await();
					}
					catch (InterruptedException again) {
						// Waits on.
					}
				}
			}
			return false;
		});
		service.startPosts(List.of(UUID.randomUUID()), UUID.randomUUID());
		// The run is held in the queue thread.
		holding.await();
		Duration threshold = Duration.ofMinutes(20);
		when(recorder.stall(run, threshold)).thenAnswer(call -> events.add("stall"));
		doAnswer(call -> {
			events.add("releaseAll");
			return 1;
		}).when(transitions).releaseAll(run);
		doAnswer(call -> events.add("runEnded")).when(notifications).runEnded(run);
		doAnswer(call -> {
			// Waits for the queue thread's interrupt, sent before this call: when it comes later, it is still
			// recorded first, and when it never comes the test times out.
			interrupted.await();
			return events.add("dispatcher.cancel");
		}).when(dispatcher).cancel(run);

		assertThat(service.stopStalled(run, threshold)).isTrue();

		// The interrupts right after the commit, before the notification's round trip.
		assertThat(events).containsExactly("stall", "releaseAll", "queue thread interrupted", "dispatcher.cancel",
				"runEnded");
	}

	@Test
	void aRunTheWatchdogCannotStopIsLeftAlone() {
		UUID run = UUID.randomUUID();
		Duration threshold = Duration.ofMinutes(20);
		when(recorder.stall(run, threshold)).thenReturn(false);

		assertThat(service.stopStalled(run, threshold)).isFalse();

		verify(transitions, never()).releaseAll(any());
		verify(notifications, never()).runEnded(any());
		verify(dispatcher, never()).cancel(any());
	}

	/**
	 * Until the queue thread has finished every run of this instance: it removes a run last, after any call a
	 * mock sees, so the test polls for it.
	 */
	private void awaitNoRunOfItsOwn() throws InterruptedException {
		while (!service.ownRuns().isEmpty()) {
			Thread.sleep(POLL);
		}
	}

	/**
	 * {@code execute} on this thread after {@link PipelineRunService#onClose} stopped the queue: the step
	 * ends with {@code failure}, and the run is left as {@code onClose} left it, the interrupt flag set.
	 */
	private void assertStoppingInstance(RuntimeException failure, CapturedOutput output) {
		UUID run = started(RunKind.POSTS, RunStatus.INTERRUPTED);
		when(sequence.run(any())).thenThrow(failure);
		service.onClose();

		service.execute(run);

		assertThat(Thread.interrupted()).as("the interrupt flag is restored").isTrue();
		assertThat(output.getAll()).contains("Pipeline run " + run + ": stopped, the application is stopping");
		assertThat(errors(output, run)).isEmpty();
		verify(recorder, never()).isRunning(any());
		verify(recorder, never()).fail(any(), any());
		verify(notifications, never()).runEnded(run);
	}

	/**
	 * The ERROR lines about {@code run}: the captured output also holds what the queue threads of earlier
	 * tests logged.
	 */
	private static List<String> errors(CapturedOutput output, UUID run) {
		return output.getAll().lines().filter(line -> line.contains(" ERROR ") && line.contains(run.toString())).toList();
	}

	/** A queued run that {@code execute} starts, of {@code kind}, read back as {@code status} when it closes. */
	private UUID started(RunKind kind, RunStatus status) {
		UUID id = UUID.randomUUID();
		PipelineRun run = mock(PipelineRun.class);
		when(run.getId()).thenReturn(id);
		when(run.getKind()).thenReturn(kind);
		when(run.getStatus()).thenReturn(status);
		when(run.getSources()).thenReturn(List.of());
		when(runs.findById(id)).thenReturn(Optional.of(run));
		when(recorder.start(id)).thenReturn(true);
		return id;
	}

}
