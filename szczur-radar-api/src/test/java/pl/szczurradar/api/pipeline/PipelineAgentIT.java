package pl.szczurradar.api.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import pl.szczurradar.api.TestcontainersConfiguration;
import pl.szczurradar.api.agents.assessor.AssessmentResult;
import pl.szczurradar.api.agents.assessor.ContractFixtures;
import pl.szczurradar.api.agents.assessor.Prefilter;
import pl.szczurradar.api.agents.assessor.Provenance;
import pl.szczurradar.api.agents.assessor.contract.ContractJson;
import pl.szczurradar.api.cli.MigrateCommand;
import pl.szczurradar.api.config.LlmProviderProperties.Provider;
import pl.szczurradar.api.config.LlmProviderProperties.ServiceTier;
import pl.szczurradar.api.llm.Channel;
import pl.szczurradar.api.llm.LlmClient;
import pl.szczurradar.api.llm.LlmRequest;
import pl.szczurradar.api.llm.LlmResponse;
import pl.szczurradar.api.llm.OutputMode;
import pl.szczurradar.api.pipeline.run.PipelineRun;
import pl.szczurradar.api.pipeline.run.RunKind;
import pl.szczurradar.api.pipeline.run.RunStatus;
import pl.szczurradar.api.pipeline.state.PostTransitions;
import pl.szczurradar.api.pipeline.state.RunRecorder;
import pl.szczurradar.api.posts.Assessment;
import pl.szczurradar.api.sources.Source;
import pl.szczurradar.api.sources.wykop.LocalWykopServer;
import pl.szczurradar.api.sources.wykop.LocalWykopServer.Reply;
import pl.szczurradar.api.sources.wykop.WykopApiClient;
import pl.szczurradar.api.sources.wykop.WykopRepository;
import pl.szczurradar.api.user.Role;
import pl.szczurradar.api.user.User;
import pl.szczurradar.api.user.UserRepository;

/**
 * The whole run graph on the Embabel platform and Postgres: Wykop is a local server answering the
 * real, anonymized #gielda answers of 2026-10-01 (12 entries, 4 links), the LLM a scripted client.
 * The methods share one database and run in order.
 */
