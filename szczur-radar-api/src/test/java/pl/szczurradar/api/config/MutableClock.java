package pl.szczurradar.api.config;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * A clock a test sets; UTC like the application's ({@link ClockConfig}). A test context makes it the
 * {@code @Primary} {@link Clock} bean, so "today" of the daily range, of a range request's check and of
 * {@code missing-days} is the test's ({@code InternalPipelineRunIT}, {@code scenario/PrEnvScenariosIT}).
 */
public final class MutableClock extends Clock {

	private volatile Instant now;

	public MutableClock(Instant now) {
		this.now = now;
	}

	public void set(Instant now) {
		this.now = now;
	}

	@Override
	public ZoneId getZone() {
		return ZoneOffset.UTC;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		return Clock.fixed(now, zone);
	}

	@Override
	public Instant instant() {
		return now;
	}

}
