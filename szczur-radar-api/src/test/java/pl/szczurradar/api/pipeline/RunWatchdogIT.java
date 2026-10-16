package pl.szczurradar.api.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import pl.szczurradar.api.TestcontainersConfiguration;
import pl.szczurradar.api.agents.assessor.Prefilter;
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
import pl.szczurradar.api.sources.wykop.LocalWykopServer;
import pl.szczurradar.api.sources.wykop.LocalWykopServer.Reply;
import pl.szczurradar.api.sources.wykop.WykopApiClient;
import pl.szczurradar.api.sources.wykop.WykopRepository;
import pl.szczurradar.api.user.Role;
import pl.szczurradar.api.user.User;
import pl.szczurradar.api.user.UserRepository;

/**
 * The watchdog on the whole run graph and Postgres with {@code PIPELINE_STALL_TIMEOUT=2s} (checked
 * every 400 ms): a run waiting for an LLM call that does not end is stopped and the run queued behind
 * it goes on; the stopped run's action, once its call ends, writes nothing; a slow run with progress is
 * not stopped; another instance's run is left alone. Wykop is a local server answering the real,
 * anonymized #gielda answers of 2026-10-01, the LLM a scripted client. The methods share one database
 * and run in order.
 */
@SpringBootTest(properties = { MigrateCommand.ENABLE_FLYWAY, PipelineStallSettings.NAME + "=2s",
		// The stopped run's action holds one thread until its call ends; the next run assesses on the other.
		"PIPELINE_LLM_CONCURRENCY=2" })
@Import({ TestcontainersConfiguration.class, RunWatchdogIT.LocalWykop.class })
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@ExtendWith(OutputCaptureExtension.class)
class RunWatchdogIT {

	private static final Duration TIMEOUT = Duration.ofSeconds(2);

	/** The run's and its open step's error after the watchdog stopped it ({@code Durations.format}). */
	private static final String STALLED = "no progress for 2.0s: the run was stopped";

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private static final LocalDate DAY = LocalDate.parse("2026-10-01");

	/** The real last entries page served as the whole day ({@code pagination.total} set to its 12). */
	private static final String ENTRIES = page(LocalWykopServer.fixture("search-entries-page.json"), 12, false);

	/** The real links page without its links: the day has none yet. */
	private static final String NO_LINKS = page(LocalWykopServer.fixture("search-links-page.json"), 0, true);

	/** When set, the day's 4 links are published: a range run stores them as new posts. */
	private static volatile boolean linksPublished;

	private static final LocalWykopServer WYKOP = new LocalWykopServer(request -> {
		if (request.path().endsWith("/auth")) {
			return Reply.ok("{\"data\":{\"token\":\"t\"}}");
		}
		if (request.path().endsWith("/search/entries")) {
			return Reply.ok(ENTRIES);
		}
		return Reply.ok(linksPublished ? LocalWykopServer.fixture("search-links-page.json") : NO_LINKS);
	});

	/** A valid V2 output without quotes: valid for any post. */
	private static final String VALID = ContractJson.MAPPER.writeValueAsString(Prefilter.noContentOutput());

	@MockitoBean
	LlmClient llmClient;

	@Autowired
	PipelineRunService service;

	@Autowired
	UserRepository users;

	@Autowired
	JdbcTemplate jdbc;

	private UUID admin;

	@BeforeEach
	void admin() {
		admin = users.findByEmail("watchdog-admin@example.com")
			.orElseGet(() -> users.save(new User("watchdog-admin@example.com", null, Role.ADMIN)))
			.getId();
		answer(request -> response(VALID));
	}

