package pl.szczurradar.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import pl.szczurradar.api.agents.assessor.AssessmentResult;
import pl.szczurradar.api.agents.assessor.ContractFixtures;
import pl.szczurradar.api.agents.assessor.Provenance;
import pl.szczurradar.api.agents.assessor.contract.AssessmentInputV1;
import pl.szczurradar.api.agents.assessor.contract.ItemType;
import pl.szczurradar.api.agents.assessor.validation.FailureClass;
import pl.szczurradar.api.agents.assessor.validation.Violation;
import pl.szczurradar.api.auth.activation.ActivationToken;
import pl.szczurradar.api.auth.activation.ActivationTokenRepository;
import pl.szczurradar.api.cli.MigrateCommand;
import pl.szczurradar.api.llm.Channel;
import pl.szczurradar.api.llm.OutputMode;
import pl.szczurradar.api.llm.Provider;
import pl.szczurradar.api.llm.ReasoningEffort;
import pl.szczurradar.api.llm.ServiceTier;
import pl.szczurradar.api.notifications.Notification;
import pl.szczurradar.api.notifications.NotificationKind;
import pl.szczurradar.api.notifications.NotificationRepository;
import pl.szczurradar.api.pipeline.run.InterruptionCause;
import pl.szczurradar.api.pipeline.run.PipelineRun;
import pl.szczurradar.api.pipeline.run.PipelineRunRepository;
import pl.szczurradar.api.pipeline.run.PipelineRunSource;
import pl.szczurradar.api.pipeline.run.PipelineRunSourceRepository;
import pl.szczurradar.api.pipeline.run.PipelineRunStep;
import pl.szczurradar.api.pipeline.run.PipelineRunStepRepository;
import pl.szczurradar.api.pipeline.run.RunKind;
import pl.szczurradar.api.pipeline.run.RunStatus;
import pl.szczurradar.api.pipeline.run.RunStep;
import pl.szczurradar.api.pipeline.run.SourceFetchStatus;
import pl.szczurradar.api.pipeline.run.StepStatus;
import pl.szczurradar.api.posts.Assessment;
import pl.szczurradar.api.posts.AssessmentRepository;
import pl.szczurradar.api.posts.Post;
import pl.szczurradar.api.posts.PostRepository;
import pl.szczurradar.api.posts.PostStage;
import pl.szczurradar.api.raw.RawPayloadRef;
import pl.szczurradar.api.raw.RawPayloadStore;
import pl.szczurradar.api.sources.Source;
import pl.szczurradar.api.user.Role;
import pl.szczurradar.api.user.SystemAccount;
import pl.szczurradar.api.user.User;
import pl.szczurradar.api.user.UserRepository;