@SpringBootTest(properties = MigrateCommand.ENABLE_FLYWAY)
@Import({ TestcontainersConfiguration.class, PipelineAgentIT.LocalWykop.class })
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PipelineAgentIT {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private static final LocalDate DAY = LocalDate.parse("2026-10-01");

	/** The real last entries page served as the whole day ({@code pagination.total} set to its 12). */
	private static final String ENTRIES = withTotal(LocalWykopServer.fixture("search-entries-page.json"), 12);

	private static final LocalWykopServer WYKOP = new LocalWykopServer(request -> {
		if (request.path().endsWith("/auth")) {
			return Reply.ok("{\"data\":{\"token\":\"t\"}}");
		}
		return Reply.ok(request.path().endsWith("/search/entries") ? ENTRIES
				: LocalWykopServer.fixture("search-links-page.json"));
	});

	/** A valid V2 output without quotes: valid for any post. */
	private static final String VALID = ContractJson.MAPPER.writeValueAsString(Prefilter.noContentOutput());

	@MockitoBean
	LlmClient llmClient;

	@Autowired
	PipelineRunService service;

	@Autowired
	PipelineStartupRecovery recovery;

	@Autowired
	PostTransitions transitions;

	@Autowired
	RunRecorder recorder;

	@Autowired
	UserRepository users;

	@Autowired
	JdbcTemplate jdbc;

	private UUID admin;

	@BeforeEach
	void admin() {
		admin = users.findByEmail("pipeline-admin@example.com")
			.orElseGet(() -> users.save(new User("pipeline-admin@example.com", null, Role.ADMIN)))
			.getId();
		answer(request -> response(VALID));
	}

	@Test
	@Order(1)
	void aRangeRunFetchesStoresAndAssessesEveryPostOfTheDay() {
		List<PipelineRun> runs = service.startRange(DAY, DAY, List.of(), admin);

		assertThat(runs).extracting(PipelineRun::getKind).containsExactly(RunKind.RANGE);
		// No sources named: every configured one (here Wykop on the local server).
		assertThat(runs.getFirst().getSources()).containsExactly(Source.WYKOP);
		Map<String, Object> run = finished(runs.getFirst().getId());
		assertThat(run).containsEntry("status", "succeeded")
			.containsEntry("fetched", 16)
			.containsEntry("new_posts", 16)
			.containsEntry("assessed_ok", 16)
			.containsEntry("assessed_failed", 0)
			.containsEntry("processing_errors", 0)
			.containsEntry("skipped", 0);
		assertThat(jdbc.queryForList("SELECT step || ':' || status FROM pipeline_run_steps WHERE run_id = ?"
				+ " ORDER BY started_at, id", String.class, runs.getFirst().getId())).containsExactly("fetch:succeeded",
						"store:succeeded", "select:succeeded", "assess:succeeded", "summarize:succeeded");
		assertThat(count("SELECT count(*) FROM posts WHERE stage = 'assessed' AND first_run_id = ?", runs.getFirst().getId()))
			.isEqualTo(16);
		assertThat(count("SELECT count(*) FROM posts p JOIN raw_payloads r ON r.id = p.raw_payload_id", null)).isEqualTo(16);
		assertThat(count("SELECT count(*) FROM posts WHERE item_type = 'link'", null)).isEqualTo(4);
		assertThat(count("SELECT count(*) FROM assessments WHERE run_id = ? AND status = 'ok'", runs.getFirst().getId()))
			.isEqualTo(16);
	}

	@Test
	@Order(2)
	void theSameRangeAgainStoresNoDuplicatesAndAssessesNothingAssessed() {
		int assessmentsBefore = count("SELECT count(*) FROM assessments", null);

		List<PipelineRun> runs = service.startRange(DAY, DAY, List.of(), admin);

		assertThat(runs).extracting(PipelineRun::getKind).containsExactly(RunKind.RANGE);
		assertThat(finished(runs.getFirst().getId())).containsEntry("status", "succeeded")
			.containsEntry("fetched", 16)
			.containsEntry("new_posts", 0);
		assertThat(count("SELECT count(*) FROM posts", null)).isEqualTo(16);
		assertThat(count("SELECT count(*) FROM assessments", null)).isEqualTo(assessmentsBefore);
	}

	@Test
	@Order(3)
	void aLeftoverGetsOneAttemptPerRequestFromTheRetryRunOnly() {
		// A post of the day put back to stored (as after an interruption) whose assessment breaks.
		// Assessed by the model (not decided by the prefilter), so the client is called for it.
		UUID leftover = jdbc.queryForObject("SELECT p.id FROM posts p JOIN assessments a ON a.post_id = p.id"
				+ " WHERE p.item_type = 'post' AND a.result_source = 'model' ORDER BY p.published_at DESC, p.id LIMIT 1",
				UUID.class);
		String body = jdbc.queryForObject("SELECT body FROM posts WHERE id = ?", String.class, leftover);
		jdbc.update("UPDATE posts SET stage = 'stored' WHERE id = ?", leftover);
		int attemptsOfLeftover = count("SELECT count(*) FROM assessments WHERE post_id = ?", leftover);
		answer(request -> {
			if (request.userMessage().contains(MAPPER.writeValueAsString(body).replaceAll("^\"|\"$", ""))) {
				throw new IllegalStateException("a bug while assessing");
			}
			return response(VALID);
		});

		List<PipelineRun> runs = service.startRange(DAY, DAY, List.of(), admin);

		assertThat(runs).extracting(PipelineRun::getKind).containsExactly(RunKind.RETRY, RunKind.RANGE);
		Map<String, Object> retry = finished(runs.get(0).getId());
		assertThat(retry).containsEntry("status", "partial").containsEntry("processing_errors", 1);
		assertThat((String) retry.get("error")).contains(leftover.toString(), "IllegalStateException");
		assertThat(finished(runs.get(1).getId())).containsEntry("status", "succeeded").containsEntry("new_posts", 0);
		// One attempt per request: the range run did not take it again; no attempt was counted.
		assertThat(post(leftover))
			.containsEntry("stage", "stored")
			.containsEntry("assessment_attempts", 0)
			.containsEntry("assessing_run_id", null);
		assertThat(count("SELECT count(*) FROM assessments WHERE post_id = ?", leftover)).isEqualTo(attemptsOfLeftover);

		answer(request -> response(VALID));
		List<PipelineRun> next = service.startRange(DAY, DAY, List.of(), admin);

		assertThat(next).extracting(PipelineRun::getKind).containsExactly(RunKind.RETRY, RunKind.RANGE);
		assertThat(finished(next.get(0).getId())).containsEntry("status", "succeeded").containsEntry("assessed_ok", 1);
		finished(next.get(1).getId());
		assertThat(post(leftover).get("stage")).isEqualTo("assessed");
	}

	@Test
	@Order(4)
	void recoveryInterruptsUnfinishedRunsAndALateWorkerChangesNothing() {
		UUID taken = jdbc.queryForObject("SELECT id FROM posts ORDER BY id LIMIT 1", UUID.class);
		UUID other = jdbc.queryForObject("SELECT id FROM posts ORDER BY id DESC LIMIT 1", UUID.class);
		jdbc.update("UPDATE posts SET stage = 'stored' WHERE id IN (?, ?)", taken, other);
		UUID oldRun = jdbc.queryForObject("INSERT INTO pipeline_runs (kind, status, requested_by, started_at)"
				+ " VALUES ('retry', 'running', ?, now()) RETURNING id", UUID.class, admin);
		assertThat(transitions.claim(taken, oldRun)).contains(0);

		assertThat(recovery.recover()).containsExactly(oldRun);

		assertThat(run(oldRun).get("status"))
			.isEqualTo("interrupted");
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
		int assessments = count("SELECT count(*) FROM assessments", null);
		Assessment late = new Assessment(taken, oldRun, ContractFixtures.validInput(),
				new AssessmentResult(AssessmentResult.Status.OK, Prefilter.noContentOutput(), null, List.of(),
						Provenance.prefilter("V2", Instant.now()), null));
		assertThat(transitions.record(taken, oldRun, 0, late)).isFalse();
		transitions.release(taken, oldRun);
		assertThat(transitions.releaseAll(oldRun)).isZero();

		assertThat(count("SELECT count(*) FROM assessments", null)).isEqualTo(assessments);
		assertThat(post(taken))
			.containsEntry("stage", "assessing")
			.containsEntry("assessing_run_id", newRun)
			.containsEntry("assessment_attempts", 0);
		// Clean up: the new run ends; nothing stays assessing.
		assertThat(recorder.finish(newRun, RunStatus.SUCCEEDED, null)).isTrue();
		assertThat(transitions.releaseAll(newRun)).isEqualTo(1);
	}

	private Map<String, Object> post(UUID postId) {
		return jdbc.queryForMap("SELECT * FROM posts WHERE id = ?", postId);
	}

	private Map<String, Object> run(UUID runId) {
		return jdbc.queryForMap("SELECT * FROM pipeline_runs WHERE id = ?", runId);
	}

	/** Waits for the run to finish (the queue runs in the background) and returns its row. */
	private Map<String, Object> finished(UUID runId) {
		long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
		while (System.nanoTime() < deadline) {
			Map<String, Object> run = run(runId);
			if (!List.of("queued", "running").contains(run.get("status"))) {
				return run;
			}
			try {
				Thread.sleep(100);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(ex);
			}
		}
		throw new AssertionError("run " + runId + " did not finish: " + run(runId));
	}

	private int count(String sql, Object arg) {
		return arg == null ? jdbc.queryForObject(sql, Integer.class) : jdbc.queryForObject(sql, Integer.class, arg);
	}

	private void answer(Function<LlmRequest, LlmResponse> script) {
		doAnswer(invocation -> script.apply(invocation.getArgument(0))).when(llmClient).complete(any());
	}

	private static LlmResponse response(String raw) {
		return new LlmResponse(raw, Provider.GEMINI, "gemini-3.1-flash-lite", Channel.GEMINI, ServiceTier.DEFAULT, false,
				null, OutputMode.JSON_SCHEMA, 100, 50, null, 1);
	}

	private static String withTotal(String page, int total) {
		ObjectNode answer = (ObjectNode) MAPPER.readTree(page);
		((ObjectNode) answer.get("pagination")).put("total", total);
		return answer.toString();
	}

	/** Wykop on the local server, in place of the keys-based bean. */
	@TestConfiguration(proxyBeanMethods = false)
	static class LocalWykop {

		@Bean
		WykopRepository wykopRepository() {
			return new WykopRepository(new WykopApiClient(WYKOP.baseUrl(), "k", "s", Duration.ofSeconds(5)), duration -> {
			}, Duration.ZERO);
		}

	}

}