	@Test
	@Order(1)
	void aRunWaitingForACallThatDoesNotEndIsStoppedAndTheRunBehindItGoesOn(CapturedOutput output) {
		// The day's entries, stored and assessed.
		UUID first = service.startRange(DAY, DAY, List.of(), admin).getFirst().getId();
		assertThat(finished(first)).containsEntry("status", "succeeded").containsEntry("new_posts", 12);
		// One of them is a leftover with a failed attempt; the model is asked about it, and never answers.
		UUID leftover = jdbc.queryForObject("SELECT p.id FROM posts p WHERE EXISTS (SELECT 1 FROM assessments a"
				+ " WHERE a.post_id = p.id AND a.result_source = 'model') ORDER BY p.id LIMIT 1", UUID.class);
		jdbc.update("UPDATE posts SET stage = 'failed', assessment_attempts = 1 WHERE id = ?", leftover);
		String body = MAPPER.writeValueAsString(jdbc.queryForObject("SELECT body FROM posts WHERE id = ?",
				String.class, leftover)).replaceAll("^\"|\"$", "");
		CountDownLatch called = new CountDownLatch(1);
		CountDownLatch answered = new CountDownLatch(1);
		answer(request -> {
			if (request.userMessage().contains(body)) {
				called.countDown();
				await(answered);
			}
			return response(VALID);
		});
		linksPublished = true;
		try {
			List<PipelineRun> runs = service.startRange(DAY, DAY, List.of(), admin);
			assertThat(runs).extracting(PipelineRun::getKind).containsExactly(RunKind.RETRY, RunKind.RANGE);
			UUID retry = runs.get(0).getId();
			UUID range = runs.get(1).getId();
			await(called);

			assertThat(finished(retry)).containsEntry("status", "failed")
				.containsEntry("error", STALLED)
				.containsEntry("interruption_cause", null);
			// The step it was in is closed with the same error, not left running.
			assertThat(steps(retry)).containsExactly("select:succeeded::true", "assess:failed:" + STALLED + ":true");
			// The post is back without an attempt used.
			assertThat(post(leftover)).containsEntry("stage", "failed")
				.containsEntry("assessment_attempts", 1)
				.containsEntry("assessing_run_id", null);
			awaitLog(output, "Pipeline run " + retry + " made no progress for 2.0s; stopped");
			awaitLog(output, "Pipeline run " + retry + ": stopped waiting, the watchdog stopped the run");

			// The range run queued behind it starts and assesses its new posts (the links), while the
			// stopped run's call still holds a thread of the LLM pool.
			assertThat(finished(range)).containsEntry("status", "succeeded")
				.containsEntry("fetched", 16)
				.containsEntry("new_posts", 4)
				.containsEntry("assessed_ok", 4);
			assertThat(answered.getCount()).isOne();

			Supplier<List<Object>> retryState = () -> List.of(run(retry), steps(retry), post(leftover),
					count("SELECT count(*) FROM assessments WHERE post_id = ? OR run_id = ?", leftover, retry));
			List<Object> stopped = retryState.get();
			answered.countDown();

			// The stopped run's action gets its answer and ends its step, but writes nothing: no
			// assessment, counter, error or step.
			awaitLog(output, "Run " + retry + " step assess ended in");
			assertUnchanged(retryState, stopped);
			assertThat(run(retry)).containsEntry("assessed_ok", 0)
				.containsEntry("assessed_failed", 0)
				.containsEntry("processing_errors", 0);
			assertThat(output.getAll()).doesNotContain("Pipeline run " + retry + " failed");
		}
		finally {
			answered.countDown();
			linksPublished = false;
		}
	}

	@Test
	@Order(2)
	void aSlowRunWithProgressIsNotStopped(CapturedOutput output) {
		List<UUID> posts = jdbc.queryForList("SELECT p.id FROM posts p WHERE p.stage = 'assessed' AND EXISTS"
				+ " (SELECT 1 FROM assessments a WHERE a.post_id = p.id AND a.result_source = 'model')"
				+ " ORDER BY p.id LIMIT 6", UUID.class);
		assertThat(posts).hasSize(6);
		posts.forEach(post -> jdbc.update("UPDATE posts SET stage = 'stored' WHERE id = ?", post));
		// Every answer takes half the timeout: on two threads a post is counted about every second, and
		// the whole run takes about three.
		answer(request -> {
			pause(TIMEOUT.dividedBy(2));
			return response(VALID);
		});

		UUID run = service.startPosts(posts, admin).getFirst().getId();

		assertThat(finished(run)).containsEntry("status", "succeeded")
			.containsEntry("assessed_ok", 6)
			.containsEntry("error", null);
		// It outlived the timeout: its progress kept it going.
		assertThat(jdbc.queryForObject("SELECT EXTRACT(EPOCH FROM finished_at - started_at) FROM pipeline_runs"
				+ " WHERE id = ?", Double.class, run)).isGreaterThan(TIMEOUT.toSeconds());
		assertThat(output.getAll()).doesNotContain("Pipeline run " + run + " made no progress");
	}

