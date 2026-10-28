package pl.szczurradar.api.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import pl.szczurradar.api.TestcontainersConfiguration;
import pl.szczurradar.api.cli.MigrateCommand;
import pl.szczurradar.api.internal.PipelineTriggerToken;
import pl.szczurradar.api.llm.LlmClient;
import pl.szczurradar.api.pipeline.run.InterruptionCause;
import pl.szczurradar.api.pipeline.run.PipelineRunRepository;
import pl.szczurradar.api.posts.AssessmentRepository;
import pl.szczurradar.api.posts.PostRepository;
import pl.szczurradar.api.posts.PostSeeds;
import pl.szczurradar.api.posts.PostStage;
import pl.szczurradar.api.raw.RawPayloadStore;
import pl.szczurradar.api.sources.wykop.LocalWykopServer;
import pl.szczurradar.api.sources.wykop.LocalWykopServer.Reply;
import pl.szczurradar.api.sources.wykop.WykopApiClient;
import pl.szczurradar.api.sources.wykop.WykopRepository;
import pl.szczurradar.api.user.Role;
import pl.szczurradar.api.user.User;
import pl.szczurradar.api.user.UserRepository;

/**
 * Orphaned runs on Postgres with {@code PIPELINE_STALL_TIMEOUT=60s}: a request to start runs, on an
 * instance without a run of its own, marks every queued and running run {@code interrupted} with the
 * cause {@code lost} when none of them showed activity for the timeout — all of them or none — and puts
 * back every post left {@code assessing} by an ended run, committed even when the request is then
 * refused (409, 400). "Another instance's" runs are rows inserted with SQL, as in {@code RunWatchdogIT}.
 * Wykop is a local server answering an empty day (a test can hold its answer to keep a run of this
 * instance active), and the LLM check fails, so a run takes no post; every test waits until this
 * instance has no run before it ends.
 *
 * <p>No test waits for the timeout: a dead run is an hour old ({@link #DEAD}), a live one was active (or
 * ended) now or 2 s ago, so the timeout is far from any test's length, and the watchdog never stops a run
 * of this instance a test holds in its fetch step. A held search waits for the gate without a bound; the
 * clean-up ({@link #awaitIdle}) opens it, after a red test too, and a test that hangs ends at the class's
 * {@link Timeout}, with a dump of every thread in the log ({@code junit-platform.properties}).
 */
