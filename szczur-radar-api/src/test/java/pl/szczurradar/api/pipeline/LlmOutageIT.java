package pl.szczurradar.api.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.convention.TestBean;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import pl.szczurradar.api.TestcontainersConfiguration;
import pl.szczurradar.api.cli.MigrateCommand;
import pl.szczurradar.api.llm.LlmClient;
import pl.szczurradar.api.llm.LlmClientFactory;
import pl.szczurradar.api.llm.LlmProviderProperties;
import pl.szczurradar.api.llm.Provider;
import pl.szczurradar.api.llm.ScriptedProviderServer;
import pl.szczurradar.api.llm.ScriptedProviderServer.Reply;
import pl.szczurradar.api.llm.ServiceTier;
import pl.szczurradar.api.pipeline.run.PipelineRun;
import pl.szczurradar.api.pipeline.run.RunKind;
import pl.szczurradar.api.sources.wykop.LocalWykopServer;
import pl.szczurradar.api.sources.wykop.WykopApiClient;
import pl.szczurradar.api.sources.wykop.WykopRepository;
import pl.szczurradar.api.user.Role;
import pl.szczurradar.api.user.User;
import pl.szczurradar.api.user.UserRepository;

/**
 * A provider outage through the real LLM client: the application's {@link LlmClient} is built by
 * {@link LlmClientFactory} (DeepSeek) on a local server that answers the check with a real DeepSeek
 * answer and every later request with HTTP 500. Each call goes through {@code TransportRetry} (its waits
 * skipped) and ends with {@code LlmUnavailableException}; after three posts in a row the breaker stops the
 * run {@code failed}, with no attempt and no technical failure on any post. The only test of the whole
 * chain transport → {@code Assessor} → dispatcher: a provider cannot be made to fail in a PR environment.
 * Wykop is a local server answering the real, anonymized #gielda answers of 2026-10-01; one LLM thread,
 * so the posts end in their order.
 */
@SpringBootTest(properties = { MigrateCommand.ENABLE_FLYWAY, "PIPELINE_LLM_CONCURRENCY=1" })
@Import({ TestcontainersConfiguration.class, LlmOutageIT.LocalWykop.class })
class LlmOutageIT {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private static final LocalDate DAY = LocalDate.parse("2026-10-01");

	/** The check's answer; once the script is used up, the server answers HTTP 500 to everything. */
	private static final ScriptedProviderServer PROVIDER = new ScriptedProviderServer(
			Reply.ok(ScriptedProviderServer.fixture("deepseek/chat-completion.json")));

	private static final LocalWykopServer WYKOP = new LocalWykopServer(request -> {
		if (request.path().endsWith("/auth")) {
			return LocalWykopServer.Reply.ok("{\"data\":{\"token\":\"t\"}}");
		}
		return LocalWykopServer.Reply.ok(request.path().endsWith("/search/entries")
				? withTotal(LocalWykopServer.fixture("search-entries-page.json"), 12)
				: LocalWykopServer.fixture("search-links-page.json"));
	});

	@TestBean
	LlmClient llmClient;

	@Autowired
	PipelineRunService service;

	@Autowired
	UserRepository users;

	@Autowired
	JdbcTemplate jdbc;

	/** The real client of the configured kind, on the local server, without waits between attempts. */
	static LlmClient llmClient() {
		return LlmClientFactory.create(new LlmProviderProperties(Provider.DEEPSEEK, "deepseek-v4-flash",
				"synthetic-key", ServiceTier.DEFAULT, null, LlmProviderProperties.MIN_TIMEOUT), PROVIDER.baseUrl(),
				duration -> {
				});
	}

	@AfterAll
	static void stopProvider() {
		PROVIDER.close();
	}

	@Test
	void threeCallsInARowWithoutTheProviderStopTheRunWithoutAnAttemptOrATechnicalFailure() {
		UUID admin = users.save(new User("outage-admin@example.com", null, Role.ADMIN)).getId();

		List<PipelineRun> created = service.startRange(DAY, DAY, List.of(), admin);

		assertThat(created).extracting(PipelineRun::getKind).containsExactly(RunKind.RANGE);
		UUID run = created.getFirst().getId();
		Map<String, Object> finished = finished(run);
		assertThat(finished).containsEntry("status", "failed")
			.containsEntry("new_posts", 16)
			.containsEntry("assessed_failed", 0)
			.containsEntry("processing_errors", 0);
		assertThat((String) finished.get("error")).contains("the LLM provider is unavailable: 3 calls in a row");
		// The check, then three posts of three HTTP attempts each: the posts after the third are not taken.
		assertThat(PROVIDER.requests()).hasSize(1 + 3 * 3);
		// No model assessment was stored, so no post spent an attempt; none counts a technical failure.
		assertThat(count("SELECT count(*) FROM assessments WHERE result_source = 'model'")).isZero();
		assertThat(count("SELECT count(*) FROM posts WHERE assessment_attempts > 0 OR technical_failures > 0"
				+ " OR last_technical_error IS NOT NULL")).isZero();
		assertThat(count("SELECT count(*) FROM posts WHERE stage IN ('assessing', 'failed', 'dead')")).isZero();
		assertThat(count("SELECT count(*) FROM posts WHERE stage = 'stored'")).isPositive();
		// The admin is notified: a failed run needs attention.
		assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE run_id = ? AND recipient_id = ?",
				Integer.class, run, admin)).isOne();
	}

	private int count(String sql) {
		return jdbc.queryForObject(sql, Integer.class);
	}

	/** Waits for the run to finish and to be reported (the queue runs in the background); returns its row. */
	private Map<String, Object> finished(UUID runId) {
		long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
		while (System.nanoTime() < deadline) {
			Map<String, Object> run = jdbc.queryForMap("SELECT * FROM pipeline_runs WHERE id = ?", runId);
			if (!List.of("queued", "running").contains(run.get("status")) && !service.ownRuns().contains(runId)) {
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
		throw new AssertionError("run " + runId + " did not finish: "
				+ jdbc.queryForMap("SELECT * FROM pipeline_runs WHERE id = ?", runId));
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
