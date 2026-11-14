package pl.szczurradar.api.pipeline.run;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link PipelineRunStep}: a new step of a run starts {@code running}, and a step loaded from its row reads back
 * every column, also those only the database writes ({@code RunRecorder}'s conditional updates, the creation
 * time). The row's columns are set as Hibernate's field access sets them.
 */
class PipelineRunStepTest {

	@Test
	void aNewStepOfARunStartsRunningWithoutAnEnd() {
		UUID run = UUID.randomUUID();

		PipelineRunStep step = new PipelineRunStep(run, RunStep.STORE);

		assertThat(step.getRunId()).isEqualTo(run);
		assertThat(step.getStep()).isEqualTo(RunStep.STORE);
		assertThat(step.getStatus()).isEqualTo(StepStatus.RUNNING);
		assertThat(step.getFinishedAt()).isNull();
		assertThat(step.getItemsIn()).isNull();
		assertThat(step.getItemsOut()).isNull();
		assertThat(step.getError()).isNull();
	}

	@Test
	void aStepLoadedFromItsRowReadsBackEveryColumn() {
		UUID id = UUID.randomUUID();
		Instant started = Instant.parse("2026-10-02T00:30:01Z");
		Instant finished = Instant.parse("2026-10-02T00:31:12Z");
		PipelineRunStep step = new PipelineRunStep(UUID.randomUUID(), RunStep.ASSESS);
		ReflectionTestUtils.setField(step, "id", id);
		ReflectionTestUtils.setField(step, "status", StepStatus.FAILED);
		ReflectionTestUtils.setField(step, "startedAt", started);
		ReflectionTestUtils.setField(step, "finishedAt", finished);
		ReflectionTestUtils.setField(step, "itemsIn", 12);
		ReflectionTestUtils.setField(step, "itemsOut", 9);
		ReflectionTestUtils.setField(step, "error", "IllegalStateException: the mapper broke");

		assertThat(step.getId()).isEqualTo(id);
		assertThat(step.getStatus()).isEqualTo(StepStatus.FAILED);
		assertThat(step.getStartedAt()).isEqualTo(started);
		assertThat(step.getFinishedAt()).isEqualTo(finished);
		assertThat(step.getItemsIn()).isEqualTo(12);
		assertThat(step.getItemsOut()).isEqualTo(9);
		assertThat(step.getError()).isEqualTo("IllegalStateException: the mapper broke");
	}

}
