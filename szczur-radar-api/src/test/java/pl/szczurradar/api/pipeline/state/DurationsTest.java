package pl.szczurradar.api.pipeline.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

/** How the run's log lines show a duration. */
class DurationsTest {

	@Test
	void aDurationIsShownInOneOrTwoUnits() {
		assertThat(Durations.format(Duration.ofMillis(850))).isEqualTo("850ms");
		assertThat(Durations.format(Duration.ofMillis(12_480))).isEqualTo("12.4s");
		assertThat(Durations.format(Duration.ofSeconds(185))).isEqualTo("3m 05s");
		assertThat(Durations.format(Duration.ofMinutes(62).plusSeconds(59))).isEqualTo("1h 02m");
		assertThat(Durations.format(Duration.ofMillis(-5))).isEqualTo("0ms");
	}

}