/**
 * The JPA mapping matches the schema built by the Flyway migrations: Hibernate validates every
 * entity against the migrated database when this context starts (the server itself never touches
 * the database at boot and never changes the schema). A mismatch fails the context, so this test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
		MigrateCommand.ENABLE_FLYWAY, "spring.jpa.hibernate.ddl-auto=validate",
		"spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=true" })
@Import(TestcontainersConfiguration.class)
class JpaSchemaIT {

	private static final Instant ASSESSED_AT = Instant.parse("2026-10-06T10:00:00Z");

	@Autowired
	UserRepository users;

	@Autowired
	PipelineRunRepository runs;

	@Autowired
	PipelineRunStepRepository steps;

	@Autowired
	PipelineRunSourceRepository runSources;

	@Autowired
	RawPayloadStore rawPayloads;

	@Autowired
	PostRepository posts;

	@Autowired
	AssessmentRepository assessments;

	@Autowired
	ActivationTokenRepository tokens;

	@Autowired
	NotificationRepository notifications;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void entitiesMatchTheMigratedSchemaAndRoundTrip() {
		User saved = users.save(new User("schema@example.com", null, Role.ADMIN));

		User loaded = users.findById(saved.getId()).orElseThrow();

		assertThat(loaded.getEmail()).isEqualTo("schema@example.com");
		assertThat(loaded.getPasswordHash()).isNull();
		assertThat(loaded.getRole()).isEqualTo(Role.ADMIN);
		assertThat(loaded.isLoginEnabled()).isTrue();
		assertThat(loaded.getCreatedAt()).isNotNull();
		assertThat(users.existsByRole(Role.ADMIN)).isTrue();
		assertThat(users.existsByEmail("schema@example.com")).isTrue();
		User scheduler = users.findById(SystemAccount.SCHEDULER.id()).orElseThrow();
		assertThat(scheduler.getEmail()).isEqualTo(SystemAccount.SCHEDULER.email());
		assertThat(scheduler.isLoginEnabled()).isFalse();
	}

	@Test
	void aRunAPostWithItsRawPayloadAndItsAssessmentsRoundTrip() {
		User admin = users.save(new User("pipeline-schema@example.com", null, Role.ADMIN));
		Instant from = Instant.parse("2026-09-30T22:00:00Z");
		PipelineRun run = runs.save(PipelineRun.range(from, from.plus(1, ChronoUnit.DAYS).minusSeconds(1),
				List.of(Source.WYKOP), admin.getId(), "deployment-1"));
		PipelineRun postsRun = runs.save(PipelineRun.posts(List.of(run.getId()), admin.getId(), null));
		steps.save(new PipelineRunStep(run.getId(), RunStep.FETCH));
		byte[] rawJson = "{\"id\":70001, \"votes\":{\"up\":3}}".getBytes(StandardCharsets.UTF_8);
		RawPayloadRef raw = rawPayloads.store(Source.WYKOP, rawJson);
		Post post = posts.save(new Post(Source.WYKOP, ItemType.POST, "70001", "gielda",
				Instant.parse("2026-10-01T08:15:00Z"), null, "Treść wpisu #gielda", "uzytkownik1", "tresc-wpisu",
				"https://wykop.pl/wpis/70001/tresc-wpisu", List.of("gielda", "inwestycje"), raw, run.getId()));
		AssessmentInputV1 input = ContractFixtures.validInput();
		AssessmentResult ok = new AssessmentResult(AssessmentResult.Status.OK, ContractFixtures.validOutput(), null,
				List.of(), modelProvenance(), "{\"raw\":\"text\"}");
		AssessmentResult failed = new AssessmentResult(AssessmentResult.Status.FAILED, null, FailureClass.TIMEOUT,
				List.of(new Violation(FailureClass.TIMEOUT, "call", "$", "timed out")), new Provenance(
						Provenance.Source.MODEL, Provider.GEMINI, "gemini-3.1-flash-lite", null, ServiceTier.DEFAULT,
						false, null, null, "p1", "V2", ASSESSED_AT.minusSeconds(60), null, null, null, 120_000, 3),
				null);
		Assessment savedOk = assessments.save(new Assessment(post.getId(), run.getId(), input, ok));
		Assessment savedFailed = assessments.save(new Assessment(post.getId(), run.getId(), input, failed));

		PipelineRun loadedRun = runs.findById(run.getId()).orElseThrow();
		assertThat(loadedRun.getKind()).isEqualTo(RunKind.RANGE);
		assertThat(loadedRun.getStatus()).isEqualTo(RunStatus.QUEUED);
		assertThat(loadedRun.getRangeFrom()).isEqualTo(from);
		assertThat(loadedRun.getSources()).containsExactly(Source.WYKOP);
		assertThat(loadedRun.getRequestedBy()).isEqualTo(admin.getId());
		assertThat(loadedRun.getCreatedAt()).isNotNull();
		assertThat(loadedRun.getDeploymentId()).isEqualTo("deployment-1");
		// Progress and interruption are written by conditional updates only (pipeline/state/RunRecorder).
		assertThat(loadedRun.getLlmRetries()).isZero();
		assertThat(loadedRun.getFlexFallbacks()).isZero();
		assertThat(loadedRun.getLastActivityAt()).isNull();
		assertThat(loadedRun.getInterruptionCause()).isNull();
		assertThat(runs.findById(postsRun.getId()).orElseThrow().getRequestedPostIds()).containsExactly(run.getId());
		assertThat(runs.findById(postsRun.getId()).orElseThrow().getDeploymentId()).isNull();
		assertThat(steps.findByRunIdOrderByStartedAtAscIdAsc(run.getId())).singleElement().satisfies(step -> {
			assertThat(step.getStep()).isEqualTo(RunStep.FETCH);
			assertThat(step.getStatus()).isEqualTo(StepStatus.RUNNING);
		});
		jdbc.update("UPDATE pipeline_runs SET status = 'interrupted', interruption_cause = 'deploy', llm_retries = 2,"
				+ " flex_fallbacks = 1, last_activity_at = now() WHERE id = ?", run.getId());
		jdbc.update("UPDATE pipeline_run_steps SET status = 'interrupted', finished_at = now() WHERE run_id = ?",
				run.getId());
		PipelineRun interrupted = runs.findById(run.getId()).orElseThrow();
		assertThat(interrupted.getStatus()).isEqualTo(RunStatus.INTERRUPTED);
		assertThat(interrupted.getInterruptionCause()).isEqualTo(InterruptionCause.DEPLOY);
		assertThat(interrupted.getLlmRetries()).isEqualTo(2);
		assertThat(interrupted.getFlexFallbacks()).isEqualTo(1);
		assertThat(interrupted.getLastActivityAt()).isNotNull();
		assertThat(steps.findByRunIdOrderByStartedAtAscIdAsc(run.getId())).singleElement()
			.satisfies(step -> assertThat(step.getStatus()).isEqualTo(StepStatus.INTERRUPTED));

		Post loadedPost = posts.findById(post.getId()).orElseThrow();
		assertThat(loadedPost.getStage()).isEqualTo(PostStage.STORED);
		assertThat(loadedPost.getBody()).isEqualTo("Treść wpisu #gielda");
		assertThat(loadedPost.getTags()).containsExactly("gielda", "inwestycje");
		assertThat(loadedPost.getContentHash()).hasSize(64);
		assertThat(rawPayloads.load(loadedPost.getRawPayload())).isEqualTo(rawJson);
		// RAW stays out of the post row: the post holds only the reference.
		assertThat(jdbc.queryForMap("SELECT source, content_encoding, media_type, octet_length(payload) AS length"
				+ " FROM raw_payloads WHERE id = ?", raw.id())).containsEntry("source", "wykop")
			.containsEntry("content_encoding", "gzip")
			.containsEntry("media_type", "application/json")
			.hasEntrySatisfying("length", length -> assertThat((Integer) length).isPositive());

		Assessment loadedOk = assessments.findById(savedOk.getId()).orElseThrow();
		assertThat(loadedOk.getStatus()).isEqualTo(AssessmentResult.Status.OK);
		assertThat(loadedOk.getInput()).isEqualTo(input);
		assertThat(loadedOk.getOutput()).isEqualTo(ContractFixtures.validOutput());
		assertThat(loadedOk.getViolations()).isEmpty();
		assertThat(loadedOk.getRawText()).isEqualTo("{\"raw\":\"text\"}");
		assertThat(loadedOk.getProvenance()).isEqualTo(modelProvenance());
		Assessment loadedFailed = assessments.findById(savedFailed.getId()).orElseThrow();
		assertThat(loadedFailed.getFailureClass()).isEqualTo(FailureClass.TIMEOUT);
		assertThat(loadedFailed.getOutput()).isNull();
		assertThat(loadedFailed.getViolations()).isEqualTo(failed.violations());
		assertThat(loadedFailed.getProvenance()).isEqualTo(failed.provenance());
		assertThat(loadedFailed.getInputHash()).isEqualTo(loadedOk.getInputHash()).hasSize(64);
		// Enums are stored as their lower-case values (the CHECK sets), not the constant names.
		assertThat(jdbc.queryForMap("SELECT status, result_source, provider, channel, service_tier, reasoning_effort,"
				+ " output_mode FROM assessments WHERE id = ?", savedOk.getId())).containsValues("ok", "model",
						"openrouter", "openai/flex", "flex", "low", "json_schema");
	}

	@Test
	void aRunsSourcesResultsRoundTripAndRedditIsARunsSource() {
		User admin = users.save(new User("run-sources-schema@example.com", null, Role.ADMIN));
		Instant from = Instant.parse("2026-10-01T22:00:00Z");
		PipelineRun run = runs.save(PipelineRun.range(from, from.plus(1, ChronoUnit.DAYS).minusSeconds(1),
				List.of(Source.WYKOP, Source.REDDIT), admin.getId(), null));
		PipelineRunSource succeeded = runSources
			.save(new PipelineRunSource(run.getId(), Source.WYKOP, SourceFetchStatus.SUCCEEDED, 16, 2, null));
		PipelineRunSource failed = runSources.save(new PipelineRunSource(run.getId(), Source.REDDIT,
				SourceFetchStatus.FAILED, 0, 0, "RedditApiException: Reddit answered 503"));

		PipelineRun loadedRun = runs.findById(run.getId()).orElseThrow();
		assertThat(loadedRun.getSources()).containsExactly(Source.WYKOP, Source.REDDIT);
		// Written with the row by RunRecorder.source only.
		assertThat(loadedRun.getSourcesFailed()).isZero();
		assertThat(loadedRun.getSourcesEmpty()).isZero();
		assertThat(jdbc.queryForObject("SELECT array_to_string(sources, ',') FROM pipeline_runs WHERE id = ?",
				String.class, run.getId())).isEqualTo("wykop,reddit");
		jdbc.update("UPDATE pipeline_runs SET sources_failed = 1, sources_empty = 2 WHERE id = ?", run.getId());
		PipelineRun counted = runs.findById(run.getId()).orElseThrow();
		assertThat(counted.getSourcesFailed()).isEqualTo(1);
		assertThat(counted.getSourcesEmpty()).isEqualTo(2);

		assertThat(runSources.findByRunId(run.getId())).hasSize(2);
		PipelineRunSource loadedSucceeded = runSources.findById(succeeded.getId()).orElseThrow();
		assertThat(loadedSucceeded.getRunId()).isEqualTo(run.getId());
		assertThat(loadedSucceeded.getSource()).isEqualTo(Source.WYKOP);
		assertThat(loadedSucceeded.getStatus()).isEqualTo(SourceFetchStatus.SUCCEEDED);
		assertThat(loadedSucceeded.getFetched()).isEqualTo(16);
		assertThat(loadedSucceeded.getSkipped()).isEqualTo(2);
		assertThat(loadedSucceeded.getError()).isNull();
		assertThat(loadedSucceeded.getFinishedAt()).isNotNull();
		PipelineRunSource loadedFailed = runSources.findById(failed.getId()).orElseThrow();
		assertThat(loadedFailed.getSource()).isEqualTo(Source.REDDIT);
		assertThat(loadedFailed.getStatus()).isEqualTo(SourceFetchStatus.FAILED);
		assertThat(loadedFailed.getError()).isEqualTo("RedditApiException: Reddit answered 503");
		// Stored as the lower-case values (the CHECK sets).
		assertThat(jdbc.queryForList("SELECT source || ':' || status FROM pipeline_run_sources WHERE run_id = ?"
				+ " ORDER BY source", String.class, run.getId())).containsExactly("reddit:failed", "wykop:succeeded");
		assertThat(runSources.findByRunIdIn(List.of(run.getId(), UUID.randomUUID())))
			.extracting(PipelineRunSource::getId)
			.containsExactlyInAnyOrder(succeeded.getId(), failed.getId());
	}

	@Test
	void anActivationTokenRoundTrips() {
		User account = users.save(new User("token-schema@example.com", null, Role.USER));
		Instant expiresAt = Instant.parse("2026-10-13T06:00:00Z");
		tokens.upsert(account.getId(), "ab".repeat(32), expiresAt);

		ActivationToken loaded = tokens.findById(account.getId()).orElseThrow();

		assertThat(loaded.getUserId()).isEqualTo(account.getId());
		assertThat(loaded.getTokenHash()).isEqualTo("ab".repeat(32));
		assertThat(loaded.getExpiresAt()).isEqualTo(expiresAt);
		assertThat(loaded.getCreatedAt()).isNotNull();
	}

	@Test
	void aNotificationRoundTripsAndIsReadOnce() {
		User admin = users.save(new User("notification-schema@example.com", null, Role.ADMIN));
		PipelineRun run = runs.save(PipelineRun.retry(admin.getId(), null));
		jdbc.update("UPDATE pipeline_runs SET status = 'failed', finished_at = now() WHERE id = ?", run.getId());

		assertThat(notifications.notifyAdmins(NotificationKind.RUN_ATTENTION, run.getId())).isPositive();
		assertThat(notifications.notifyAdmins(NotificationKind.RUN_ATTENTION, run.getId())).isZero();

		Notification loaded = notifications.findLatest(admin.getId(), 10).getFirst();
		assertThat(loaded.getRecipientId()).isEqualTo(admin.getId());
		assertThat(loaded.getKind()).isEqualTo(NotificationKind.RUN_ATTENTION);
		assertThat(loaded.getRunId()).isEqualTo(run.getId());
		assertThat(loaded.getCreatedAt()).isNotNull();
		assertThat(loaded.getReadAt()).isNull();
		assertThat(notifications.countByRecipientIdAndReadAtIsNull(admin.getId())).isOne();
		assertThat(jdbc.queryForObject("SELECT kind FROM notifications WHERE id = ?", String.class, loaded.getId()))
			.isEqualTo("run_attention");

		assertThat(notifications.markRead(loaded.getId(), admin.getId())).isOne();
		Instant readAt = notifications.findById(loaded.getId()).orElseThrow().getReadAt();
		assertThat(readAt).isNotNull();
		assertThat(notifications.markRead(loaded.getId(), admin.getId())).isOne();
		assertThat(notifications.findById(loaded.getId()).orElseThrow().getReadAt()).isEqualTo(readAt);
		assertThat(notifications.countByRecipientIdAndReadAtIsNull(admin.getId())).isZero();
	}

	private static Provenance modelProvenance() {
		return new Provenance(Provenance.Source.MODEL, Provider.OPENROUTER, "openai/gpt-6-luna", Channel.OPENAI_FLEX,
				ServiceTier.FLEX, true, ReasoningEffort.LOW, OutputMode.JSON_SCHEMA, "p1", "V2", ASSESSED_AT, 1200, 300,
				1024, 15_000, 2);
	}

}
