package pl.szczurradar.api.pipeline.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import pl.szczurradar.api.pipeline.api.DeploySafetyController.DeploySafety;
import pl.szczurradar.api.pipeline.run.PipelineRunRepository;

/**
 * {@link DeploySafetyController} between its two questions: the race {@code DeploySafetyIT} cannot
 * stage. Every answer for a real database is in {@code DeploySafetyIT}.
 */
class DeploySafetyControllerTest {

	private final PipelineRunRepository runs = mock(PipelineRunRepository.class);

	private final DeploySafetyController controller = new DeploySafetyController(runs);

	@Test
	void aRunSeenAsActiveIsNeverSafeWhateverTheReasonQuestionSees() {
		// Active when asked whether to deploy; no longer running when asked why (it ended, or it was
		// still queued and is taken now): the answer stays unsafe.
		when(runs.existsActive()).thenReturn(true);
		when(runs.existsRunning()).thenReturn(false);

		assertThat(controller.deploySafety().getBody())
				.isEqualTo(new DeploySafety(false, DeploySafetyReason.PIPELINE_QUEUED));
	}

	@Test
	void withoutAnActiveRunItIsSafeEvenIfARunStartsJustAfter() {
		when(runs.existsActive()).thenReturn(false);
		when(runs.existsRunning()).thenReturn(true);

		assertThat(controller.deploySafety().getBody()).isEqualTo(new DeploySafety(true, null));
	}
}