	@Test
	@Order(3)
	void anotherInstancesRunIsLeftAlone(CapturedOutput output) {
		// Running without activity for an hour, but not queued by this instance: start-up recovery's.
		UUID foreign = jdbc.queryForObject("INSERT INTO pipeline_runs (kind, status, requested_by, started_at,"
				+ " last_activity_at, deployment_id) VALUES ('retry', 'running', ?, now() - interval '1 hour',"
				+ " now() - interval '1 hour', 'deployment-other') RETURNING id", UUID.class, admin);
		try {
			// Five checks.
			pause(TIMEOUT);

			assertThat(run(foreign)).containsEntry("status", "running")
				.containsEntry("finished_at", null)
				.containsEntry("error", null);
			assertThat(output.getAll()).doesNotContain(foreign.toString());
		}
		finally {
			jdbc.update("UPDATE pipeline_runs SET status = 'failed', finished_at = now() WHERE id = ?", foreign);
		}
	}

	/** The run's steps as {@code step:status:error:closed}, in the order they started. */
	private List<String> steps(UUID runId) {
		return jdbc.queryForList("SELECT step || ':' || status || ':' || COALESCE(error, '') || ':'"
				+ " || (finished_at IS NOT NULL) FROM pipeline_run_steps WHERE run_id = ? ORDER BY started_at, id",
				String.class, runId);
	}

	/** Checks for a second that {@code state} stays {@code expected}: a late worker would change it. */
	private static void assertUnchanged(Supplier<List<Object>> state, List<Object> expected) {
		long deadline = System.nanoTime() + Duration.ofSeconds(1).toNanos();
		while (System.nanoTime() < deadline) {
			assertThat(state.get()).isEqualTo(expected);
			pause(Duration.ofMillis(100));
		}
	}

	/** Waits until the log has a line containing {@code text}. */
	private static void awaitLog(CapturedOutput output, String text) {
		long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
		while (!output.getAll().contains(text)) {
			if (System.nanoTime() > deadline) {
				throw new AssertionError("the log never said: " + text);
			}
			pause(Duration.ofMillis(100));
		}
	}

	private static void await(CountDownLatch latch) {
		try {
			assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(ex);
		}
	}

	private static void pause(Duration duration) {
		try {
			Thread.sleep(duration);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(ex);
		}
	}

	private Map<String, Object> post(UUID postId) {
		return jdbc.queryForMap("SELECT stage, assessment_attempts, assessing_run_id FROM posts WHERE id = ?", postId);
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
			pause(Duration.ofMillis(100));
		}
		throw new AssertionError("run " + runId + " did not finish: " + run(runId));
	}

	private int count(String sql, Object... args) {
		return jdbc.queryForObject(sql, Integer.class, args);
	}

	private void answer(Function<LlmRequest, LlmResponse> script) {
		doAnswer(invocation -> script.apply(invocation.getArgument(0))).when(llmClient).complete(any());
	}

	private static LlmResponse response(String raw) {
		return new LlmResponse(raw, Provider.GEMINI, "gemini-3.1-flash-lite", Channel.GEMINI, ServiceTier.DEFAULT,
				false, null, OutputMode.JSON_SCHEMA, 100, 50, null, 1);
	}

	/** A real search page with {@code pagination.total} set to {@code total}; without elements when {@code empty}. */
	private static String page(String page, int total, boolean empty) {
		ObjectNode answer = (ObjectNode) MAPPER.readTree(page);
		((ObjectNode) answer.get("pagination")).put("total", total);
		if (empty) {
			answer.putArray("data");
		}
		return answer.toString();
	}

	/** Wykop on the local server, in place of the keys-based bean. */
	@TestConfiguration(proxyBeanMethods = false)
	static class LocalWykop {

		@Bean
		WykopRepository wykopRepository() {
			return new WykopRepository(new WykopApiClient(WYKOP.baseUrl(), "k", "s", Duration.ofSeconds(5)),
					duration -> {
					}, Duration.ZERO);
		}

	}

}
