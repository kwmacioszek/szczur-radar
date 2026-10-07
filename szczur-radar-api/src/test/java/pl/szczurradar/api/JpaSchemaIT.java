package pl.szczurradar.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

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
import pl.szczurradar.api.cli.MigrateCommand;
import pl.szczurradar.api.config.LlmProviderProperties.Provider;
import pl.szczurradar.api.config.LlmProviderProperties.ReasoningEffort;
import pl.szczurradar.api.config.LlmProviderProperties.ServiceTier;
import pl.szczurradar.api.llm.Channel;
import pl.szczurradar.api.llm.OutputMode;
import pl.szczurradar.api.pipeline.PipelineRun;
import pl.szczurradar.api.pipeline.PipelineRunRepository;
import pl.szczurradar.api.pipeline.PipelineRunStep;
import pl.szczurradar.api.pipeline.PipelineRunStepRepository;
import pl.szczurradar.api.pipeline.RunKind;
import pl.szczurradar.api.pipeline.RunStatus;
import pl.szczurradar.api.pipeline.RunStep;
import pl.szczurradar.api.pipeline.StepStatus;
import pl.szczurradar.api.posts.Assessment;
import pl.szczurradar.api.posts.AssessmentRepository;
import pl.szczurradar.api.posts.Post;
import pl.szczurradar.api.posts.PostRepository;
import pl.szczurradar.api.posts.PostStage;
import pl.szczurradar.api.raw.RawPayloadRef;
import pl.szczurradar.api.raw.RawPayloadStore;
import pl.szczurradar.api.sources.Source;
import pl.szczurradar.api.user.Role;
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
	RawPayloadStore rawPayloads;

	@Autowired
	PostRepository posts;

	@Autowired
	AssessmentRepository assessments;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void entitiesMatchTheMigratedSchemaAndRoundTrip() {
		User saved = users.save(new User("schema@example.com", null, Role.ADMIN));

		User loaded = users.findById(saved.getId()).orElseThrow();

		assertThat(loaded.getEmail()).isEqualTo("schema@example.com");
		assertThat(loaded.getPasswordHash()).isNull();
		assertThat(loaded.getRole()).isEqualTo(Role.ADMIN);
		assertThat(loaded.getCreatedAt()).isNotNull();
		assertThat(users.existsByRole(Role.ADMIN)).isTrue();
		assertThat(users.existsByEmail("schema@example.com")).isTrue();
	}

	@Test
	void aRunAPostWithItsRawPayloadAndItsAssessmentsRoundTrip() {
		User admin = users.save(new User("pipeline-schema@example.com", null, Role.ADMIN));
		Instant from = Instant.parse("2026-09-30T22:00:00Z");
		PipelineRun run = runs.save(PipelineRun.range(from, from.plus(1, ChronoUnit.DAYS).minusSeconds(1), admin.getId()));
		PipelineRun postsRun = runs.save(PipelineRun.posts(List.of(run.getId()), admin.getId()));
		steps.save(new PipelineRunStep(run.getId(), RunStep.FETCH));
		byte[] rawJson = "{\"id\":70001, \"votes\":{\"up\":3}}".getBytes(StandardCharsets.UTF_8);
		RawPayloadRef raw = rawPayloads.store(Source.WYKOP, rawJson);
		Post post = posts.save(new Post(Source.WYKOP, ItemType.POST, "70001", Instant.parse("2026-10-01T08:15:00Z"),
				null, "Treść wpisu #gielda", "uzytkownik1", "tresc-wpisu", "https://wykop.pl/wpis/70001/tresc-wpisu",
				List.of("gielda", "inwestycje"), raw, run.getId()));
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
		assertThat(loadedRun.getRequestedBy()).isEqualTo(admin.getId());
		assertThat(loadedRun.getCreatedAt()).isNotNull();
		assertThat(runs.findById(postsRun.getId()).orElseThrow().getRequestedPostIds()).containsExactly(run.getId());
		assertThat(steps.findByRunIdOrderByStartedAtAscIdAsc(run.getId())).singleElement().satisfies(step -> {
			assertThat(step.getStep()).isEqualTo(RunStep.FETCH);
			assertThat(step.getStatus()).isEqualTo(StepStatus.RUNNING);
		});

		Post loadedPost = posts.findById(post.getId()).orElseThrow();
		assertThat(loadedPost.getStage()).isEqualTo(PostStage.STORED);
		assertThat(loadedPost.getBody()).isEqualTo("Treść wpisu #gielda");
		assertThat(loadedPost.getTags()).containsExactly("gielda", "inwestycje");
		assertThat(loadedPost.getContentHash()).hasSize(64);
		assertThat(rawPayloads.load(loadedPost.getRawPayload())).isEqualTo(rawJson);
		// RAW stays out of the post row: the post holds only the reference.
		assertThat(jdbc.queryForObject("SELECT octet_length(payload) FROM raw_payloads WHERE id = ?", Integer.class,
				raw.id())).isPositive();

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

	private static Provenance modelProvenance() {
		return new Provenance(Provenance.Source.MODEL, Provider.OPENROUTER, "openai/gpt-6-luna", Channel.OPENAI_FLEX,
				ServiceTier.FLEX, true, ReasoningEffort.LOW, OutputMode.JSON_SCHEMA, "p1", "V2", ASSESSED_AT, 1200, 300,
				1024, 15_000, 2);
	}

}
