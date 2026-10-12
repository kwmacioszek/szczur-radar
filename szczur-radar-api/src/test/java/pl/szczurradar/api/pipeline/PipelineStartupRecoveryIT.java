package pl.szczurradar.api.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import pl.szczurradar.api.TestcontainersConfiguration;
import pl.szczurradar.api.cli.MigrateCommand;
import pl.szczurradar.api.user.SystemAccount;

/**
 * Start-up recovery ends before the web server accepts connections: a run left queued by a stopped
 * instance is already {@code interrupted} when the server has opened its port
 * ({@link WebServerInitializedEvent}). Otherwise the request that wakes the API (the daily cron's
 * start) could create a run that recovery interrupts a moment later. A real server on a random port.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = MigrateCommand.ENABLE_FLYWAY)
@Import({ TestcontainersConfiguration.class, PipelineStartupRecoveryIT.Probe.class })
class PipelineStartupRecoveryIT {

	@Autowired
	UnfinishedRun unfinishedRun;

	@Test
	void unfinishedRunsAreInterruptedBeforeTheServerAcceptsConnections() {
		assertThat(unfinishedRun.statusWhenTheServerStarted).isEqualTo("interrupted");
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class Probe {

		@Bean
		UnfinishedRun unfinishedRun(JdbcTemplate jdbc) {
			return new UnfinishedRun(jdbc);
		}

	}

	/**
	 * A queued run written when this bean is created — after the migrations (Spring Boot creates the
	 * {@link JdbcTemplate} after Flyway) and before start-up recovery (every singleton is created before
	 * any of them is told that all exist) — and its status when the web server has started.
	 */
	static final class UnfinishedRun implements ApplicationListener<WebServerInitializedEvent> {

		private final JdbcTemplate jdbc;

		private final UUID runId;

		volatile @Nullable String statusWhenTheServerStarted;

		UnfinishedRun(JdbcTemplate jdbc) {
			this.jdbc = jdbc;
			this.runId = jdbc.queryForObject(
					"INSERT INTO pipeline_runs (kind, requested_by) VALUES ('retry', ?) RETURNING id", UUID.class,
					SystemAccount.SCHEDULER.id());
		}

		@Override
		public void onApplicationEvent(WebServerInitializedEvent event) {
			statusWhenTheServerStarted = jdbc.queryForObject("SELECT status FROM pipeline_runs WHERE id = ?",
					String.class, runId);
		}

	}

}
