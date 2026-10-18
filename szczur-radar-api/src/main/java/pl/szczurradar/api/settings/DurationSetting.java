package pl.szczurradar.api.settings;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.convert.DurationStyle;

/**
 * The one rule for a duration setting ({@code LLM_TIMEOUT}, {@code ACTIVATION_LINK_TTL},
 * {@code PIPELINE_STALL_TIMEOUT}): a whole number with a lower-case unit ({@code ns}, {@code us},
 * {@code ms}, {@code s}, {@code m}, {@code h}, {@code d}), e.g. {@code 300s} or {@code 5m}, within the
 * setting's bounds.
 *
 * <p>A bare number is refused (Spring would read it as milliseconds), and so are ISO-8601
 * ({@code PT5M}), fractions, spaces and unknown units. Each caller keeps its own default for an empty
 * value and its own way of reporting the problem.
 */
public final class DurationSetting {

	/** The accepted units, largest first: the order the bounds in a message are written in. */
	private enum Unit {

		DAYS("d", Duration.ofDays(1)),
		HOURS("h", Duration.ofHours(1)),
		MINUTES("m", Duration.ofMinutes(1)),
		SECONDS("s", Duration.ofSeconds(1)),
		MILLIS("ms", Duration.ofMillis(1)),
		MICROS("us", Duration.ofNanos(1_000)),
		NANOS("ns", Duration.ofNanos(1));

		private final String suffix;

		private final Duration length;

		Unit(String suffix, Duration length) {
			this.suffix = suffix;
			this.length = length;
		}

	}

	private static final Pattern BARE_NUMBER = Pattern.compile("[+-]?\\d+");

	private static final Pattern WITH_UNIT = Pattern.compile("[+-]?\\d+("
			+ Arrays.stream(Unit.values()).map(unit -> unit.suffix).collect(Collectors.joining("|")) + ")");

	private DurationSetting() {
	}

	/**
	 * Reads the setting {@code name}.
	 *
	 * @param text the value, already {@link SettingText#trimmed trimmed} and not empty (an empty value
	 *        is the caller's default)
	 * @param min the shortest accepted value (included); positive, so that zero and negative values are
	 *        refused
	 * @param max the longest accepted value (included)
	 * @param problems gets one message naming the setting and {@code text} when the value is refused
	 * @return the duration, or {@code null} when the value is refused
	 */
	public static @Nullable Duration parse(String name, String text, Duration min, Duration max,
			List<String> problems) {
		if (BARE_NUMBER.matcher(text).matches()) {
			problems.add(name + " needs a unit, e.g. 300s or 5m (got '" + text
					+ "'; a bare number would be milliseconds)");
			return null;
		}
		Duration duration = WITH_UNIT.matcher(text).matches() ? simple(text) : null;
		if (duration == null) {
			problems.add(name + " must be a duration such as 300s or 5m (got '" + text + "')");
			return null;
		}
		if (duration.compareTo(min) < 0 || duration.compareTo(max) > 0) {
			problems.add(name + " must be from " + format(min) + " to " + format(max) + " (got '" + text + "')");
			return null;
		}
		return duration;
	}

	/** {@code duration} in the largest unit that holds it whole, e.g. {@code 24h} as {@code 1d}. */
	static String format(Duration duration) {
		for (Unit unit : Unit.values()) {
			long count = duration.dividedBy(unit.length);
			if (unit.length.multipliedBy(count).equals(duration)) {
				return count + unit.suffix;
			}
		}
		throw new IllegalStateException("unreachable: every duration is a whole number of nanoseconds");
	}

	/** Only the simple style, never ISO-8601; {@code null} on an overflow. */
	private static @Nullable Duration simple(String text) {
		try {
			return DurationStyle.SIMPLE.parse(text);
		}
		catch (IllegalArgumentException | ArithmeticException ex) {
			return null;
		}
	}

}
