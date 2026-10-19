package pl.szczurradar.api.pipeline.run;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Which runs need an admin's attention ({@link PipelineRun#needsAttention()}), for every status. */
class PipelineRunTest {

	@ParameterizedTest
	@EnumSource(value = RunStatus.class, names = { "FAILED", "INTERRUPTED" })
	void aFailedOrInterruptedRunNeedsAttentionWhateverItsCounters(RunStatus status) {
		assertThat(PipelineRun.needsAttention(status, 0, 0, 0)).isTrue();
		assertThat(PipelineRun.needsAttention(status, 1, 1, 1)).isTrue();
	}

	@Test
	void aPartialRunNeedsAttentionOnlyWithADeadPostATechnicalErrorOrSkippedElements() {
		assertThat(PipelineRun.needsAttention(RunStatus.PARTIAL, 1, 0, 0)).as("a dead post").isTrue();
		assertThat(PipelineRun.needsAttention(RunStatus.PARTIAL, 0, 1, 0)).as("a technical error").isTrue();
		assertThat(PipelineRun.needsAttention(RunStatus.PARTIAL, 0, 0, 1)).as("a skipped element").isTrue();
		assertThat(PipelineRun.needsAttention(RunStatus.PARTIAL, 2, 3, 4)).isTrue();
		// Only failed posts with attempts left: the next run retries them.
		assertThat(PipelineRun.needsAttention(RunStatus.PARTIAL, 0, 0, 0)).isFalse();
	}

	@Test
	void aSucceededRunNeedsAttentionOnlyWithSkippedElements() {
		assertThat(PipelineRun.needsAttention(RunStatus.SUCCEEDED, 0, 0, 1)).as("a skipped element").isTrue();
		assertThat(PipelineRun.needsAttention(RunStatus.SUCCEEDED, 0, 0, 0)).isFalse();
		// A succeeded run has neither, but they alone would not make it need attention.
		assertThat(PipelineRun.needsAttention(RunStatus.SUCCEEDED, 1, 1, 0)).isFalse();
	}

	@ParameterizedTest
	@EnumSource(value = RunStatus.class, names = { "QUEUED", "RUNNING" })
	void anUnfinishedRunNeedsNoAttentionWhateverItsCounters(RunStatus status) {
		assertThat(PipelineRun.needsAttention(status, 0, 0, 0)).isFalse();
		assertThat(PipelineRun.needsAttention(status, 1, 1, 1)).isFalse();
	}

	@Test
	void aNewRunIsQueuedAndNeedsNoAttention() {
		assertThat(PipelineRun.retry(UUID.randomUUID(), null).needsAttention()).isFalse();
	}

}
