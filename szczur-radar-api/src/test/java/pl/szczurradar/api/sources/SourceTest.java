package pl.szczurradar.api.sources;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The thresholds of failed assessment attempts each source declares (S-35, evidence in its change folder). */
class SourceTest {

	@Test
	void eachSourceHasItsFailureThreshold() {
		assertThat(Source.WYKOP.failureThresholdPercent()).isEqualTo(5);
		assertThat(Source.REDDIT.failureThresholdPercent()).isEqualTo(25);
	}

}
