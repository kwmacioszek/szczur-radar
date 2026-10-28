package pl.szczurradar.api.pipeline.run;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import pl.szczurradar.api.user.SystemAccount;

/**
 * Why runs need an admin's attention ({@link PipelineRun#attentionReasons()}, and so
 * {@link PipelineRun#needsAttention()}), for every status and in the rule's order, and which the daily cron
 * requested ({@link PipelineRun#scheduled()}).
 */
class PipelineRunTest {

	@Test
	void aFailedRunHasOnlyItsOwnReasonWhateverItsCounters() {
		assertThat(PipelineRun.attentionReasons(RunStatus.FAILED, 0, 0, 0)).containsExactly(AttentionReason.FAILED);
		assertThat(PipelineRun.attentionReasons(RunStatus.FAILED, 1, 1, 1)).containsExactly(AttentionReason.FAILED);
	}

	@Test
	void anInterruptedRunHasOnlyItsOwnReasonWhateverItsCounters() {
		assertThat(PipelineRun.attentionReasons(RunStatus.INTERRUPTED, 0, 0, 0))
			.containsExactly(AttentionReason.INTERRUPTED);
		assertThat(PipelineRun.attentionReasons(RunStatus.INTERRUPTED, 1, 1, 1))
			.containsExactly(AttentionReason.INTERRUPTED);
	}

	@Test
	void aPartialRunNeedsAttentionOnlyWithADeadPostATechnicalErrorOrSkippedElementsInThatOrder() {
		assertThat(PipelineRun.attentionReasons(RunStatus.PARTIAL, 1, 0, 0)).as("a dead post")
			.containsExactly(AttentionReason.DEAD_POSTS);
		assertThat(PipelineRun.attentionReasons(RunStatus.PARTIAL, 0, 1, 0)).as("a technical error")
			.containsExactly(AttentionReason.PROCESSING_ERRORS);
		assertThat(PipelineRun.attentionReasons(RunStatus.PARTIAL, 0, 0, 1)).as("a skipped element")
			.containsExactly(AttentionReason.SKIPPED);
		assertThat(PipelineRun.attentionReasons(RunStatus.PARTIAL, 2, 3, 4)).containsExactly(AttentionReason.DEAD_POSTS,
				AttentionReason.PROCESSING_ERRORS, AttentionReason.SKIPPED);
		// Only failed posts with attempts left: the next run retries them.
		assertThat(PipelineRun.attentionReasons(RunStatus.PARTIAL, 0, 0, 0)).isEmpty();
	}

	@Test
	void aSucceededRunNeedsAttentionOnlyWithSkippedElements() {
		assertThat(PipelineRun.attentionReasons(RunStatus.SUCCEEDED, 0, 0, 1)).as("a skipped element")
			.containsExactly(AttentionReason.SKIPPED);
		assertThat(PipelineRun.attentionReasons(RunStatus.SUCCEEDED, 0, 0, 0)).isEmpty();
		// A succeeded run has neither, but they alone would not make it need attention.
		assertThat(PipelineRun.attentionReasons(RunStatus.SUCCEEDED, 1, 1, 0)).isEmpty();
	}

	@ParameterizedTest
	@EnumSource(value = RunStatus.class, names = { "QUEUED", "RUNNING" })
	void anUnfinishedRunNeedsNoAttentionWhateverItsCounters(RunStatus status) {
		assertThat(PipelineRun.attentionReasons(status, 0, 0, 0)).isEmpty();
		assertThat(PipelineRun.attentionReasons(status, 1, 1, 1)).isEmpty();
	}

	@Test
	void onlyARunRequestedByTheSchedulerIsScheduled() {
		assertThat(PipelineRun.retry(SystemAccount.SCHEDULER.id(), null).scheduled()).isTrue();
		assertThat(PipelineRun.retry(UUID.randomUUID(), null).scheduled()).as("an admin's").isFalse();
	}

	@Test
	void aNewRunIsQueuedAndNeedsNoAttention() {
		PipelineRun run = PipelineRun.retry(UUID.randomUUID(), null);

		assertThat(run.attentionReasons()).isEmpty();
		assertThat(run.needsAttention()).isFalse();
	}

}
