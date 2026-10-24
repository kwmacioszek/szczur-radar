package pl.szczurradar.api.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;

import pl.szczurradar.api.alerts.AlertEvent;
import pl.szczurradar.api.alerts.OperatorAlertQueue;
import pl.szczurradar.api.pipeline.run.InterruptionCause;
import pl.szczurradar.api.pipeline.run.PipelineRun;
import pl.szczurradar.api.pipeline.run.PipelineRunRepository;
import pl.szczurradar.api.pipeline.run.RunKind;
import pl.szczurradar.api.pipeline.run.RunStatus;

/**
 * Which run endings {@link RunNotifications#runEnded} raises to the operator, with mocks: a run that
 * needs attention once its notifications were created, except a cron run that ended {@code failed} or
 * {@code interrupted} (the cron reports it); a failure to notify; and an operator command's context
 * without the queue. Who gets the notifications is in {@code NotificationsIT}.
 */
@ExtendWith(OutputCaptureExtension.class)
class RunNotificationsTest {

	private final PipelineRunRepository runs = mock(PipelineRunRepository.class);

	private final NotificationRepository notifications = mock(NotificationRepository.class);

	private final OperatorAlertQueue alerts = mock(OperatorAlertQueue.class);

	@SuppressWarnings("unchecked")
	private final ObjectProvider<OperatorAlertQueue> alertQueue = mock(ObjectProvider.class);

	private final RunNotifications runNotifications = new RunNotifications(runs, notifications, alertQueue);

	RunNotificationsTest() {
		when(alertQueue.getIfAvailable()).thenReturn(alerts);
	}

	@Test
	void anAdminsFailedRunIsRaisedWithItsStateAndItsErrorCut() {
		String error = "the LLM check failed: " + "x".repeat(400);
		PipelineRun run = run(RunKind.RANGE, false, RunStatus.FAILED, null, 0, 0, 0, error);
		UUID id = run.getId();
		notified(run, 2);

		runNotifications.runEnded(id);

		verify(alerts).raise(eq(AlertEvent.RUN_NEEDS_ATTENTION), eq(id), argThat(detail -> {
			assertThat(detail).startsWith("Pipeline run " + id + " (range, admin) ended failed: dead 0,"
					+ " errors 0, skipped 0; error: the LLM check failed: xxx")
				.endsWith("…; /admin/runs/" + id)
				.doesNotContain("x".repeat(300));
			return true;
		}));
		verify(alerts, times(1)).raise(any(), any(), any());
	}

	@ParameterizedTest
	@EnumSource(value = RunStatus.class, names = { "FAILED", "INTERRUPTED" })
	void aCronRunThatFailedOrWasInterruptedIsLeftToTheCron(RunStatus status) {
		PipelineRun run = run(RunKind.RANGE, true, status, null, 0, 0, 0, "the run failed");
		UUID id = run.getId();
		notified(run, 1);

		runNotifications.runEnded(id);

		// The admins are notified in the app; the cron's exit code reaches the operator.
		verify(notifications).notifyAdmins(NotificationKind.RUN_ATTENTION, id);
		verify(alerts, never()).raise(any(), any(), any());
	}

	@Test
	void aCronRunThatEndedPartialWithADeadPostIsRaised() {
		PipelineRun run = run(RunKind.RETRY, true, RunStatus.PARTIAL, null, 1, 0, 0, null);
		UUID id = run.getId();
		notified(run, 1);

		runNotifications.runEnded(id);

		verify(alerts).raise(AlertEvent.RUN_NEEDS_ATTENTION, id, "Pipeline run " + id
				+ " (retry, cron) ended partial: dead 1, errors 0, skipped 0; /admin/runs/" + id);
	}

