package pl.szczurradar.api.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
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
import pl.szczurradar.api.posts.PostStage;
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
 * interrupt of the run's thread and of its post tasks, then the notification. The requests themselves: the
 * runs they queue (Warsaw days, leftovers first, the deployment), a dead post refused, the advisory lock first in
 * each transaction, the clean-up's warnings and reports; and every way a run ends on each of {@code execute}'s
 * three paths ({@link #endings()}). The tests wait for events
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

	private final PostRepository posts = mock(PostRepository.class);

	private final JdbcClient jdbc = mock(JdbcClient.class);

	private final JdbcClient.StatementSpec lockStatement = mock(JdbcClient.StatementSpec.class);

	@SuppressWarnings("unchecked")
	private final JdbcClient.MappedQuerySpec<Boolean> lockQuery = mock(JdbcClient.MappedQuerySpec.class);

	/** Each transaction the service opens and each advisory lock it takes, in order. */
	private final List<String> events = new CopyOnWriteArrayList<>();

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

	private final PipelineRunService service = service(null);

	PipelineRunServiceTest() {
		when(stall.getObject()).thenReturn(PipelineStallSettings.from(
				new MockEnvironment().withProperty(PipelineStallSettings.NAME, "20m"),
				new LlmProviderProperties(Provider.GEMINI, "model", "key")));
		when(transaction.execute(any())).thenAnswer(call -> {
			events.add("transaction");
			return call.<TransactionCallback<?>>getArgument(0).doInTransaction(null);
		});
		when(alertQueue.getIfAvailable()).thenReturn(alerts);
		// Only the advisory lock with its key gets an answer.
		when(jdbc.sql("SELECT pg_advisory_xact_lock(:lock)")).thenReturn(lockStatement);
		when(lockStatement.param("lock", PipelineRunService.LOCK_ID)).thenReturn(lockStatement);
		doReturn(lockQuery).when(lockStatement).query(any(RowMapper.class));
		when(lockQuery.single()).thenAnswer(call -> events.add("lock"));
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

	@Test
	void theCleanUpAndTheRequestEachTakeTheAdvisoryLockFirstInATransactionOfTheirOwn() throws InterruptedException {
		when(recorder.interruptOrphans(any())).thenAnswer(call -> {
			events.add("interruptOrphans");
			return List.of();
		});
		when(transitions.releaseOrphans()).thenAnswer(call -> {
			events.add("releaseOrphans");
			return 0;
		});
		when(runs.existsActive()).thenAnswer(call -> {
			events.add("existsActive");
			return false;
		});
		when(runs.saveAll(any())).thenAnswer(call -> {
			events.add("saveAll");
			return withIds(call.getArgument(0));
		});

		service.startPosts(List.of(UUID.randomUUID()), UUID.randomUUID());

		assertThat(events).containsExactly("transaction", "lock", "interruptOrphans", "releaseOrphans", "transaction",
				"lock", "existsActive", "saveAll");
		awaitNoRunOfItsOwn();
	}

	@Test
	void aPostsRequestQueuesOneRunOfItsPostsAndReturnsIt() throws InterruptedException {
		List<UUID> postIds = List.of(UUID.randomUUID(), UUID.randomUUID());
		UUID admin = UUID.randomUUID();
		when(runs.saveAll(any())).thenAnswer(call -> withIds(call.getArgument(0)));
		CountDownLatch startedHere = new CountDownLatch(1);
		List<UUID> startedRuns = new CopyOnWriteArrayList<>();
		when(recorder.start(any())).thenAnswer(call -> {
			startedRuns.add(call.getArgument(0));
			startedHere.countDown();
			return false;
		});

		List<PipelineRun> created = service.startPosts(postIds, admin);

		assertThat(created).singleElement().satisfies(run -> {
			assertThat(run.getKind()).isEqualTo(RunKind.POSTS);
			assertThat(run.getRequestedPostIds()).isEqualTo(postIds);
			assertThat(run.getRequestedBy()).isEqualTo(admin);
		});
		startedHere.await();
		assertThat(startedRuns).containsExactly(created.getFirst().getId());
		awaitNoRunOfItsOwn();
	}

	@Test
	void aRequestWithADeadPostIsRefusedAndQueuesNothing() {
		UUID alive = UUID.randomUUID();
		UUID dead = UUID.randomUUID();
		when(posts.findIdsInStage(List.of(alive, dead), PostStage.DEAD)).thenReturn(List.of(dead));

		assertThatThrownBy(() -> service.startPosts(List.of(alive, dead), UUID.randomUUID()))
			.isInstanceOfSatisfying(PostDeadException.class,
					ex -> assertThat(ex.deadPostIds()).containsExactly(dead));

		verify(runs, never()).saveAll(any());
		assertThat(service.ownRuns()).isEmpty();
	}

	@Test
	void aRangeRequestWithLeftoversQueuesTheRetryRunFirstThenTheRangeOverWarsawDays() throws InterruptedException {
		UUID admin = UUID.randomUUID();
		when(sources.resolve(List.of())).thenReturn(List.of(Source.WYKOP));
		when(posts.existsLeftover()).thenReturn(true);
		when(runs.saveAll(any())).thenAnswer(call -> withIds(call.getArgument(0)));

		List<PipelineRun> created = service.startRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2), List.of(),
				admin);

		assertThat(created).extracting(PipelineRun::getKind).containsExactly(RunKind.RETRY, RunKind.RANGE);
		assertThat(created).extracting(PipelineRun::getRequestedBy).containsOnly(admin);
		PipelineRun range = created.getLast();
		// Warsaw is UTC+2 in October (CEST).
		assertThat(range.getRangeFrom()).isEqualTo(Instant.parse("2026-09-30T22:00:00Z"));
		assertThat(range.getRangeTo()).isEqualTo(Instant.parse("2026-10-02T21:59:59Z"));
		assertThat(range.getSources()).containsExactly(Source.WYKOP);
		awaitNoRunOfItsOwn();
	}

	@Test
	void aRangeRequestWithoutLeftoversQueuesOnlyTheRange() throws InterruptedException {
		when(sources.resolve(List.of(Source.REDDIT))).thenReturn(List.of(Source.REDDIT));
		when(runs.saveAll(any())).thenAnswer(call -> withIds(call.getArgument(0)));

		List<PipelineRun> created = service.startRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 1),
				List.of(Source.REDDIT), UUID.randomUUID());

		assertThat(created).singleElement().satisfies(range -> {
			assertThat(range.getKind()).isEqualTo(RunKind.RANGE);
			assertThat(range.getSources()).containsExactly(Source.REDDIT);
		});
		awaitNoRunOfItsOwn();
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = { "", "  " })
	void aRunQueuedWithoutADeploymentIdRemembersNone(String deploymentId) throws InterruptedException {
		assertThat(queuedBy(deploymentId).getDeploymentId()).isNull();
	}

	@Test
	void aRunRemembersTheDeploymentThatQueuedIt() throws InterruptedException {
		assertThat(queuedBy("deployment-7").getDeploymentId()).isEqualTo("deployment-7");
	}

	@Test
	void orphanedRunsAreWarnedAboutAndReportedAndEvenOnePutBackPostIsWarnedAbout(CapturedOutput output) {
		UUID first = UUID.randomUUID();
		UUID second = UUID.randomUUID();
		when(recorder.interruptOrphans(Duration.ofMinutes(20))).thenReturn(List.of(first, second));
		when(transitions.releaseOrphans()).thenReturn(1);
		when(runs.existsActive()).thenReturn(true);

		assertThatThrownBy(() -> service.startPosts(List.of(UUID.randomUUID()), UUID.randomUUID()))
			.isInstanceOf(PipelineBusyException.class);

		assertThat(output.getAll()).contains("Marked 2 orphaned pipeline run(s) interrupted",
				"Put back 1 post(s) left assessing by ended runs");
		verify(notifications).runEnded(first);
		verify(notifications).runEnded(second);
	}

	@Test
	void aCleanUpThatFindsNothingWarnsAboutNothingAndReportsNothing(CapturedOutput output) {
		when(runs.existsActive()).thenReturn(true);

		assertThatThrownBy(() -> service.startPosts(List.of(UUID.randomUUID()), UUID.randomUUID()))
			.isInstanceOf(PipelineBusyException.class);

		verify(transitions).releaseOrphans();
		assertThat(output.getAll()).doesNotContain("orphaned pipeline run").doesNotContain("Put back");
		verify(notifications, never()).runEnded(any());
	}

	@Test
	void aFailedShutdownInterruptionWithoutARunOfItsOwnRaisesNothing(CapturedOutput output) {
		when(recorder.interrupt(List.of(), InterruptionCause.SHUTDOWN))
			.thenThrow(new CannotAcquireLockException("the database is gone"));

		service.onClose();

		assertThat(output).contains("ERROR", "could not be interrupted on shutdown");
		verify(alerts, never()).raise(any(), any(), any());
	}

	@Test
	void theWatchdogStopsARunWhoseTaskIsNoLongerQueuedHere() {
		// The run ended its task here (or never had one) while the watchdog still saw it among the own runs.
		UUID run = UUID.randomUUID();
		Duration threshold = Duration.ofMinutes(20);
		when(recorder.stall(run, threshold)).thenReturn(true);

		assertThat(service.stopStalled(run, threshold)).isTrue();

		verify(transitions).releaseAll(run);
		verify(dispatcher).cancel(run);
		verify(notifications).runEnded(run);
	}

	@Test
	void aRunThatNeverStartedIsClosedWithoutAnEndLine(CapturedOutput output) {
		// Not queued any more when its turn came (e.g. interrupted meanwhile): its row is still there.
		UUID run = UUID.randomUUID();
		PipelineRun row = mock(PipelineRun.class);
		when(row.getKind()).thenReturn(RunKind.POSTS);
		when(row.getStatus()).thenReturn(RunStatus.INTERRUPTED);
		when(runs.findById(run)).thenReturn(Optional.of(row));

		service.execute(run);

		verify(sequence, never()).run(any());
		verify(recorder).fail(run, "the run ended without a summary");
		verify(notifications).runEnded(run);
		assertThat(output.getAll()).doesNotContain("Pipeline run " + run + " (")
			.doesNotContain("Pipeline run " + run + " had already ended");
		assertThat(errors(output, run)).isEmpty();
		verify(alerts, never()).raise(any(), any(), any());
	}

	@Test
	void aStepsCheckThatFailsOnAThreadNotInterruptedIsNotReadAgainAndItsFailureIsKept(CapturedOutput output) {
		UUID run = started(RunKind.RETRY, RunStatus.FAILED);
		when(sequence.run(any())).thenThrow(new IllegalStateException("the mapper broke"));
		// A second read would find the run ended; the failure of the first is no interrupt, so there is none.
		when(recorder.isRunning(run)).thenThrow(new CannotAcquireLockException("the run's state cannot be read"))
			.thenReturn(false);

		service.execute(run);

		verify(recorder, times(1)).isRunning(run);
		verify(recorder).fail(run, "IllegalStateException: the mapper broke");
		assertThat(errors(output, run)).singleElement()
			.satisfies(line -> assertThat(line).contains("Pipeline run " + run + " failed"));
		// Kept with the step's exception, so its stack trace shows it.
		assertThat(output.getAll()).contains("Suppressed: ", "the run's state cannot be read");
	}

	@Test
	void aStepsCheckWhoseSecondReadFindsTheRunRunningIsTheStepsError(CapturedOutput output) {
		UUID run = started(RunKind.RETRY, RunStatus.FAILED);
		when(sequence.run(any())).thenThrow(new IllegalStateException("the mapper broke"));
		when(recorder.isRunning(run)).thenAnswer(call -> {
			Thread.currentThread().interrupt();
			throw new DataAccessResourceFailureException("Interrupted during connection acquisition");
		}).thenReturn(true);

		service.execute(run);

		verify(recorder, times(2)).isRunning(run);
		verify(recorder).fail(run, "IllegalStateException: the mapper broke");
		assertThat(errors(output, run)).singleElement()
			.satisfies(line -> assertThat(line).contains("Pipeline run " + run + " failed"));
		assertThat(Thread.interrupted()).as("the interrupt is not kept for the next run").isFalse();
	}

	@ParameterizedTest(name = "second read fails: {0}")
	@ValueSource(booleans = { false, true })
	void anInterruptLandingInTheStepsCheckIsKeptWhenTheInstanceBeganToStopByTheSecondRead(boolean secondReadFails) {
		UUID run = started(RunKind.RETRY, RunStatus.FAILED);
		when(sequence.run(any())).thenThrow(new IllegalStateException("the mapper broke"));
		when(recorder.isRunning(run)).thenAnswer(call -> {
			Thread.currentThread().interrupt();
			throw new DataAccessResourceFailureException("Interrupted during connection acquisition");
		}).thenAnswer(call -> {
			service.onClose();
			if (secondReadFails) {
				throw new CannotAcquireLockException("the database is closing");
			}
			return true;
		});

		service.execute(run);

		assertThat(Thread.interrupted()).as("the check restores the interrupt for the stopping queue thread").isTrue();
		verify(recorder, never()).fail(any(), any());
		verify(notifications, never()).runEnded(run);
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("endings")
	void howARunEndsDecidesWhetherItIsClosedHereAndWhetherTheInterruptIsKept(Ends ends) {
		UUID run = started(RunKind.RETRY, RunStatus.FAILED);
		when(sequence.run(any())).thenAnswer(call -> {
			ends.duringStep().happen(service);
			return switch (ends.ending()) {
				case SUMMARY -> new RunSummary(run, RunStatus.SUCCEEDED);
				case FAILURE -> throw new IllegalStateException("the mapper broke");
				case ERROR -> throw new NoClassDefFoundError("pl/szczurradar/Missing");
			};
		});
		when(recorder.isRunning(run)).thenAnswer(call -> {
			ends.duringCheck().happen(service);
			return true;
		});
		doAnswer(call -> {
			ends.duringNotification().happen(service);
			return null;
		}).when(notifications).runEnded(run);

		if (ends.ending() == Ending.ERROR) {
			assertThatThrownBy(() -> service.execute(run)).isInstanceOf(NoClassDefFoundError.class);
		}
		else {
			service.execute(run);
		}

		boolean kept = Thread.interrupted();
		if (ends.closed()) {
			verify(recorder).fail(run, ends.ending() == Ending.FAILURE ? "IllegalStateException: the mapper broke"
					: "the run ended without a summary");
			verify(notifications).runEnded(run);
		}
		else {
			verify(recorder, never()).fail(any(), any());
			verify(notifications, never()).runEnded(run);
		}
		assertThat(kept).as("the interrupt flag after the run").isEqualTo(ends.interruptKept());
	}

	/**
	 * Every way {@code execute} ends, on each of its three paths (a summary, the step's own exception, an
	 * {@link Error}), with the stop of the instance or an interrupt landing in the step, in the check of the run's
	 * state or in the notification: whether the run is closed and reported here, and whether the thread keeps
	 * the interrupt (only when the instance is stopping).
	 */
	static Stream<Ends> endings() {
		return Stream.of(
				new Ends("a summary: closed here", Ending.SUMMARY, Event.NONE, Event.NONE, Event.NONE, true, false),
				new Ends("a summary as the instance stops: left to onClose", Ending.SUMMARY, Event.STOP, Event.NONE,
						Event.NONE, false, false),
				new Ends("a summary as the instance stops with its interrupt: left to onClose, interrupt kept",
						Ending.SUMMARY, Event.STOP_AND_INTERRUPT, Event.NONE, Event.NONE, false, true),
				new Ends("a summary with the watchdog's late interrupt: closed, interrupt cleared", Ending.SUMMARY,
						Event.INTERRUPT, Event.NONE, Event.NONE, true, false),
				new Ends("a summary whose notification gets an interrupt: cleared", Ending.SUMMARY, Event.NONE,
						Event.NONE, Event.INTERRUPT, true, false),
				new Ends("a summary whose notification sees the instance stop: interrupt kept", Ending.SUMMARY,
						Event.NONE, Event.NONE, Event.STOP_AND_INTERRUPT, true, true),
				new Ends("the step's own failure: closed with its error", Ending.FAILURE, Event.NONE, Event.NONE,
						Event.NONE, true, false),
				new Ends("a failure after the instance stopped: left to onClose, interrupt kept", Ending.FAILURE,
						Event.STOP, Event.NONE, Event.NONE, false, true),
				new Ends("a failure whose check sees the instance stop: left to onClose", Ending.FAILURE, Event.NONE,
						Event.STOP, Event.NONE, false, false),
				new Ends("a failure whose check sees the instance stop with its interrupt: interrupt kept",
						Ending.FAILURE, Event.NONE, Event.STOP_AND_INTERRUPT, Event.NONE, false, true),
				new Ends("a failure whose check gets the watchdog's late interrupt: closed, cleared", Ending.FAILURE,
						Event.NONE, Event.INTERRUPT, Event.NONE, true, false),
				new Ends("a failure whose notification gets an interrupt: cleared", Ending.FAILURE, Event.NONE,
						Event.NONE, Event.INTERRUPT, true, false),
				new Ends("a failure whose notification sees the instance stop: interrupt kept", Ending.FAILURE,
						Event.NONE, Event.NONE, Event.STOP_AND_INTERRUPT, true, true),
				new Ends("an Error: closed with the default error", Ending.ERROR, Event.NONE, Event.NONE, Event.NONE,
						true, false),
				new Ends("an Error as the instance stops: left to onClose", Ending.ERROR, Event.STOP, Event.NONE,
						Event.NONE, false, false),
				new Ends("an Error as the instance stops with its interrupt: left to onClose, interrupt kept",
						Ending.ERROR, Event.STOP_AND_INTERRUPT, Event.NONE, Event.NONE, false, true),
				new Ends("an Error with the watchdog's late interrupt: closed, interrupt cleared", Ending.ERROR,
						Event.INTERRUPT, Event.NONE, Event.NONE, true, false),
				new Ends("an Error whose notification gets an interrupt: cleared", Ending.ERROR, Event.NONE,
						Event.NONE, Event.INTERRUPT, true, false),
				new Ends("an Error whose notification sees the instance stop: interrupt kept", Ending.ERROR,
						Event.NONE, Event.NONE, Event.STOP_AND_INTERRUPT, true, true));
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

	/** A service of this test's mocks, in a deployment {@code deploymentId} (as {@code RAILWAY_DEPLOYMENT_ID}). */
	private PipelineRunService service(String deploymentId) {
		return new PipelineRunService(runs, posts, sources, recorder, transitions, notifications, dispatcher, sequence,
				jdbc, transaction, stall, alertQueue, deploymentId);
	}

	/** The run a posts request queues on a service in {@code deploymentId}. */
	private PipelineRun queuedBy(String deploymentId) throws InterruptedException {
		when(runs.saveAll(any())).thenAnswer(call -> withIds(call.getArgument(0)));
		PipelineRunService deployed = service(deploymentId);
		try {
			PipelineRun run = deployed.startPosts(List.of(UUID.randomUUID()), UUID.randomUUID()).getFirst();
			while (!deployed.ownRuns().isEmpty()) {
				Thread.sleep(POLL);
			}
			return run;
		}
		finally {
			deployed.onClose();
		}
	}

	/** The runs {@code saveAll} got, each with the id the database would give it. */
	private static List<PipelineRun> withIds(Iterable<PipelineRun> toSave) {
		List<PipelineRun> saved = new ArrayList<>();
		for (PipelineRun run : toSave) {
			ReflectionTestUtils.setField(run, "id", UUID.randomUUID());
			saved.add(run);
		}
		return saved;
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

	/** How the step of a run ends. */
	enum Ending {

		/** Its summary, returned. */
		SUMMARY,
		/** Its own exception. */
		FAILURE,
		/** An {@link Error}, which {@code execute} does not catch. */
		ERROR

	}

	/** What lands on the queue thread at one point of a run's end. */
	enum Event {

		NONE, INTERRUPT, STOP, STOP_AND_INTERRUPT;

		void happen(PipelineRunService service) {
			if (this == STOP || this == STOP_AND_INTERRUPT) {
				service.onClose();
			}
			if (this == INTERRUPT || this == STOP_AND_INTERRUPT) {
				Thread.currentThread().interrupt();
			}
		}

	}

	/**
	 * One way a run ends ({@link #endings()}).
	 *
	 * @param name what the case is, the test's display name
	 * @param closed whether {@code execute} closes and reports the run itself
	 * @param interruptKept whether the queue thread keeps an interrupt after the run
	 */
	record Ends(String name, Ending ending, Event duringStep, Event duringCheck, Event duringNotification,
			boolean closed, boolean interruptKept) {

		@Override
		public String toString() {
			return name;
		}

	}

}
