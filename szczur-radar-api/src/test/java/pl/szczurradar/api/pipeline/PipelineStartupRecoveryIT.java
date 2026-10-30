package pl.szczurradar.api.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
import pl.szczurradar.api.agents.assessor.AssessmentResult;
import pl.szczurradar.api.agents.assessor.ContractFixtures;
import pl.szczurradar.api.agents.assessor.Prefilter;
import pl.szczurradar.api.agents.assessor.Provenance;
import pl.szczurradar.api.agents.assessor.contract.ItemType;
import pl.szczurradar.api.cli.MigrateCommand;
import pl.szczurradar.api.pipeline.run.RunStatus;
import pl.szczurradar.api.pipeline.state.PostTransitions;
import pl.szczurradar.api.pipeline.state.RunRecorder;
import pl.szczurradar.api.posts.Assessment;
import pl.szczurradar.api.posts.Post;
import pl.szczurradar.api.posts.PostRepository;
import pl.szczurradar.api.raw.RawPayloadStore;
import pl.szczurradar.api.sources.Source;
import pl.szczurradar.api.user.SystemAccount;

/**
 * Start-up recovery ends before the web server accepts connections: a run left queued by a stopped
 * instance is already {@code interrupted} when the server has opened its port
 * ({@link WebServerInitializedEvent}). Otherwise the request that wakes the API (the daily cron's
 * start) could create a run that recovery interrupts a moment later. Every run it interrupts notifies
 * the admins. A real server on a random port, as deployment {@value #DEPLOYMENT}
 * ({@code RAILWAY_DEPLOYMENT_ID}). And a recovery while the old worker still lives: it takes no new
 * post, and its late result, clean-up and release leave a post another run took alone.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { MigrateCommand.ENABLE_FLYWAY, "RAILWAY_DEPLOYMENT_ID=" + PipelineStartupRecoveryIT.DEPLOYMENT })
@Import({ TestcontainersConfiguration.class, PipelineStartupRecoveryIT.Probe.class })
class PipelineStartupRecoveryIT {

	/** This instance's deployment. */
	static final String DEPLOYMENT = "deployment-b";

	@Autowired
	UnfinishedRun unfinishedRun;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	PipelineStartupRecovery recovery;

	@Autowired
	PostTransitions transitions;

	@Autowired
	RunRecorder recorder;

	@Autowired
	PostRepository posts;

	@Autowired
	RawPayloadStore rawPayloads;

	@Test
	void unfinishedRunsAreInterruptedBeforeTheServerAcceptsConnections() {
		assertThat(unfinishedRun.statusWhenTheServerStarted).isEqualTo("interrupted");
	}

	@Test
	void aRunOfAnotherDeploymentWasInterruptedByTheDeployAnyOtherByARestart() {
		assertThat(run(unfinishedRun.ofAnotherDeployment)).containsEntry("status", "interrupted")
			.containsEntry("interruption_cause", "deploy")
			.containsEntry("error", "interrupted: a new deployment took over");
		assertThat(run(unfinishedRun.ofThisDeployment)).containsEntry("status", "interrupted")
			.containsEntry("interruption_cause", "restart")
			.containsEntry("error", "interrupted: the application restarted");
		// No deployment recorded (e.g. queued before RAILWAY_DEPLOYMENT_ID was read): a restart.
		assertThat(run(unfinishedRun.runId)).containsEntry("interruption_cause", "restart");
		// The step it was in is closed, not left running in a run that is over.
		assertThat(jdbc.queryForList("SELECT step || ':' || status || ':' || (finished_at IS NOT NULL)"
				+ " FROM pipeline_run_steps WHERE run_id = ? ORDER BY started_at, id", String.class,
				unfinishedRun.ofAnotherDeployment))
			.isEqualTo(List.of("select:succeeded:true", "assess:interrupted:true"));
	}

	@Test
	void everyInterruptedRunNotifiesTheAdminButNotTheScheduler() {
		for (UUID run : List.of(unfinishedRun.runId, unfinishedRun.ofAnotherDeployment,
				unfinishedRun.ofThisDeployment)) {
			assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE run_id = ? AND recipient_id = ?"
					+ " AND kind = 'run_attention' AND read_at IS NULL", Integer.class, run, unfinishedRun.admin))
				.as(run.toString())
				.isOne();
		}
		// The runs' requester is a system account, not an admin.
		assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE recipient_id = ?", Integer.class,
				SystemAccount.SCHEDULER.id())).isZero();
	}

	@Test
	void recoveryInterruptsUnfinishedRunsAndALateWorkerChangesNothing() {
		UUID admin = unfinishedRun.admin;
		UUID firstRun = jdbc.queryForObject("INSERT INTO pipeline_runs (kind, status, requested_by, started_at,"
				+ " finished_at) VALUES ('retry', 'succeeded', ?, now(), now()) RETURNING id", UUID.class, admin);
		UUID taken = post("90001", firstRun);
		UUID other = post("90002", firstRun);
		UUID oldRun = jdbc.queryForObject("INSERT INTO pipeline_runs (kind, status, requested_by, started_at)"
				+ " VALUES ('retry', 'running', ?, now()) RETURNING id", UUID.class, admin);
		assertThat(transitions.claim(taken, oldRun)).contains(0);

		assertThat(recovery.recover()).containsExactly(oldRun);

		// No deployment id on the run: a restart.
		assertThat(run(oldRun)).containsEntry("status", "interrupted")
			.containsEntry("interruption_cause", "restart")
			.containsEntry("error", "interrupted: the application restarted");
		assertThat(post(taken))
			.containsEntry("stage", "stored")
			.containsEntry("assessing_run_id", null);
		// The old worker, still alive: it takes no new post, and its summary changes nothing.
		assertThat(transitions.claim(other, oldRun)).isEmpty();
		assertThat(recorder.finish(oldRun, RunStatus.SUCCEEDED, null)).isFalse();

		// A new run takes the post; the old worker's late result, clean-up and release leave it alone.
		UUID newRun = jdbc.queryForObject("INSERT INTO pipeline_runs (kind, status, requested_by, started_at)"
				+ " VALUES ('retry', 'running', ?, now()) RETURNING id", UUID.class, admin);
		assertThat(transitions.claim(taken, newRun)).contains(0);
		int assessments = jdbc.queryForObject("SELECT count(*) FROM assessments", Integer.class);
		Assessment late = new Assessment(taken, oldRun, ContractFixtures.validInput(),
				new AssessmentResult(AssessmentResult.Status.OK, Prefilter.noContentOutput(), null, List.of(),
						Provenance.prefilter("V2", Instant.now()), null));
		assertThat(transitions.record(taken, oldRun, 0, late)).isFalse();
		transitions.release(taken, oldRun);
		assertThat(transitions.releaseAll(oldRun)).isZero();

		assertThat(jdbc.queryForObject("SELECT count(*) FROM assessments", Integer.class)).isEqualTo(assessments);
		assertThat(post(taken))
			.containsEntry("stage", "assessing")
			.containsEntry("assessing_run_id", newRun)
			.containsEntry("assessment_attempts", 0);
		// Clean up: the new run ends; nothing stays assessing.
		assertThat(recorder.finish(newRun, RunStatus.SUCCEEDED, null)).isTrue();
		assertThat(transitions.releaseAll(newRun)).isEqualTo(1);
	}

	/** A stored post of Wykop, first stored by {@code firstRun}. */
	private UUID post(String sourceItemId, UUID firstRun) {
		return posts.save(new Post(Source.WYKOP, ItemType.POST, sourceItemId, "gielda",
				Instant.parse("2026-10-01T08:00:00Z"), null, "treść", null, null, null, List.of("gielda"),
				rawPayloads.store(Source.WYKOP, ("{\"id\":" + sourceItemId + "}").getBytes(StandardCharsets.UTF_8)),
				firstRun)).getId();
	}

	private Map<String, Object> post(UUID postId) {
		return jdbc.queryForMap("SELECT stage, assessment_attempts, assessing_run_id FROM posts WHERE id = ?", postId);
	}

	private Map<String, Object> run(UUID runId) {
		return jdbc.queryForMap("SELECT status, interruption_cause, error FROM pipeline_runs WHERE id = ?", runId);
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
	 * any of them is told that all exist) — and its status when the web server has started. With it, a
	 * run of another deployment left running in its assess step, a queued run of this deployment, and an
	 * admin to notify.
	 */
	static final class UnfinishedRun implements ApplicationListener<WebServerInitializedEvent> {

		private final JdbcTemplate jdbc;

		private final UUID runId;

		private final UUID ofAnotherDeployment;

		private final UUID ofThisDeployment;

		private final UUID admin;

		volatile @Nullable String statusWhenTheServerStarted;

		UnfinishedRun(JdbcTemplate jdbc) {
			this.jdbc = jdbc;
			this.runId = jdbc.queryForObject(
					"INSERT INTO pipeline_runs (kind, requested_by) VALUES ('retry', ?) RETURNING id", UUID.class,
					SystemAccount.SCHEDULER.id());
			this.ofAnotherDeployment = jdbc.queryForObject("INSERT INTO pipeline_runs (kind, status, requested_by,"
					+ " started_at, deployment_id) VALUES ('retry', 'running', ?, now(), 'deployment-a') RETURNING id",
					UUID.class, SystemAccount.SCHEDULER.id());
			jdbc.update("INSERT INTO pipeline_run_steps (run_id, step, status, started_at, finished_at) VALUES (?,"
					+ " 'select', 'succeeded', now() - interval '1 minute', now())", ofAnotherDeployment);
			jdbc.update("INSERT INTO pipeline_run_steps (run_id, step) VALUES (?, 'assess')", ofAnotherDeployment);
			this.ofThisDeployment = jdbc.queryForObject("INSERT INTO pipeline_runs (kind, requested_by, deployment_id)"
					+ " VALUES ('retry', ?, ?) RETURNING id", UUID.class, SystemAccount.SCHEDULER.id(), DEPLOYMENT);
			this.admin = jdbc.queryForObject("INSERT INTO users (email, role) VALUES ('recovery-admin@example.com',"
					+ " 'admin') RETURNING id", UUID.class);
		}

		@Override
		public void onApplicationEvent(WebServerInitializedEvent event) {
			statusWhenTheServerStarted = jdbc.queryForObject("SELECT status FROM pipeline_runs WHERE id = ?",
					String.class, runId);
		}

	}

}