@SpringBootTest(properties = { MigrateCommand.ENABLE_FLYWAY, PipelineStallSettings.NAME + "=60s",
		PipelineTriggerToken.NAME + "=" + OrphanedRunIT.TOKEN })
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, OrphanedRunIT.LocalWykop.class })
@ExtendWith(OutputCaptureExtension.class)
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class OrphanedRunIT {

	/** A test-only token, as long as {@code openssl rand -hex 32} makes one. */
	static final String TOKEN = "4b7d1f3a5c9e2b6d8f0a1c3e5b7d9f2a4c6e8b0d1f3a5c7e9b2d4f6a8c0e1b3d";

	private static final String DAILY = "/internal/pipeline-runs/daily";

	private static final String BUSY = "{\"detail\":\"pipeline_busy\"}";

	/** An hour without activity: far older than the timeout. */
	private static final String DEAD = "now() - interval '1 hour'";

	/** Closed while a test needs a run of this instance to stay active; Wykop's searches wait for it. */
	static volatile CountDownLatch wykopGate = new CountDownLatch(0);

	private static final LocalWykopServer WYKOP = new LocalWykopServer(request -> {
		if (request.path().endsWith("/auth")) {
			return Reply.ok("{\"data\":{\"token\":\"t\"}}");
		}
		try {
			// Without a bound: the test or its clean-up opens the gate.
			wykopGate.await();
		}
		catch (InterruptedException ex) {
			// Never an empty day for a held search: the fetch fails, as when Wykop is down.
			Thread.currentThread().interrupt();
			return new Reply(503, "{\"error\":{\"code\":503,\"message\":\"the test's gate was interrupted\"}}");
		}
		return Reply.ok("{\"data\":[],\"pagination\":{\"total\":0}}");
	});

	@MockitoBean
	LlmClient llmClient;

	@Autowired
	PipelineRunService service;

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	PostRepository posts;

	@Autowired
	AssessmentRepository assessments;

	@Autowired
	RawPayloadStore rawPayloads;

	@Autowired
	PipelineRunRepository runs;

	@Autowired
	UserRepository users;

	@Autowired
	EntityManager entityManager;

	private UUID admin;

	private PostSeeds seeds;

	@BeforeEach
	void setUp() {
		admin = users.save(new User("orphans-" + UUID.randomUUID() + "@example.com", null, Role.ADMIN)).getId();
		seeds = new PostSeeds(posts, assessments, rawPayloads, runs, users, jdbc, entityManager);
		// A retry run behind the clean-up takes no post: the post put back stays as the clean-up left it.
		doThrow(new IllegalStateException("no LLM call in this test")).when(llmClient).check();
	}

	@AfterEach
	void awaitIdle() {
		wykopGate.countDown();
		long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
		while (!service.ownRuns().isEmpty()) {
			assertThat(System.nanoTime()).as("runs of this instance still active").isLessThan(deadline);
			sleep(100);
		}
		// Rows of "another instance" a test left active, and leftovers that would add a retry run.
		jdbc.update("UPDATE pipeline_runs SET status = 'failed', finished_at = now() WHERE status IN ('queued',"
				+ " 'running')");
		// A run that ended within the timeout means a living queue: the next test's dead rows would stay.
		jdbc.update("UPDATE pipeline_runs SET finished_at = " + DEAD + " WHERE finished_at IS NOT NULL");
		jdbc.update("UPDATE posts SET stage = 'dead', assessment_attempts = 3, assessing_run_id = NULL"
				+ " WHERE stage IN ('stored', 'failed', 'assessing')");
	}

	@Test
	void deadRunsOfAnotherInstanceAreInterruptedAsLostAndTheRequestGoesOn(CapturedOutput output) throws Exception {
		UUID running = foreignRunning(DEAD);
		jdbc.update("INSERT INTO pipeline_run_steps (run_id, step, started_at, items_in) VALUES (?, 'assess', "
				+ DEAD + ", 1)", running);
		UUID held = heldBy(running);
		UUID queued = foreignQueued(DEAD);

		MvcTestResult result = daily();

		assertThat(result).hasStatus(HttpStatus.ACCEPTED);
		for (UUID run : List.of(running, queued)) {
			assertThat(run(run)).containsEntry("status", "interrupted")
				.containsEntry("interruption_cause", InterruptionCause.LOST.value())
				.containsEntry("error", InterruptionCause.LOST.error());
			assertThat(run(run).get("finished_at")).isNotNull();
		}
		assertThat(jdbc.queryForList("SELECT status || ':' || (finished_at IS NOT NULL) FROM pipeline_run_steps"
				+ " WHERE run_id = ?", String.class, running)).containsExactly("interrupted:true");
		assertThat(post(held)).containsEntry("stage", "stored").containsEntry("assessing_run_id", null);
		// The admin is notified about both, once each.
		assertThat(notified(running) + notified(queued)).isEqualTo(2);
		assertThat(warning(output, "Marked 2 orphaned pipeline run(s) interrupted: no queued or running run showed"
				+ " activity for 1m 00s and none runs on this instance: ")).contains(running.toString(),
						queued.toString());
		assertThat(warning(output, "Put back 1 post(s) left assessing by ended runs")).isNotBlank();
		// The new runs: a retry run for the post put back, then the day's range.
		List<String> kinds = JsonPath.read(result.getResponse().getContentAsString(), "$[*].kind");
		assertThat(kinds).containsExactly("retry", "range");
		List<String> ids = JsonPath.read(result.getResponse().getContentAsString(), "$[*].id");
		assertThat(ids).doesNotContain(running.toString(), queued.toString());
	}

	@Test
	void aLoneOrphanedQueuedRunIsInterrupted() {
		UUID queued = foreignQueued(DEAD);

		assertThat(daily()).hasStatus(HttpStatus.ACCEPTED);

		assertThat(run(queued)).containsEntry("status", "interrupted")
			.containsEntry("interruption_cause", InterruptionCause.LOST.value());
	}

	@Test
	void aQueuedRunBehindARunThatJustEndedIsLeftAlone() {
		// Between the end of a long run and the start of the one queued with it: the queue is alive.
		jdbc.update("INSERT INTO pipeline_runs (kind, status, requested_by, created_at, started_at, last_activity_at,"
				+ " finished_at, deployment_id) VALUES ('retry', 'succeeded', ?, " + DEAD + ", " + DEAD + ", " + DEAD
				+ ", now(), 'deployment-other')", admin);
		UUID queued = foreignQueued(DEAD);
		Map<String, Object> before = run(queued);

		assertThat(daily()).hasStatus(HttpStatus.CONFLICT).bodyJson().isStrictlyEqualTo(BUSY);

		assertThat(run(queued)).isEqualTo(before).containsEntry("status", "queued");
	}

	@Test
	void anotherInstancesRunWithActivityIsLeftAlone() {
		UUID running = foreignRunning("now()");
		Map<String, Object> before = run(running);

		assertThat(daily()).hasStatus(HttpStatus.CONFLICT).bodyJson().isStrictlyEqualTo(BUSY);

		assertThat(run(running)).isEqualTo(before).containsEntry("status", "running");
	}

	@Test
	void oneActiveRunWithActivityKeepsEveryRunAsItIs() {
		UUID running = foreignRunning(DEAD);
		UUID queued = foreignQueued("now()");
		Map<String, Object> runningBefore = run(running);
		Map<String, Object> queuedBefore = run(queued);

		assertThat(daily()).hasStatus(HttpStatus.CONFLICT).bodyJson().isStrictlyEqualTo(BUSY);

		assertThat(run(running)).isEqualTo(runningBefore);
		assertThat(run(queued)).isEqualTo(queuedBefore);
	}

	@Test
	void activityWithinTheTimeoutKeepsTheRun() {
		// 2 s ago, with a timeout of 60 s.
		UUID running = foreignRunning("now() - interval '2 seconds'");
		Map<String, Object> before = run(running);

		assertThat(daily()).hasStatus(HttpStatus.CONFLICT).bodyJson().isStrictlyEqualTo(BUSY);

		assertThat(run(running)).isEqualTo(before);
	}

	@Test
	void anInstanceWithARunOfItsOwnCleansUpNothing() throws Exception {
		wykopGate = new CountDownLatch(1);
		MvcTestResult first = daily();
		assertThat(first).hasStatus(HttpStatus.ACCEPTED);
		UUID own = UUID.fromString(JsonPath.read(first.getResponse().getContentAsString(), "$[0].id"));
		awaitFetching(own);
		// Queued (one run only may be running) by another instance an hour ago.
		UUID foreign = foreignQueued(DEAD);
		Map<String, Object> before = run(foreign);

		assertThat(daily()).hasStatus(HttpStatus.CONFLICT).bodyJson().isStrictlyEqualTo(BUSY);

		assertThat(run(foreign)).isEqualTo(before);
		assertThat(run(own)).containsEntry("status", "running");
	}

	@Test
	void aPostLeftAssessingIsPutBackThoughTheRequestIsBusy() {
		UUID ended = foreignRunning(DEAD);
		UUID held = heldBy(ended);
		jdbc.update("UPDATE pipeline_runs SET status = 'interrupted', finished_at = now() WHERE id = ?", ended);
		UUID running = foreignRunning("now()");

		assertThat(daily()).hasStatus(HttpStatus.CONFLICT).bodyJson().isStrictlyEqualTo(BUSY);

		// Committed although the request was refused.
		assertThat(post(held)).containsEntry("stage", "stored").containsEntry("assessing_run_id", null);
		assertThat(run(running)).containsEntry("status", "running");
	}

	@Test
	void aPostLeftAssessingIsPutBackThoughTheRequestNamesADeadPost() {
		UUID ended = foreignRunning(DEAD);
		UUID held = heldBy(ended);
		jdbc.update("UPDATE pipeline_runs SET status = 'interrupted', finished_at = now() WHERE id = ?", ended);
		UUID dead = seeds.post(PostStage.DEAD, 3, "2026-10-01T09:00:00Z");

		assertThat(adminStart("{\"postIds\":[\"" + dead + "\"]}")).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.isStrictlyEqualTo("{\"detail\":\"post_dead\"}");

		assertThat(post(held)).containsEntry("stage", "stored").containsEntry("assessing_run_id", null);
	}

	@Test
	void anAdminsRequestCleansUpTheSameWay() {
		UUID running = foreignRunning(DEAD);
		String yesterday = LocalDate.now(PipelineRunService.ZONE).minusDays(1).toString();

		assertThat(adminStart("{\"from\":\"" + yesterday + "\",\"to\":\"" + yesterday + "\"}"))
			.hasStatus(HttpStatus.ACCEPTED);

		assertThat(run(running)).containsEntry("status", "interrupted")
			.containsEntry("interruption_cause", InterruptionCause.LOST.value());
		assertThat(notified(running)).isOne();
	}

	/** A running run of another deployment, started with its last activity at {@code activity} (SQL). */
	private UUID foreignRunning(String activity) {
		return jdbc.queryForObject("INSERT INTO pipeline_runs (kind, status, requested_by, created_at, started_at,"
				+ " last_activity_at, deployment_id) VALUES ('retry', 'running', ?, " + activity + ", " + activity
				+ ", " + activity + ", 'deployment-other') RETURNING id", UUID.class, admin);
	}

	/** A run of another deployment queued at {@code created} (SQL). */
	private UUID foreignQueued(String created) {
		return jdbc.queryForObject("INSERT INTO pipeline_runs (kind, status, requested_by, created_at, deployment_id)"
				+ " VALUES ('retry', 'queued', ?, " + created + ", 'deployment-other') RETURNING id", UUID.class,
				admin);
	}

	/** A post {@code runId} holds in assessment. */
	private UUID heldBy(UUID runId) {
		UUID post = seeds.post(PostStage.STORED, 0, "2026-10-01T08:00:00Z");
		jdbc.update("UPDATE posts SET stage = 'assessing', assessing_run_id = ? WHERE id = ?", runId, post);
		return post;
	}

	private MvcTestResult daily() {
		return mvc.post().uri(DAILY).header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN).exchange();
	}

	private MvcTestResult adminStart(String body) {
		return mvc.post()
			.uri("/api/admin/pipeline-runs")
			.with(user(admin.toString()).roles(Role.ADMIN.name()))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.exchange();
	}

	/** Waits until {@code runId} is in its fetch step, held by Wykop. */
	private void awaitFetching(UUID runId) {
		long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
		while (jdbc.queryForObject("SELECT count(*) FROM pipeline_run_steps WHERE run_id = ? AND step = 'fetch'"
				+ " AND status = 'running'", Integer.class, runId) == 0) {
			assertThat(System.nanoTime()).as("run " + runId + " never fetched").isLessThan(deadline);
			sleep(50);
		}
	}

	/** The log's WARN line containing {@code text}. */
	private static String warning(CapturedOutput output, String text) {
		return output.getAll()
			.lines()
			.filter(line -> line.contains(text) && line.contains("WARN"))
			.findFirst()
			.orElseThrow(() -> new AssertionError("no WARN line: " + text));
	}

	/** The admin's notifications about {@code runId}. */
	private int notified(UUID runId) {
		return jdbc.queryForObject("SELECT count(*) FROM notifications WHERE run_id = ? AND recipient_id = ?",
				Integer.class, runId, admin);
	}

	private Map<String, Object> run(UUID runId) {
		return jdbc.queryForMap("SELECT * FROM pipeline_runs WHERE id = ?", runId);
	}

	private Map<String, Object> post(UUID postId) {
		return jdbc.queryForMap("SELECT stage, assessing_run_id FROM posts WHERE id = ?", postId);
	}

	private static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(ex);
		}
	}

	/** Wykop on the local server, in place of the keys-based bean. */
	@TestConfiguration(proxyBeanMethods = false)
	static class LocalWykop {

		@Bean
		WykopRepository wykopRepository() {
			return new WykopRepository(new WykopApiClient(WYKOP.baseUrl(), "k", "s", Duration.ofSeconds(60)),
					duration -> {
					}, Duration.ZERO);
		}

	}

}
