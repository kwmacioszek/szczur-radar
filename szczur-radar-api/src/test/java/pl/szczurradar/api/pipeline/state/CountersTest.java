package pl.szczurradar.api.pipeline.state;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import pl.szczurradar.api.pipeline.state.RunRecorder.Counters;

/** What one batch or one post adds to a run's counters. */
class CountersTest {

	@Test
	void aStoredBatchAddsItsNewPostsOnly() {
		assertThat(Counters.stored(3)).isEqualTo(new Counters(0, 3, 0, 0, 0, 0, 0, 0, 0));
	}

	@Test
	void anOkAssessmentAddsOneOkItsRetriesAndAFlexFallbackOnlyWhenItFellBack() {
		assertThat(Counters.assessedOk(2, true)).isEqualTo(new Counters(0, 0, 1, 0, 0, 0, 0, 2, 1));
		assertThat(Counters.assessedOk(0, false)).isEqualTo(new Counters(0, 0, 1, 0, 0, 0, 0, 0, 0));
	}

	@Test
	void aFailedAssessmentIsDeadOnlyWhenItWasTheThirdAndFellBackOnlyWhenItDid() {
		assertThat(Counters.assessedFailed(true, 1, false)).isEqualTo(new Counters(0, 0, 0, 1, 0, 1, 0, 1, 0));
		assertThat(Counters.assessedFailed(false, 0, true)).isEqualTo(new Counters(0, 0, 0, 1, 0, 0, 0, 0, 1));
	}

	@Test
	void aTechnicalErrorAddsOneProcessingErrorOnly() {
		assertThat(Counters.processingError()).isEqualTo(new Counters(0, 0, 0, 0, 1, 0, 0, 0, 0));
	}

}
