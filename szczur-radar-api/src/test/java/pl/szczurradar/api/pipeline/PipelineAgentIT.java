package pl.szczurradar.api.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;

import com.embabel.agent.core.AgentPlatform;
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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
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
import pl.szczurradar.api.llm.LlmConfigurationException;
import pl.szczurradar.api.llm.LlmRequest;
import pl.szczurradar.api.llm.LlmResponse;
import pl.szczurradar.api.llm.OutputMode;
import pl.szczurradar.api.llm.transport.TransportException;
import pl.szczurradar.api.notifications.RunNotifications;
import pl.szczurradar.api.pipeline.run.PipelineRun;
import pl.szczurradar.api.pipeline.run.PipelineRunRepository;
import pl.szczurradar.api.pipeline.run.RunKind;
import pl.szczurradar.api.pipeline.run.RunStatus;
import pl.szczurradar.api.pipeline.state.PostTransitions;
import pl.szczurradar.api.pipeline.state.RunRecorder;
import pl.szczurradar.api.pipeline.step.SourceIngest;
import pl.szczurradar.api.posts.Assessment;
import pl.szczurradar.api.posts.PostRepository;
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
 * real, anonymized #gielda answers of 2026-10-01 (12 entries, 4 links), the LLM a scripted client. The
 * admin is notified about the runs that need attention, as their end, a failure or a closing instance
 * reports them. The methods share one database and run in order.
 */
@SpringBootTest(properties = MigrateCommand.ENABLE_FLYWAY)
@Import({ TestcontainersConfiguration.class, PipelineAgentIT.LocalWykop.class })
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@ExtendWith(OutputCaptureExtension.class)
class PipelineAgentIT {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private static final LocalDate DAY = LocalDate.parse("2026-10-01");

	/** The real last entries page served as the whole day ({@code pagination.total} set to its 12). */
	private static final String ENTRIES = withTotal(LocalWykopServer.fixture("search-entries-page.json"), 12);

	/** When set, Wykop's search answers 503 (a short synthetic error body in Wykop's shape). */
	private static volatile boolean wykopDown;

