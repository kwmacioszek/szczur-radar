package pl.szczurradar.api.pipeline;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.PropertyResolver;

import pl.szczurradar.api.llm.LlmProviderProperties;
import pl.szczurradar.api.llm.transport.TransportRetry;
import pl.szczurradar.api.pipeline.state.Durations;
import pl.szczurradar.api.settings.DurationSetting;
import pl.szczurradar.api.settings.InvalidSettingsException;
import pl.szczurradar.api.settings.SettingText;

/**
 * {@code PIPELINE_STALL_TIMEOUT}: how long a run of this instance may go without progress before
 * {@link RunWatchdog} stops it. Optional, with a unit, e.g. {@code 39m} or {@code 1h}; a bare number
 * (Spring would read it as milliseconds), zero or less, or more than {@code 24h} fails the start.
 *
 * <p>The default is the longest single LLM call ({@link TransportRetry#maxCallDuration} for
 * {@code LLM_TIMEOUT} and {@code LLM_SERVICE_TIER}) plus {@link #MARGIN}, and at least
 * {@link #MIN_DEFAULT}: the LLM check before the assess step, the run's last posts and a small retry run
 * have only one call in flight, which moves nothing until it ends. That is {@code 15m} with the default
 * settings ({@code 120s}, tier {@code default}) and {@code 39m} on production ({@code 300s}, flex). A
 * shorter explicit value is accepted (tests, a manual check), with a warning at start-up.
 *
 * <p>Only the server reads it ({@link RunWatchdog}); the operator commands ({@code migrate},
 * {@code create-admin}, …) do not.
 */
public final class PipelineStallSettings {

	public static final String NAME = "PIPELINE_STALL_TIMEOUT";

	/** The shortest default timeout. */
	public static final Duration MIN_DEFAULT = Duration.ofMinutes(15);

	/** Added to the longest single LLM call: the run's own writes, a slow connection. */
	public static final Duration MARGIN = Duration.ofMinutes(5);

	/** The shortest accepted timeout: an explicit value may be far below {@link #minimum()} (tests, a manual check). */
	public static final Duration MIN = Duration.ofMillis(1);

	/** The longest accepted timeout ({@code 1d}). */
	public static final Duration MAX = Duration.ofHours(24);

	private static final Logger log = LoggerFactory.getLogger(PipelineStallSettings.class);

	private final Duration timeout;

	private final Duration minimum;

	private PipelineStallSettings(Duration timeout, Duration minimum) {
		this.timeout = timeout;
		this.minimum = minimum;
	}

	/**
	 * Reads and validates the setting; logs a warning when it is set below {@link #minimum()}.
	 *
	 * @param llm the call settings the default and the minimum follow
	 * @throws InvalidSettingsException naming the setting when its value is invalid
	 */
	public static PipelineStallSettings from(PropertyResolver environment, LlmProviderProperties llm) {
		Duration minimum = TransportRetry.maxCallDuration(llm.timeout(), llm.serviceTier()).plus(MARGIN);
		Duration defaultTimeout = minimum.compareTo(MIN_DEFAULT) > 0 ? minimum : MIN_DEFAULT;
		String text = SettingText.trimmed(environment.getProperty(NAME));
		if (text.isEmpty()) {
			return new PipelineStallSettings(defaultTimeout, minimum);
		}
		PipelineStallSettings settings = new PipelineStallSettings(parse(text, defaultTimeout), minimum);
		if (settings.belowMinimum()) {
			log.warn("{}={} is shorter than the longest single LLM call plus {} ({} with {}={} and {}={}): a run"
					+ " waiting for one slow call may be stopped as stalled. Unset it for the default ({}).", NAME,
					text, Durations.format(MARGIN), Durations.format(minimum), LlmProviderProperties.TIMEOUT,
					Durations.format(llm.timeout()), LlmProviderProperties.SERVICE_TIER, llm.serviceTier().id(),
					Durations.format(defaultTimeout));
		}
		return settings;
	}

	/** How long a run may go without progress. */
	public Duration timeout() {
		return timeout;
	}

	/** The longest single LLM call plus {@link #MARGIN}: a shorter timeout may stop a run waiting for one call. */
	public Duration minimum() {
		return minimum;
	}

	/** Whether the timeout is shorter than {@link #minimum()} (an explicit value; never the default). */
	public boolean belowMinimum() {
		return timeout.compareTo(minimum) < 0;
	}

	@Override
	public String toString() {
		return "PipelineStallSettings[timeout=" + timeout + ", minimum=" + minimum + "]";
	}

	private static Duration parse(String text, Duration defaultTimeout) {
		List<String> problems = new ArrayList<>();
		Duration timeout = DurationSetting.parse(NAME, text, MIN, MAX, problems);
		if (timeout == null) {
			throw new InvalidSettingsException("Invalid configuration: " + problems.getFirst()
					+ ", or unset for the default (" + Durations.format(defaultTimeout) + ").");
		}
		return timeout;
	}

}
