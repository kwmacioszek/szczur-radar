package pl.szczurradar.api.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The application's clock: system time in UTC. Code that asks for "now" or "today" takes it, so a
 * test can fix the time (e.g. the daily run's range on a daylight-saving day).
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

}