	@Test
	void anInterruptedAdminsRunNamesItsCause() {
		PipelineRun run = run(RunKind.POSTS, false, RunStatus.INTERRUPTED, InterruptionCause.DEPLOY, 0, 0, 0,
				InterruptionCause.DEPLOY.error());
		UUID id = run.getId();
		notified(run, 1);

		runNotifications.runEnded(id);

		verify(alerts).raise(AlertEvent.RUN_NEEDS_ATTENTION, id, "Pipeline run " + id
				+ " (posts, admin) ended interrupted (deploy): dead 0, errors 0, skipped 0; error: "
				+ InterruptionCause.DEPLOY.error() + "; /admin/runs/" + id);
	}

	@Test
	void aRunReportedAgainCreatesNoNotificationAndRaisesNothing() {
		PipelineRun run = run(RunKind.RANGE, false, RunStatus.FAILED, null, 0, 0, 0, "the run failed");
		UUID id = run.getId();
		notified(run, 0);

		runNotifications.runEnded(id);

		verify(alerts, never()).raise(any(), any(), any());
	}

	@Test
	void aFailureToNotifyIsRaisedWithoutItsMessageAndNotThrown(CapturedOutput output) {
		PipelineRun run = run(RunKind.RANGE, false, RunStatus.FAILED, null, 0, 0, 0, "the run failed");
		UUID id = run.getId();
		when(runs.findById(id)).thenReturn(Optional.of(run));
		when(notifications.notifyAdmins(NotificationKind.RUN_ATTENTION, id))
			.thenThrow(new DataIntegrityViolationException("insert into notifications values (secret-ish)"));

		assertThatNoException().isThrownBy(() -> runNotifications.runEnded(id));

		assertThat(output).contains("ERROR", "Pipeline run " + id + ": the admins could not be notified");
		verify(alerts).raise(AlertEvent.NOTIFICATION_FAILED, id, "Pipeline run " + id
				+ ": the admins could not be notified (" + DataIntegrityViolationException.class.getName()
				+ "); details in the API log");
		verify(alerts, times(1)).raise(any(), any(), any());
	}

	@Test
	void withoutTheQueueNothingIsRaisedAndNothingThrown() {
		when(alertQueue.getIfAvailable()).thenReturn(null);
		PipelineRun run = run(RunKind.RANGE, false, RunStatus.FAILED, null, 0, 0, 0, "the run failed");
		UUID id = run.getId();
		notified(run, 1);
		UUID broken = UUID.randomUUID();
		when(runs.findById(broken)).thenThrow(new IllegalStateException("the database is gone"));

		assertThatNoException().isThrownBy(() -> {
			runNotifications.runEnded(id);
			runNotifications.runEnded(broken);
		});

		verify(notifications).notifyAdmins(NotificationKind.RUN_ATTENTION, id);
		verify(alerts, never()).raise(any(), any(), any());
	}

	/** A finished run that needs attention, as the repository returns it. */
	private PipelineRun run(RunKind kind, boolean scheduled, RunStatus status, @Nullable InterruptionCause cause,
			int dead, int errors, int skipped, @Nullable String error) {
		PipelineRun run = mock(PipelineRun.class);
		when(run.getId()).thenReturn(UUID.randomUUID());
		when(run.getKind()).thenReturn(kind);
		when(run.scheduled()).thenReturn(scheduled);
		when(run.getStatus()).thenReturn(status);
		when(run.getInterruptionCause()).thenReturn(cause);
		when(run.getDead()).thenReturn(dead);
		when(run.getProcessingErrors()).thenReturn(errors);
		when(run.getSkipped()).thenReturn(skipped);
		when(run.getError()).thenReturn(error);
		when(run.needsAttention()).thenReturn(true);
		return run;
	}

	/** {@code run} is found and its report creates {@code created} notifications. */
	private void notified(PipelineRun run, int created) {
		when(runs.findById(run.getId())).thenReturn(Optional.of(run));
		when(notifications.notifyAdmins(NotificationKind.RUN_ATTENTION, run.getId())).thenReturn(created);
	}

}