	private static final LocalWykopServer WYKOP = new LocalWykopServer(request -> {
		if (request.path().endsWith("/auth")) {
			return Reply.ok("{\"data\":{\"token\":\"t\"}}");
		}
		if (wykopDown) {
			return new Reply(503, "{\"error\":{\"code\":503,\"message\":\"Service unavailable\"}}");
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
	RunNotifications notifications;

	@Autowired
	RunRecorder recorder;

	@Autowired
	UserRepository users;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	PipelineRunRepository runs;

	@Autowired
	PostRepository posts;

	@Autowired
	SourceIngest sources;

	@Autowired
	AgentPlatform agentPlatform;

	@Autowired
	JdbcClient jdbcClient;

	@Autowired
	TransactionTemplate transaction;

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
			.containsEntry("skipped", 0)
			.containsEntry("llm_retries", 0)
			.containsEntry("flex_fallbacks", 0)
			.containsEntry("interruption_cause", null);
		assertThat(run.get("last_activity_at")).isNotNull();
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
		// The technical error needs attention; the run that succeeded does not.
		awaitClosed(runs.get(1).getId());
		assertThat(notified(runs.get(0).getId())).isOne();
		assertThat(notified(runs.get(1).getId())).isZero();
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

		// No deployment ids here (neither the run's nor this instance's): a restart.
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

	@Test
	@Order(5)
	void aFailingSourceFailsTheRunAndTheQueueTakesTheNextRequest() {
		List<PipelineRun> runs;
		wykopDown = true;
		try {
			runs = service.startRange(DAY, DAY, List.of(), admin);
			Map<String, Object> range = finished(runs.getLast().getId());

			assertThat(range).containsEntry("status", "failed").containsEntry("fetched", 0);
			assertThat((String) range.get("error")).isNotBlank();
			assertThat(jdbc.queryForList("SELECT step || ':' || status FROM pipeline_run_steps WHERE run_id = ?"
					+ " ORDER BY started_at, id", String.class, runs.getLast().getId())).containsExactly("fetch:failed");
			// The admin is notified once the run is closed.
			awaitClosed(runs.getLast().getId());
			assertThat(notified(runs.getLast().getId())).isOne();
		}
		finally {
			wykopDown = false;
		}

		List<PipelineRun> next = service.startRange(DAY, DAY, List.of(), admin);

		assertThat(finished(next.getLast().getId())).containsEntry("status", "succeeded");
		awaitClosed(next.getLast().getId());
		assertThat(notified(next.getLast().getId())).isZero();
	}

	@Test
	@Order(6)
	void recoveryPutsBackAPostClaimedJustAsItsRunWasInterrupted() {
		UUID claimed = jdbc.queryForObject("SELECT id FROM posts ORDER BY id LIMIT 1", UUID.class);
		jdbc.update("UPDATE posts SET stage = 'failed', assessment_attempts = 1 WHERE id = ?", claimed);
		UUID run = jdbc.queryForObject("INSERT INTO pipeline_runs (kind, status, requested_by, started_at)"
				+ " VALUES ('retry', 'running', ?, now()) RETURNING id", UUID.class, admin);
		assertThat(transitions.claim(claimed, run)).contains(1);
		// The stopping instance interrupted the run and put its posts back just before this claim
		// committed, then stopped before the worker could put the post back itself.
		jdbc.update("UPDATE pipeline_runs SET status = 'interrupted', finished_at = now() WHERE id = ?", run);

		assertThat(recovery.recover()).isEmpty();

		assertThat(post(claimed))
			.containsEntry("stage", "failed")
			.containsEntry("assessment_attempts", 1)
			.containsEntry("assessing_run_id", null);
	}

	@Test
	@Order(7)
	void aStoppingInstanceInterruptsItsRunAndPutsItsPostsBack() throws InterruptedException {
		// Assessed by the model (not decided by the prefilter), so the client is called for it.
		UUID post = jdbc.queryForObject("SELECT p.id FROM posts p WHERE p.stage = 'assessed' AND EXISTS"
				+ " (SELECT 1 FROM assessments a WHERE a.post_id = p.id AND a.result_source = 'model')"
				+ " ORDER BY p.id DESC LIMIT 1", UUID.class);
		jdbc.update("UPDATE posts SET stage = 'stored' WHERE id = ?", post);
		CountDownLatch called = new CountDownLatch(1);
		CountDownLatch answered = new CountDownLatch(1);
		answer(request -> {
			called.countDown();
			try {
				answered.await(30, TimeUnit.SECONDS);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
			return response(VALID);
		});
		// A second instance of the service, as during a deployment overlap; the shared one keeps running.
		PipelineRunService stopping = new PipelineRunService(runs, posts, sources, recorder, transitions, notifications,
				agentPlatform, jdbcClient, transaction, "deployment-stopping");
		try {
			UUID run = stopping.startPosts(List.of(post), admin).getFirst().getId();
			assertThat(called.await(30, TimeUnit.SECONDS)).isTrue();
			assertThat(post(post)).containsEntry("stage", "assessing").containsEntry("assessing_run_id", run);

			stopping.onClose();

			assertThat(run(run)).containsEntry("status", "interrupted")
				.containsEntry("interruption_cause", "shutdown")
				.containsEntry("error", "interrupted: the application stopped")
				.containsEntry("deployment_id", "deployment-stopping");
			// The step it was in is closed, not left running.
			assertThat(steps(run)).containsExactly("select:succeeded", "assess:interrupted");
			assertThat(jdbc.queryForObject("SELECT finished_at IS NOT NULL FROM pipeline_run_steps WHERE run_id = ?"
					+ " AND step = 'assess'", Boolean.class, run)).isTrue();
			assertThat(post(post)).containsEntry("stage", "stored").containsEntry("assessing_run_id", null);
			// The admin is notified while the instance closes.
			assertThat(notified(run)).isOne();
		}
		finally {
			answered.countDown();
		}
	}

	@Test
	@Order(8)
	void aRefusedLlmKeyFailsTheRunWithoutUsingAnAttempt() {
		UUID post = jdbc.queryForObject("SELECT id FROM posts WHERE stage = 'assessed' ORDER BY id LIMIT 1", UUID.class);
		jdbc.update("UPDATE posts SET stage = 'failed', assessment_attempts = 2 WHERE id = ?", post);
		int assessments = count("SELECT count(*) FROM assessments", null);
		when(llmClient.check()).thenThrow(new LlmConfigurationException("HTTP 401: Authentication Fails", 1,
				ServiceTier.DEFAULT, false,
				new TransportException(TransportException.Kind.CONFIGURATION, "HTTP 401", null, null)));

		UUID run = service.startPosts(List.of(post), admin).getFirst().getId();

		Map<String, Object> finished = finished(run);
		assertThat(finished).containsEntry("status", "failed").containsEntry("assessed_failed", 0);
		assertThat((String) finished.get("error")).contains("the LLM check failed", "HTTP 401");
		// Its last attempt is not spent on a bad key.
		assertThat(post(post))
			.containsEntry("stage", "failed")
			.containsEntry("assessment_attempts", 2)
			.containsEntry("assessing_run_id", null);
		assertThat(count("SELECT count(*) FROM assessments", null)).isEqualTo(assessments);
	}

	@Test
	@Order(9)
	void aRunCountsEachPostAsItIsAssessed() {
		List<UUID> ids = modelAssessedPosts(2);
		jdbc.update("UPDATE posts SET stage = 'stored' WHERE id IN (?, ?)", ids.get(0), ids.get(1));
		// The first call answers at once (after 3 HTTP attempts and a flex fallback), the second waits.
		AtomicInteger calls = new AtomicInteger();
		CountDownLatch answered = new CountDownLatch(1);
		answer(request -> {
			if (calls.getAndIncrement() > 0) {
				await(answered);
			}
			return response(VALID, 3, true);
		});
		try {
			UUID run = service.startPosts(ids, admin).getFirst().getId();

			// One post counted, the other one taken and waiting for its answer.
			Map<String, Object> during = until(run, row -> (int) row.get("assessed_ok") == 1 && calls.get() == 2);
			assertThat(during).containsEntry("status", "running")
				.containsEntry("assessed_failed", 0)
				.containsEntry("llm_retries", 2)
				.containsEntry("flex_fallbacks", 1);
			assertThat(during.get("last_activity_at")).isNotNull();
			assertThat(steps(run)).containsExactly("select:succeeded", "assess:running");
			assertThat(recorder.inFlight(run)).isEqualTo(1);

			answered.countDown();

			// Counted once each: the step adds no total at its end.
			assertThat(finished(run)).containsEntry("status", "succeeded")
				.containsEntry("assessed_ok", 2)
				.containsEntry("llm_retries", 4)
				.containsEntry("flex_fallbacks", 2);
			assertThat(recorder.inFlight(run)).isZero();
		}
		finally {
			answered.countDown();
		}
	}

	@Test
	@Order(10)
	void aRunInterruptedWhileAssessingGetsNoFurtherStepAndLogsNoError(CapturedOutput output) {
		UUID post = modelAssessedPosts(1).getFirst();
		jdbc.update("UPDATE posts SET stage = 'stored' WHERE id = ?", post);
		CountDownLatch called = new CountDownLatch(1);
		CountDownLatch answered = new CountDownLatch(1);
		answer(request -> {
			called.countDown();
			await(answered);
			return response(VALID);
		});
		try {
			UUID run = service.startPosts(List.of(post), admin).getFirst().getId();
			await(called);

			// A newer instance's recovery takes over while this one is still assessing (deployment overlap).
			assertThat(recovery.recover()).containsExactly(run);
			answered.countDown();

			awaitLog(output, "Pipeline run " + run + " is no longer running here");
			assertThat(run(run)).containsEntry("status", "interrupted").containsEntry("interruption_cause", "restart");
			// The late worker neither reopened the closed step nor added the summarize step.
			assertThat(steps(run)).containsExactly("select:succeeded", "assess:interrupted");
			assertThat(output.getAll()).contains("Pipeline run " + run + " (posts) ended interrupted in")
				.doesNotContain("Run " + run + " step summarize")
				.doesNotContain("Exception in thread");
			assertThat(output.getAll().lines().filter(line -> line.contains(" ERROR "))).isEmpty();
		}
		finally {
			answered.countDown();
		}
	}

	/** The admin's notifications about {@code runId}. */
	private int notified(UUID runId) {
		return jdbc.queryForObject("SELECT count(*) FROM notifications WHERE run_id = ? AND recipient_id = ?",
				Integer.class, runId, admin);
	}

	/** Waits until the shared service has closed {@code runId}: its end was reported to the notifications. */
	private void awaitClosed(UUID runId) {
		long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
		while (service.ownRuns().contains(runId)) {
			if (System.nanoTime() > deadline) {
				throw new AssertionError("run " + runId + " was never closed");
			}
			pause();
		}
	}

	/** {@code limit} posts now {@code assessed} whose assessment was made by the model (so a run calls the client). */
	private List<UUID> modelAssessedPosts(int limit) {
		return jdbc.queryForList("SELECT p.id FROM posts p WHERE p.stage = 'assessed' AND EXISTS"
				+ " (SELECT 1 FROM assessments a WHERE a.post_id = p.id AND a.result_source = 'model')"
				+ " ORDER BY p.id LIMIT ?", UUID.class, limit);
	}

	/** The run's steps as {@code step:status}, in the order they started. */
	private List<String> steps(UUID runId) {
		return jdbc.queryForList("SELECT step || ':' || status FROM pipeline_run_steps WHERE run_id = ?"
				+ " ORDER BY started_at, id", String.class, runId);
	}

	/** Waits until the run's row matches {@code condition} and returns it. */
	private Map<String, Object> until(UUID runId, Predicate<Map<String, Object>> condition) {
		long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
		while (System.nanoTime() < deadline) {
			Map<String, Object> run = run(runId);
			if (condition.test(run)) {
				return run;
			}
			pause();
		}
		throw new AssertionError("run " + runId + " never matched: " + run(runId));
	}

	/** Waits until the log has a line containing {@code text}. */
	private static void awaitLog(CapturedOutput output, String text) {
		long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
		while (!output.getAll().contains(text)) {
			if (System.nanoTime() > deadline) {
				throw new AssertionError("the log never said: " + text);
			}
			pause();
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

	private static void pause() {
		try {
			Thread.sleep(100);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(ex);
		}
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
		return response(raw, 1, false);
	}

	/** An answer after {@code attempts} HTTP attempts; after a refusal on the flex tier when {@code flexFallback}. */
	private static LlmResponse response(String raw, int attempts, boolean flexFallback) {
		return new LlmResponse(raw, Provider.GEMINI, "gemini-3.1-flash-lite", Channel.GEMINI, ServiceTier.DEFAULT,
				flexFallback, null, OutputMode.JSON_SCHEMA, 100, 50, null, attempts);
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
