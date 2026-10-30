package pl.szczurradar.api.pipeline.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import pl.szczurradar.api.TestcontainersConfiguration;
import pl.szczurradar.api.cli.MigrateCommand;
import pl.szczurradar.api.config.SessionConfig;
import pl.szczurradar.api.internal.PipelineTriggerToken;
import pl.szczurradar.api.llm.LlmClient;
import pl.szczurradar.api.pipeline.run.PipelineRun;
import pl.szczurradar.api.pipeline.run.PipelineRunRepository;
import pl.szczurradar.api.pipeline.run.RunKind;
import pl.szczurradar.api.sources.LocalApiServer.Reply;
import pl.szczurradar.api.sources.Source;
import pl.szczurradar.api.sources.wykop.LocalWykopServer;
import pl.szczurradar.api.sources.wykop.WykopApiClient;
import pl.szczurradar.api.sources.wykop.WykopRepository;
import pl.szczurradar.api.user.Role;
import pl.szczurradar.api.user.SystemAccount;
import pl.szczurradar.api.user.User;
import pl.szczurradar.api.user.UserRepository;

/**
 * The daily cron's routes ({@code /internal/pipeline-runs}) against the real queue: the trigger token
 * and only the token (no session accepted, none created, no cookie set), the token meaning nothing on
 * {@code /api/**}, the daily range on a fixed clock, a run's state with its step and progress, and the
 * admin's refusals. Runs execute in the
 * background with Wykop on a local server answering an empty day, so a run fails at its fetch step (no
 * post to store); a test can hold the answer to keep a run active. Every test waits until no run is
 * active before it ends.
 *
 * <p>A held search waits for the gate without a bound; the clean-up ({@link #awaitIdle}) opens it, after a
 * red test too, and a test that hangs ends at the class's {@link Timeout}, with a dump of every thread in
 * the log ({@code junit-platform.properties}).
 */
