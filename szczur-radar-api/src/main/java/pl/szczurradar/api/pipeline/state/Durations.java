package pl.szczurradar.api.pipeline.state;

import java.time.Duration;

/**
 * How a run's log lines show a duration: short, in one or two units ({@code 850ms}, {@code 12.4s},
 * {@code 3m 05s}, {@code 1h 02m}).
 */
public final class Durations {

	private Durations() {
	}

	/** {@code duration} for a log line; a negative one counts as zero. */
	public static String format(Duration duration) {
		long millis = Math.max(duration.toMillis(), 0);
		if (millis < 1_000) {
			return millis + "ms";
		}
		if (millis < 60_000) {
			return millis / 1_000 + "." + millis % 1_000 / 100 + "s";
		}
		long seconds = millis / 1_000;
		if (seconds < 3_600) {
			return "%dm %02ds".formatted(seconds / 60, seconds % 60);
		}
		return "%dh %02dm".formatted(seconds / 3_600, seconds % 3_600 / 60);
	}

	/** The time since {@code startNanos} ({@link System#nanoTime()}), {@link #format formatted}. */
	public static String since(long startNanos) {
		return format(Duration.ofNanos(System.nanoTime() - startNanos));
	}

}
