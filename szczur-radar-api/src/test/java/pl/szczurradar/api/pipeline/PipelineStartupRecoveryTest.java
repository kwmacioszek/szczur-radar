package pl.szczurradar.api.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.support.TransactionTemplate;

import pl.szczurradar.api.alerts.AlertEvent;
import pl.szczurradar.api.alerts.OperatorAlertQueue;
import pl.szczurradar.api.notifications.RunNotifications;
import pl.szczurradar.api.pipeline.state.PostTransitions;
import pl.szczurradar.api.pipeline.state.RunRecorder;

/**
 * {@link PipelineStartupRecovery} whose transaction fails (e.g. the database not reachable yet), which
 * {@code PipelineStartupRecoveryIT} cannot stage: logged, raised to the operator once, nobody notified,
 * and the start goes on.
 */
@ExtendWith(OutputCaptureExtension.class)
class PipelineStartupRecoveryTest {

	private final TransactionTemplate transaction = mock(TransactionTemplate.class);

	private final RunNotifications notifications = mock(RunNotifications.class);

	private final OperatorAlertQueue alerts = mock(OperatorAlertQueue.class);

	private final PipelineStartupRecovery recovery = new PipelineStartupRecovery(mock(RunRecorder.class),
			mock(PostTransitions.class), notifications, transaction, alerts, null);

	@Test
	void aFailedRecoveryIsLoggedAndRaisedAndTheStartGoesOn(CapturedOutput output) {
		when(transaction.execute(any())).thenThrow(new DataAccessResourceFailureException("connection refused"));

		assertThat(recovery.recover()).isEmpty();

		assertThat(output).contains("ERROR", "Start-up recovery of pipeline runs failed");
		verify(alerts).raise(eq(AlertEvent.RECOVERY_FAILED), isNull(),
				argThat(detail -> detail.contains(DataAccessResourceFailureException.class.getName())
						&& !detail.contains("connection refused")));
		verify(alerts, times(1)).raise(any(), any(), any());
		verifyNoInteractions(notifications);
	}

}