@SpringBootTest(properties = { MigrateCommand.ENABLE_FLYWAY,
		PipelineTriggerToken.NAME + "=" + InternalPipelineRunIT.TOKEN })
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, InternalPipelineRunIT.TestBeans.class })
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class InternalPipelineRunIT {

	/** A test-only token, as long as {@code openssl rand -hex 32} makes one. */
	static final String TOKEN = "9f2c4e6a8b0d1f3e5a7c9e1b3d5f7a9c0e2b4d6f8a1c3e5b7d9f0a2c4e6b8d0f";

	private static final String DAILY = "/internal/pipeline-runs/daily";

	private static final String NOT_AUTHENTICATED = "{\"detail\":\"not_authenticated\"}";

	private static final String PASSWORD = "correct horse battery";

	/** Closed while a test needs a run to stay active; Wykop's searches wait for it. */
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

	/** 01:30 in Warsaw on 26 October 2026, the night after the autumn clock change. */
	private static final MutableClock CLOCK = new MutableClock(Instant.parse("2026-10-26T00:30:00Z"));

	/** No post is assessed here; never a real LLM call. */
	@MockitoBean
	LlmClient llmClient;

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	PipelineRunRepository runs;

	@Autowired
	UserRepository users;

	@Autowired
	PasswordEncoder passwordEncoder;

	@AfterEach
	void awaitIdle() {
		wykopGate.countDown();
		long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
		while (activeRuns() > 0) {
			assertThat(System.nanoTime()).as("runs still active").isLessThan(deadline);
			sleep(100);
		}
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = { "", "Bearer ", "Bearer wrong", "Bearer " + TOKEN + "0", "Basic " + TOKEN, TOKEN })
	void withoutTheTokenEveryInternalRouteIs401AndCreatesNothing(@Nullable String authorization) {
		int before = runCount();

		assertThat(internal(HttpMethod.POST, DAILY, authorization, null)).hasStatus(HttpStatus.UNAUTHORIZED)
			.bodyJson()
			.isStrictlyEqualTo(NOT_AUTHENTICATED);
		assertThat(internal(HttpMethod.GET, "/internal/pipeline-runs/" + UUID.randomUUID(), authorization, null))
			.hasStatus(HttpStatus.UNAUTHORIZED)
			.bodyJson()
			.isStrictlyEqualTo(NOT_AUTHENTICATED);
		assertThat(runCount()).isEqualTo(before);
	}

	@Test
	void anAdminSessionIsNotAcceptedOnInternalRoutes() {
		String email = "internal-admin-" + UUID.randomUUID() + "@example.com";
		users.save(new User(email, passwordEncoder.encode(PASSWORD), Role.ADMIN));
		MvcTestResult login = mvc.post()
			.uri("/api/auth/login")
			.header("X-Real-IP", "192.0.2.20")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD))
			.exchange();
		assertThat(login).hasStatusOk();
		Cookie session = login.getResponse().getCookie(SessionConfig.COOKIE_NAME);
		assertThat(session).isNotNull();
		assertThat(mvc.get().uri("/api/auth/me").cookie(session)).hasStatusOk();
		int before = runCount();

		assertThat(internal(HttpMethod.POST, DAILY, null, session)).hasStatus(HttpStatus.UNAUTHORIZED)
			.bodyJson()
			.isStrictlyEqualTo(NOT_AUTHENTICATED);
		assertThat(internal(HttpMethod.GET, "/internal/pipeline-runs/" + UUID.randomUUID(), null, session))
			.hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(runCount()).isEqualTo(before);
	}

	@Test
	void theTokenMeansNothingOnApiRoutes() {
		int before = runCount();

		assertThat(mvc.post()
			.uri("/api/admin/pipeline-runs")
			.header(HttpHeaders.AUTHORIZATION, bearer(TOKEN))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"from\":\"2026-10-01\",\"to\":\"2026-10-01\"}")).hasStatus(HttpStatus.UNAUTHORIZED)
			.bodyJson()
			.isStrictlyEqualTo(NOT_AUTHENTICATED);
		assertThat(mvc.get().uri("/api/posts").header(HttpHeaders.AUTHORIZATION, bearer(TOKEN)))
			.hasStatus(HttpStatus.UNAUTHORIZED)
			.bodyJson()
			.isStrictlyEqualTo(NOT_AUTHENTICATED);
		assertThat(runCount()).isEqualTo(before);
	}

	@ParameterizedTest
	@CsvSource({
			// 01:30 in Warsaw the night after the autumn change: 25 October has 25 hours, its end is CET.
			"2026-10-26T00:30:00Z, 2026-10-23T22:00:00Z, 2026-10-25T22:59:59Z",
			// 02:30 in Warsaw the night after the spring change: 29 March has 23 hours, its end is CEST.
			"2026-03-30T00:30:00Z, 2026-03-27T23:00:00Z, 2026-03-29T21:59:59Z",
			// Still 14 July in UTC, already 15 July in Warsaw: the Warsaw calendar decides.
			"2026-07-14T22:30:00Z, 2026-07-12T22:00:00Z, 2026-07-14T21:59:59Z" })
	void theDailyRunCoversTheDayBeforeYesterdayAndYesterdayInWarsaw(Instant now, Instant from, Instant to)
			throws Exception {
		CLOCK.set(now);

		MvcTestResult started = internal(HttpMethod.POST, DAILY, bearer(TOKEN), null);

		assertThat(started).hasStatus(HttpStatus.ACCEPTED);
		String id = JsonPath.read(started.getResponse().getContentAsString(), "$[0].id");
		assertThat(started).bodyJson().isStrictlyEqualTo("[{\"id\":\"%s\",\"kind\":\"range\"}]".formatted(id));
		PipelineRun run = runs.findById(UUID.fromString(id)).orElseThrow();
		assertThat(run.getKind()).isEqualTo(RunKind.RANGE);
		assertThat(run.getRangeFrom()).isEqualTo(from);
		assertThat(run.getRangeTo()).isEqualTo(to);
		assertThat(run.getSources()).containsExactly(Source.WYKOP);
		assertThat(run.getRequestedBy()).isEqualTo(SystemAccount.SCHEDULER.id());

		awaitIdle();
		// The summarize step closes just after the run's status is written.
		awaitStepsClosed(UUID.fromString(id));
		MvcTestResult status = internal(HttpMethod.GET, "/internal/pipeline-runs/" + id, bearer(TOKEN), null);
		assertThat(status).hasStatusOk();
		Map<String, Object> body = JsonPath.read(status.getResponse().getContentAsString(), "$");
		PipelineRun finished = runs.findById(UUID.fromString(id)).orElseThrow();
		assertThat(body)
			.containsOnlyKeys("id", "kind", "status", "startedAt", "finishedAt", "lastActivityAt", "interruptionCause",
					"step", "assessTotal", "fetched", "newPosts", "assessedOk", "assessedFailed", "processingErrors",
					"dead", "skipped")
			.containsEntry("id", id)
			.containsEntry("kind", "range")
			// An empty day: the source gave no post, so the fetch step fails the run (the cron exits 1).
			.containsEntry("status", "failed")
			.containsEntry("interruptionCause", null)
			.containsEntry("step", null)
			// No assess step.
			.containsEntry("assessTotal", null)
			.containsEntry("fetched", finished.getFetched())
			.containsEntry("newPosts", finished.getNewPosts())
			.containsEntry("assessedOk", finished.getAssessedOk())
			.containsEntry("assessedFailed", finished.getAssessedFailed())
			.containsEntry("processingErrors", finished.getProcessingErrors())
			.containsEntry("dead", finished.getDead())
			.containsEntry("skipped", finished.getSkipped());
		assertThat(Instant.parse((String) body.get("startedAt"))).isEqualTo(finished.getStartedAt());
		assertThat(Instant.parse((String) body.get("finishedAt"))).isEqualTo(finished.getFinishedAt());
		assertThat(Instant.parse((String) body.get("lastActivityAt"))).isEqualTo(finished.getLastActivityAt());
	}

	@Test
	void whileARunAssessesItsStateNamesTheStepAndItsTotalAndAnInterruptionItsCause() throws Exception {
		UUID run = jdbc.queryForObject("INSERT INTO pipeline_runs (kind, status, requested_by, started_at,"
				+ " last_activity_at, assessed_ok, assessed_failed) VALUES ('retry', 'running', ?,"
				+ " '2026-10-26T00:30:05Z', '2026-10-26T00:35:00Z', 3, 1) RETURNING id", UUID.class,
				SystemAccount.SCHEDULER.id());
		try {
			jdbc.update("INSERT INTO pipeline_run_steps (run_id, step, status, started_at, finished_at, items_out)"
					+ " VALUES (?, 'select', 'succeeded', '2026-10-26T00:30:06Z', '2026-10-26T00:30:07Z', 10)", run);
			jdbc.update("INSERT INTO pipeline_run_steps (run_id, step, started_at, items_in)"
					+ " VALUES (?, 'assess', '2026-10-26T00:30:08Z', 10)", run);

			Map<String, Object> during = state(run);

			assertThat(during).containsEntry("status", "running")
				.containsEntry("step", "assess")
				.containsEntry("assessTotal", 10)
				.containsEntry("assessedOk", 3)
				.containsEntry("assessedFailed", 1)
				.containsEntry("interruptionCause", null)
				.containsEntry("finishedAt", null);
			assertThat(Instant.parse((String) during.get("startedAt"))).isEqualTo("2026-10-26T00:30:05Z");
			assertThat(Instant.parse((String) during.get("lastActivityAt"))).isEqualTo("2026-10-26T00:35:00Z");

			// A new deployment's recovery interrupts it and closes its step.
			jdbc.update("UPDATE pipeline_runs SET status = 'interrupted', finished_at = now(), interruption_cause ="
					+ " 'deploy' WHERE id = ?", run);
			jdbc.update("UPDATE pipeline_run_steps SET status = 'interrupted', finished_at = now() WHERE run_id = ?"
					+ " AND status = 'running'", run);

			assertThat(state(run)).containsEntry("status", "interrupted")
				.containsEntry("interruptionCause", "deploy")
				.containsEntry("step", null)
				.containsEntry("assessTotal", 10);
		}
		finally {
			jdbc.update("UPDATE pipeline_runs SET status = 'failed', finished_at = now() WHERE id = ?"
					+ " AND status = 'running'", run);
		}
	}

	@Test
	void aSecondStartWhileTheDailyRunIsActiveIsBusyAndCreatesNothing() {
		wykopGate = new CountDownLatch(1);
		assertThat(internal(HttpMethod.POST, DAILY, bearer(TOKEN), null)).hasStatus(HttpStatus.ACCEPTED);
		int before = runCount();

		assertThat(internal(HttpMethod.POST, DAILY, bearer(TOKEN), null)).hasStatus(HttpStatus.CONFLICT)
			.bodyJson()
			.isStrictlyEqualTo("{\"detail\":\"pipeline_busy\"}");
		assertThat(runCount()).isEqualTo(before);
	}

	@Test
	void theStatusOfAnUnknownRunIsNotFound() {
		assertThat(internal(HttpMethod.GET, "/internal/pipeline-runs/" + UUID.randomUUID(), bearer(TOKEN), null))
			.hasStatus(HttpStatus.NOT_FOUND)
			.bodyJson()
			.isStrictlyEqualTo("{\"detail\":\"not_found\"}");
	}

	/**
	 * A call to an internal route, which must never touch a session: no {@code Set-Cookie} in the
	 * answer and no new {@code spring_session} row, whatever the outcome.
	 */
	private MvcTestResult internal(HttpMethod method, String uri, @Nullable String authorization,
			@Nullable Cookie cookie) {
		long sessionsBefore = sessionRows();
		MockMvcRequestBuilder request = mvc.method(method).uri(uri);
		if (authorization != null) {
			request.header(HttpHeaders.AUTHORIZATION, authorization);
		}
		if (cookie != null) {
			request.cookie(cookie);
		}
		MvcTestResult result = request.exchange();
		assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).as(method + " " + uri).isEmpty();
		assertThat(sessionRows()).as(method + " " + uri).isEqualTo(sessionsBefore);
		return result;
	}

	/** The cron's view of {@code runId}. */
	private Map<String, Object> state(UUID runId) throws Exception {
		MvcTestResult status = internal(HttpMethod.GET, "/internal/pipeline-runs/" + runId, bearer(TOKEN), null);
		assertThat(status).hasStatusOk();
		return JsonPath.read(status.getResponse().getContentAsString(), "$");
	}

	/** Waits until none of the run's steps is running. */
	private void awaitStepsClosed(UUID runId) {
		long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
		while (jdbc.queryForObject("SELECT count(*) FROM pipeline_run_steps WHERE run_id = ? AND status = 'running'",
				Integer.class, runId) > 0) {
			assertThat(System.nanoTime()).as("steps still running").isLessThan(deadline);
			sleep(50);
		}
	}

	private static String bearer(String token) {
		return "Bearer " + token;
	}

	private int runCount() {
		return jdbc.queryForObject("SELECT count(*) FROM pipeline_runs", Integer.class);
	}

	private int activeRuns() {
		return jdbc.queryForObject("SELECT count(*) FROM pipeline_runs WHERE status IN ('queued', 'running')",
				Integer.class);
	}

	private long sessionRows() {
		return jdbc.queryForObject("SELECT count(*) FROM spring_session", Long.class)
				+ jdbc.queryForObject("SELECT count(*) FROM spring_session_attributes", Long.class);
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

	/** Wykop on the local server, in place of the keys-based bean, and the test's clock. */
	@TestConfiguration(proxyBeanMethods = false)
	static class TestBeans {

		@Bean
		WykopRepository wykopRepository() {
			return new WykopRepository(new WykopApiClient(WYKOP.baseUrl(), "k", "s", Duration.ofSeconds(60)),
					duration -> {
					}, Duration.ZERO);
		}

		@Bean
		@Primary
		Clock testClock() {
			return CLOCK;
		}

	}

	/** A clock a test sets; UTC like the application's. */
	static final class MutableClock extends Clock {

		private volatile Instant now;

		MutableClock(Instant now) {
			this.now = now;
		}

		void set(Instant now) {
			this.now = now;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return Clock.fixed(now, zone);
		}

		@Override
		public Instant instant() {
			return now;
		}

	}

}
