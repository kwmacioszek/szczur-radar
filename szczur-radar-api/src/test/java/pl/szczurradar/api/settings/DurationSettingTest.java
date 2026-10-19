package pl.szczurradar.api.settings;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** The one rule for a duration setting: a whole number with a lower-case unit, within the bounds. */
class DurationSettingTest {

	private static final String NAME = "SOME_TIMEOUT";

	private static final Duration MIN = Duration.ofSeconds(10);

	private static final Duration MAX = Duration.ofMinutes(30);

	private final List<String> problems = new ArrayList<>();

	@ParameterizedTest
	@ValueSource(strings = { "120", "+5", "-5", "0" })
	void aBareNumberNeedsAUnitBecauseSpringWouldReadMilliseconds(String text) {
		assertThat(DurationSetting.parse(NAME, text, MIN, MAX, problems)).isNull();

		assertThat(problems).containsExactly("SOME_TIMEOUT needs a unit, e.g. 300s or 5m (got '" + text
				+ "'; a bare number would be milliseconds)");
	}

	@ParameterizedTest
	@ValueSource(strings = { "PT5M", "PT0.1S", "1.5h", "12 hours", "12x", "soon", "5 m", "m", "300S", "5M" })
	void anythingButAWholeNumberWithALowerCaseUnitIsNotADuration(String text) {
		assertThat(DurationSetting.parse(NAME, text, MIN, MAX, problems)).isNull();

		assertThat(problems).containsExactly("SOME_TIMEOUT must be a duration such as 300s or 5m (got '" + text + "')");
	}

	@ParameterizedTest
	@ValueSource(strings = { "9999999999999999999d", "9999999999999999d" })
	void anOverflowIsNotADurationAndALongValueIsRepeatedOnlyInPart(String text) {
		assertThat(DurationSetting.parse(NAME, text, MIN, MAX, problems)).isNull();

		assertThat(problems)
			.containsExactly("SOME_TIMEOUT must be a duration such as 300s or 5m (got '9999999999999999…')");
	}

	@ParameterizedTest
	@CsvSource({ "1000000000ns, PT1S", "1000000us, PT1S", "1000ms, PT1S", "1s, PT1S", "1m, PT1M", "1h, PT1H",
			"1d, PT24H", "+5m, PT5M" })
	void everyUnitIsRead(String text, Duration expected) {
		assertThat(DurationSetting.parse(NAME, text, Duration.ofNanos(1), Duration.ofDays(1), problems))
			.isEqualTo(expected);
		assertThat(problems).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = { "10s", "10000ms", "30m", "1800s" })
	void theBoundsAreIncluded(String text) {
		assertThat(DurationSetting.parse(NAME, text, MIN, MAX, problems)).isBetween(MIN, MAX);
		assertThat(problems).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = { "9s", "9999ms", "31m", "1801s", "0s", "-5m", "30d" })
	void aValueOutsideTheBoundsIsRefusedNamingThem(String text) {
		assertThat(DurationSetting.parse(NAME, text, MIN, MAX, problems)).isNull();

		assertThat(problems).containsExactly("SOME_TIMEOUT must be from 10s to 30m (got '" + text + "')");
	}

	@Test
	void eachProblemIsAddedToTheCallersList() {
		problems.add("ANOTHER_SETTING is not set");

		DurationSetting.parse(NAME, "120", MIN, MAX, problems);

		assertThat(problems).hasSize(2).first().isEqualTo("ANOTHER_SETTING is not set");
	}

	@ParameterizedTest
	@CsvSource({ "PT0.001S, 1ms", "PT10S, 10s", "PT1M, 1m", "PT30M, 30m", "PT90S, 90s", "PT24H, 1d", "PT168H, 7d",
			"PT36H, 36h", "PT0.000001S, 1us", "PT0.000000001S, 1ns", "PT1.5S, 1500ms" })
	void boundsAreWrittenInTheLargestWholeUnit(Duration bound, String expected) {
		assertThat(DurationSetting.format(bound)).isEqualTo(expected);
	}

}
