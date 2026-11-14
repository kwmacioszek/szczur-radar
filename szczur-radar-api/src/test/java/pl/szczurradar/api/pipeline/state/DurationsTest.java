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

	@Test
	void eachUnitStartsExactlyAtItsBoundary() {
		assertThat(Durations.format(Duration.ofMillis(999))).isEqualTo("999ms");
		assertThat(Durations.format(Duration.ofMillis(1_000))).isEqualTo("1.0s");
		assertThat(Durations.format(Duration.ofMillis(59_999))).isEqualTo("59.9s");
		assertThat(Durations.format(Duration.ofMillis(60_000))).isEqualTo("1m 00s");
		assertThat(Durations.format(Duration.ofSeconds(3_599))).isEqualTo("59m 59s");
		assertThat(Durations.format(Duration.ofSeconds(3_600))).isEqualTo("1h 00m");
	}

	@Test
	void theTimeSinceAStartIsTheTimeElapsedSinceIt() {
		long fiveMinutesAgo = System.nanoTime() - Duration.ofMinutes(5).toNanos();

		// The test's own time is far below the next second's change of the minute shown.
		assertThat(Durations.since(fiveMinutesAgo)).startsWith("5m 0");
	}

}
