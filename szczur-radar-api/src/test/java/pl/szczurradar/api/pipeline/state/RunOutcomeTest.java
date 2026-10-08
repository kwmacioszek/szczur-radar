package pl.szczurradar.api.pipeline.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import pl.szczurradar.api.pipeline.flow.RunFlow.AssessedBatch;
import pl.szczurradar.api.pipeline.flow.RunFlow.ProcessingError;
import pl.szczurradar.api.pipeline.run.RunStatus;
import pl.szczurradar.api.pipeline.state.PostTransitions.Outcome;
import pl.szczurradar.api.posts.PostStage;

/** Where a post goes after an attempt, and a run's status from its assess step. */
class RunOutcomeTest {

	@Test
	void anOkAttemptAssessesThePostWithoutCountingAnAttempt() {
		assertThat(Outcome.after(true, 0)).isEqualTo(new Outcome(PostStage.ASSESSED, 0));
		assertThat(Outcome.after(true, 2)).isEqualTo(new Outcome(PostStage.ASSESSED, 2));
	}

	@Test
	void aFailedAttemptCountsAndTheThirdMakesThePostDead() {
		assertThat(Outcome.after(false, 0)).isEqualTo(new Outcome(PostStage.FAILED, 1));
		assertThat(Outcome.after(false, 1)).isEqualTo(new Outcome(PostStage.FAILED, 2));
		assertThat(Outcome.after(false, 2)).isEqualTo(new Outcome(PostStage.DEAD, 3));
	}

	@Test
	void aRunSucceedsOnlyWithoutFailedDeadAndTechnicalErrors() {
		UUID run = UUID.randomUUID();
		assertThat(new AssessedBatch(run, 5, 0, 0, List.of()).status()).isEqualTo(RunStatus.SUCCEEDED);
		assertThat(new AssessedBatch(run, 0, 0, 0, List.of()).status()).isEqualTo(RunStatus.SUCCEEDED);
		assertThat(new AssessedBatch(run, 5, 1, 0, List.of()).status()).isEqualTo(RunStatus.PARTIAL);
		assertThat(new AssessedBatch(run, 5, 1, 1, List.of()).status()).isEqualTo(RunStatus.PARTIAL);
		assertThat(new AssessedBatch(run, 5, 0, 0,
				List.of(new ProcessingError(UUID.randomUUID(), "IllegalStateException: x"))).status())
			.isEqualTo(RunStatus.PARTIAL);
	}

}
