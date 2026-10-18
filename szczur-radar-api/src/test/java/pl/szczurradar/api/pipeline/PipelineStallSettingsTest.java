package pl.szczurradar.api.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.env.MockEnvironment;

import pl.szczurradar.api.llm.LlmProviderProperties;
import pl.szczurradar.api.llm.Provider;
import pl.szczurradar.api.llm.ServiceTier;
import pl.szczurradar.api.settings.InvalidSettingsException;

/**
 * {@code PIPELINE_STALL_TIMEOUT}: the default follows the longest single LLM call, a shorter explicit
 * value is accepted with a warning, and a bare number, zero, more than a day or no duration at all
 * (ISO-8601 included) fails the start.
 */
@ExtendWith(OutputCaptureExtension.class)
class PipelineStallSettingsTest {

	/** Only the three required LLM settings: tier {@code default}, {@code 120s} per attempt (a call ≤ 8 min). */
	private static final LlmProviderProperties DEFAULT_CALLS = new LlmProviderProperties(Provider.GEMINI, "model",
			"key");

	/** Production: flex, {@code 300s} per attempt (a call ≤ 34 min). */
	private static final LlmProviderProperties PRODUCTION = new LlmProviderProperties(Provider.OPENROUTER, "model",
			"key", ServiceTier.FLEX, null, Duration.ofSeconds(300));

	@Test
	void theDefaultIsTheLongestLlmCallPlusFiveMinutesButAtLeast15Minutes() {
		// 8 min + 5 min is less than the shortest default.
		assertThat(PipelineStallSettings.from(new MockEnvironment(), DEFAULT_CALLS).timeout())
			.isEqualTo(Duration.ofMinutes(15));
		assertThat(PipelineStallSettings.from(environment("  "), DEFAULT_CALLS).timeout())
			.isEqualTo(Duration.ofMinutes(15));
		// 34 min + 5 min.
		PipelineStallSettings production = PipelineStallSettings.from(new MockEnvironment(), PRODUCTION);
		assertThat(production.timeout()).isEqualTo(Duration.ofMinutes(39));
		assertThat(production.belowMinimum()).isFalse();
	}

	@Test
	void anExplicitValueBelowTheLongestCallIsAcceptedWithAWarning(CapturedOutput output) {
		PipelineStallSettings settings = PipelineStallSettings.from(environment("90s"), DEFAULT_CALLS);

		assertThat(settings.timeout()).isEqualTo(Duration.ofSeconds(90));
		assertThat(settings.minimum()).isEqualTo(Duration.ofMinutes(13));
		assertThat(settings.belowMinimum()).isTrue();
		assertThat(output).contains("WARN", "PIPELINE_STALL_TIMEOUT=90s", "13m 00s", "LLM_TIMEOUT=2m 00s",
				"LLM_SERVICE_TIER=default");
	}

	@Test
	void anExplicitValueAboveTheLongestCallNeedsNoWarning(CapturedOutput output) {
		PipelineStallSettings settings = PipelineStallSettings.from(environment("1h"), DEFAULT_CALLS);

		assertThat(settings.timeout()).isEqualTo(Duration.ofHours(1));
		assertThat(settings.belowMinimum()).isFalse();
		assertThat(output).doesNotContain(PipelineStallSettings.NAME);

		// The same hour is too short when an attempt may take 30 minutes on flex (a call ≤ 3 h 04 min).
		LlmProviderProperties slowCalls = new LlmProviderProperties(Provider.OPENROUTER, "model", "key",
				ServiceTier.FLEX, null, Duration.ofMinutes(30));
		assertThat(PipelineStallSettings.from(environment("1h"), slowCalls).belowMinimum()).isTrue();
		assertThat(output).contains("PIPELINE_STALL_TIMEOUT=1h");
	}

	@Test
	void aBareNumberFailsTheStartNamingTheSetting() {
		assertThatExceptionOfType(InvalidSettingsException.class)
			.isThrownBy(() -> PipelineStallSettings.from(environment("15"), DEFAULT_CALLS))
			.withMessageStartingWith("Invalid configuration: PIPELINE_STALL_TIMEOUT needs a unit")
			.withMessageContaining("'15'")
			.withMessageContaining("milliseconds")
			.withMessageEndingWith(", or unset for the default (15m).");
	}

	@Test
	void zeroMoreThanADayOrNoDurationFailsTheStartNamingTheSetting() {
		for (String value : List.of("0s", "-5m", "25h", "abc", "PT15M")) {
			assertThatIllegalStateException()
				.isThrownBy(() -> PipelineStallSettings.from(environment(value), DEFAULT_CALLS))
				.withMessageStartingWith("Invalid configuration: PIPELINE_STALL_TIMEOUT")
				.withMessageContaining("'" + value + "'");
		}
		assertThatIllegalStateException().isThrownBy(() -> PipelineStallSettings.from(environment("25h"), DEFAULT_CALLS))
			.withMessageContaining("PIPELINE_STALL_TIMEOUT must be from 1ms to 1d (got '25h')");
		assertThatIllegalStateException().isThrownBy(() -> PipelineStallSettings.from(environment("PT15M"), DEFAULT_CALLS))
			.withMessageContaining("PIPELINE_STALL_TIMEOUT must be a duration such as 300s or 5m (got 'PT15M')");
		assertThat(PipelineStallSettings.from(environment("24h"), DEFAULT_CALLS).timeout())
			.isEqualTo(Duration.ofHours(24));
		assertThat(PipelineStallSettings.from(environment("1ms"), DEFAULT_CALLS).timeout())
			.isEqualTo(Duration.ofMillis(1));
	}

	private static MockEnvironment environment(String value) {
		return new MockEnvironment().withProperty(PipelineStallSettings.NAME, value);
	}

}
