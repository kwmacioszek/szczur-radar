package pl.szczurradar.api.pipeline.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import pl.szczurradar.api.TestcontainersConfiguration;
import pl.szczurradar.api.alerts.AlertEvent;
import pl.szczurradar.api.alerts.OperatorAlertQueue;
import pl.szczurradar.api.cli.MigrateCommand;
import pl.szczurradar.api.config.MutableClock;
import pl.szczurradar.api.config.SessionConfig;
import pl.szczurradar.api.contract.ContractFiles;
import pl.szczurradar.api.internal.PipelineTriggerToken;
import pl.szczurradar.api.llm.LlmClient;
import pl.szczurradar.api.pipeline.run.PipelineRun;
import pl.szczurradar.api.pipeline.run.PipelineRunRepository;
import pl.szczurradar.api.pipeline.run.RunKind;
import pl.szczurradar.api.posts.AssessmentQuality;
import pl.szczurradar.api.posts.AssessmentRepository;
import pl.szczurradar.api.sources.LocalApiServer.Reply;
import pl.szczurradar.api.sources.Source;
import pl.szczurradar.api.sources.wykop.LocalWykopServer;
import pl.szczurradar.api.sources.wykop.WykopApiClient;
import pl.szczurradar.api.sources.wykop.WykopRepository;
import pl.szczurradar.api.user.Role;
import pl.szczurradar.api.user.SystemAccount;
import pl.szczurradar.api.user.User;
import pl.szczurradar.api.user.UserRepository;
import pl.szczurradar.api.web.MappedRoutes;
import pl.szczurradar.probe.InternalProbeController;

/**
 * The daily cron's routes ({@code /internal/pipeline-runs}) against the real queue: the trigger token
 * and only the token (no session accepted, none created, no cookie set; every mapped {@code /internal/**}
 * route is called, computed from the mappings, no list to keep), the token meaning nothing on
 * {@code /api/**}, the daily range on a fixed clock, a run's state with its step and progress, the
 * admin's refusals, and the night's summary: its text, the {@code NIGHT_SUMMARY} raised for the range run
 * (the queue a mock), {@code ASSESSMENT_QUALITY} after it only when a source crossed a threshold of either
 * quality measure — once, with every crossing of both (S-36) —, each measure's failed read costing only its
 * own lines, 400 for a bad body and 404 for an unknown run (what the text is made of is
 * {@code pipeline/NightSummaryIT}). Runs execute in the
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
@Import({ TestcontainersConfiguration.class, InternalPipelineRunIT.TestBeans.class, InternalProbeController.class })
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class InternalPipelineRunIT {

	/** A test-only token, as long as {@code openssl rand -hex 32} makes one. */
	static final String TOKEN = "9f2c4e6a8b0d1f3e5a7c9e1b3d5f7a9c0e2b4d6f8a1c3e5b7d9f0a2c4e6b8d0f";

	private static final String DAILY = "/internal/pipeline-runs/daily";

	private static final String SUMMARY = "/internal/pipeline-runs/nightly-summary";

	/** The bodies the cron reads, pinned here and used by the cron's test to build its stub API. */
	private static final String FIXTURES = "../tools/pipeline-trigger/fixtures/api/";

	private static final String NOT_AUTHENTICATED = "{\"detail\":\"not_authenticated\"}";

	private static final String PASSWORD = "correct horse battery";

	private static final String POLISH = "Autor uważa, że kurs wzrośnie";

	private static final String ENGLISH = "The author thinks that the price will rise";

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

	/** The server's queue, a mock: the night's summary is raised to it. */
	@MockitoBean
	OperatorAlertQueue alerts;

	/** The real quality window; a test makes it fail (reset after each test). */
	@MockitoSpyBean
	AssessmentQuality quality;

	/** The real assessments; a test makes the theses' query fail (reset after each test). */
	@MockitoSpyBean
	AssessmentRepository assessments;

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	RequestMappingHandlerMapping handlerMapping;

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

		assertEveryInternalRouteIs401(authorization, null);
		assertThat(runCount()).isEqualTo(before);
		verify(alerts, never()).raise(any(), any(), any());
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

		assertEveryInternalRouteIs401(null, session);
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
		fixture(started, "daily-started.json");
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
		fixture(status, "run-state-failed.json");
		Map<String, Object> body = JsonPath.read(status.getResponse().getContentAsString(), "$");
		PipelineRun finished = runs.findById(UUID.fromString(id)).orElseThrow();
		assertThat(body)
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

			Map<String, Object> during = state(run, "run-state-running.json");

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

			assertThat(state(run, "run-state-interrupted.json")).containsEntry("status", "interrupted")
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
	void aQueuedRunHasNoStartNoStepAndNoTotal() throws Exception {
		UUID run = jdbc.queryForObject("INSERT INTO pipeline_runs (kind, status, requested_by) VALUES ('retry',"
				+ " 'queued', ?) RETURNING id", UUID.class, SystemAccount.SCHEDULER.id());
		try {
			assertThat(state(run, "run-state-queued.json")).containsEntry("status", "queued")
				.containsEntry("startedAt", null)
				.containsEntry("lastActivityAt", null)
				.containsEntry("step", null)
				.containsEntry("assessTotal", null);
		}
		finally {
			jdbc.update("UPDATE pipeline_runs SET status = 'failed', finished_at = now() WHERE id = ?"
					+ " AND status = 'queued'", run);
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
	 * The night's text with both measures' lines (S-35, S-36): an admin's run on the 22nd, outside the night, left
	 * two good attempts of a Wykop post with 600 theses, 3 of them English — below every minimum or threshold, so
	 * no alarm. The answer is the cron's file, from which its test builds the stub's summary.
	 */
	@Test
	void theNightlySummaryIsReturnedAndRaisedForTheRangeRun() throws Exception {
		UUID retry = jdbc.queryForObject("INSERT INTO pipeline_runs (kind, status, requested_by, created_at, started_at,"
				+ " finished_at, dead) VALUES ('retry', 'partial', ?, '2026-10-26T00:30:00Z', '2026-10-26T00:30:01Z',"
				+ " '2026-10-26T00:35:00Z', 1) RETURNING id", UUID.class, SystemAccount.SCHEDULER.id());
		UUID range = jdbc.queryForObject("INSERT INTO pipeline_runs (kind, status, requested_by, created_at, started_at,"
				+ " finished_at, range_from, range_to, sources, skipped) VALUES ('range', 'succeeded', ?,"
				+ " '2026-10-26T00:30:00.5Z', '2026-10-26T00:35:01Z', '2026-10-26T00:50:00Z', '2026-10-23T22:00:00Z',"
				+ " '2026-10-25T22:59:59Z', ARRAY['wykop'], 2) RETURNING id", UUID.class, SystemAccount.SCHEDULER.id());
		jdbc.update("INSERT INTO pipeline_run_sources (run_id, source, status, fetched, skipped)"
				+ " VALUES (?, 'wykop', 'succeeded', 7, 2)", range);
		UUID admin = jdbc.queryForObject("INSERT INTO users (email, role) VALUES (?, 'admin') RETURNING id", UUID.class,
				"internal-summary-" + UUID.randomUUID() + "@example.com");
		UUID adminRun = jdbc.queryForObject("INSERT INTO pipeline_runs (kind, status, requested_by, created_at,"
				+ " started_at, finished_at) VALUES ('retry', 'succeeded', ?, '2026-10-22T10:00:00Z',"
				+ " '2026-10-22T10:00:01Z', '2026-10-22T10:05:00Z') RETURNING id", UUID.class, admin);
		UUID post = wykopPost(adminRun);
		try {
			theses(post, adminRun, POLISH, "2026-10-22T10:01:00Z", 597);
			theses(post, adminRun, ENGLISH, "2026-10-22T10:02:00Z", 3);
			CLOCK.set(Instant.parse("2026-10-26T00:55:00Z"));

			MvcTestResult result = summary(bearer(TOKEN), "{\"runs\":[\"" + retry + "\",\"" + range + "\"]}");

			assertThat(result).hasStatusOk();
			String text = JsonPath.read(result.getResponse().getContentAsString(), "$.text");
			fixture(result, "nightly-summary.json");
			assertThat(text).isEqualTo("""
					Night 2026-10-26 (range 2026-10-24…2026-10-25): retry partial, range succeeded; errors 0, skipped 2
					wykop: fetched 7, skipped 2, new 0, leftovers 0; assessed 0, failed 0, dead 0; tokens in 0 (cached 0), out 0
					wykop quality 7d (2026-10-20…2026-10-26): 2 attempts, fewer than 100, threshold not checked
					wykop language 7d (2026-10-20…2026-10-26): other language 0.5% of 600 theses; threshold 2%
					Dead in admin runs since the previous night: none
					/admin""");
			verify(alerts).raise(AlertEvent.NIGHT_SUMMARY, range, text);
			// No source crossed a threshold of either measure: no alarm.
			verify(alerts, never()).raise(eq(AlertEvent.ASSESSMENT_QUALITY), any(), any());
		}
		finally {
			delete(new SeededNight(adminRun, post));
		}
	}

	/**
	 * The quality window cannot be read (S-35 review F1): the summary still goes out, with one line
	 * {@code unavailable} in place of the quality lines, and no {@code ASSESSMENT_QUALITY} — the night's
	 * summary is the operator's every-night signal (S-34) and does not depend on the quality query.
	 */
	@Test
	void aQualityWindowThatCannotBeReadLeavesTheSummaryWithoutItsQualityLines() throws Exception {
		UUID range = jdbc.queryForObject("INSERT INTO pipeline_runs (kind, status, requested_by, created_at, started_at,"
				+ " finished_at, range_from, range_to, sources) VALUES ('range', 'succeeded', ?,"
				+ " '2026-10-26T00:30:00.5Z', '2026-10-26T00:35:01Z', '2026-10-26T00:50:00Z', '2026-10-23T22:00:00Z',"
				+ " '2026-10-25T22:59:59Z', ARRAY['wykop']) RETURNING id", UUID.class, SystemAccount.SCHEDULER.id());
		jdbc.update("INSERT INTO pipeline_run_sources (run_id, source, status, fetched, skipped)"
				+ " VALUES (?, 'wykop', 'succeeded', 7, 0)", range);
		CLOCK.set(Instant.parse("2026-10-26T00:55:00Z"));
		doThrow(new QueryTimeoutException("test: the quality query timed out")).when(quality).window(any(), any());

		MvcTestResult result = summary(bearer(TOKEN), "{\"runs\":[\"" + range + "\"]}");

		assertThat(result).hasStatusOk();
		String text = JsonPath.read(result.getResponse().getContentAsString(), "$.text");
		assertThat(text).isEqualTo("""
				Night 2026-10-26 (range 2026-10-24…2026-10-25): range succeeded; errors 0, skipped 0
				wykop: fetched 7, skipped 0, new 0, leftovers 0; assessed 0, failed 0, dead 0; tokens in 0 (cached 0), out 0
				quality 7d (2026-10-20…2026-10-26): unavailable (QueryTimeoutException)
				Dead in admin runs since the previous night: none
				/admin""");
		verify(alerts).raise(AlertEvent.NIGHT_SUMMARY, range, text);
		verify(alerts, never()).raise(eq(AlertEvent.ASSESSMENT_QUALITY), any(), any());
	}

	/**
	 * 02:55 in Warsaw on 15 September 2026: Wykop's 100 good attempts on the 10th and an admin's 10 failed
	 * ones at 14:00 on the 14th, after the previous night's summary — above its threshold only tonight, so
	 * the summary's quality line says so and one {@code ASSESSMENT_QUALITY} follows {@code NIGHT_SUMMARY}
	 * for the same run; a repeated request within the queue's window is the queue's to drop.
	 */
	@Test
	void aSourceCrossingItsQualityThresholdRaisesOneAlarmAfterTheSummary() throws Exception {
		SeededNight night = seededNight();
		try {
			attempts(night.post(), night.range(), null, "2026-09-10T10:00:00Z", 100);
			attempts(night.post(), night.range(), "fabricated_evidence", "2026-09-14T12:00:00Z", 10);

			MvcTestResult result = summary(bearer(TOKEN), "{\"runs\":[\"" + night.range() + "\"]}");

			assertThat(result).hasStatusOk();
			String text = JsonPath.read(result.getResponse().getContentAsString(), "$.text");
			assertThat(result).bodyJson().isStrictlyEqualTo("{\"text\":" + jsonString(text) + "}");
			assertThat(text).contains("\nwykop quality 7d (2026-09-09…2026-09-15): failed 9.1% of 110 attempts"
					+ " (fabricated_evidence 9.1%); threshold 5%; ABOVE THRESHOLD\n");
			InOrder order = inOrder(alerts);
			order.verify(alerts).raise(AlertEvent.NIGHT_SUMMARY, night.range(), text);
			order.verify(alerts).raise(AlertEvent.ASSESSMENT_QUALITY, night.range(), "wykop: failed 9.1% of 110 attempts"
					+ " in 7 days (2026-09-09…2026-09-15), above its threshold 5% (the day before: 0.0% of 100 attempts)"
					+ " (fabricated_evidence 9.1%); /admin");
			verify(alerts, times(1)).raise(eq(AlertEvent.ASSESSMENT_QUALITY), any(), any());
		}
		finally {
			delete(night);
		}
	}

	/**
	 * The same night with theses only (S-36): Wykop's 500 Polish theses on the 10th and an admin's attempt with
	 * 20 English ones at 14:00 on the 14th — above its threshold of 2% only tonight, so the language line says so
	 * and one {@code ASSESSMENT_QUALITY} with the language part alone follows the summary.
	 */
	@Test
	void aSourceCrossingItsLanguageThresholdAloneRaisesOneAlarm() throws Exception {
		SeededNight night = seededNight();
		try {
			theses(night.post(), night.range(), POLISH, "2026-09-10T10:00:00Z", 500);
			theses(night.post(), night.range(), ENGLISH, "2026-09-14T12:00:00Z", 20);

			MvcTestResult result = summary(bearer(TOKEN), "{\"runs\":[\"" + night.range() + "\"]}");

			assertThat(result).hasStatusOk();
			String text = JsonPath.read(result.getResponse().getContentAsString(), "$.text");
			assertThat(text).contains("\nwykop quality 7d (2026-09-09…2026-09-15): 2 attempts, fewer than 100,"
					+ " threshold not checked\nwykop language 7d (2026-09-09…2026-09-15): other language 3.8% of 520"
					+ " theses; threshold 2%; ABOVE THRESHOLD\nDead in admin runs");
			InOrder order = inOrder(alerts);
			order.verify(alerts).raise(AlertEvent.NIGHT_SUMMARY, night.range(), text);
			order.verify(alerts).raise(AlertEvent.ASSESSMENT_QUALITY, night.range(), "wykop: other language 3.8% of 520"
					+ " theses in 7 days (2026-09-09…2026-09-15), above its threshold 2% (the day before: 0.0% of 500"
					+ " theses); /admin");
			verify(alerts, times(1)).raise(eq(AlertEvent.ASSESSMENT_QUALITY), any(), any());
		}
		finally {
			delete(night);
		}
	}

	/**
	 * Both measures crossing the same night: one {@code ASSESSMENT_QUALITY} — a second with the same event and
	 * run would be the queue's repeat, dropped — the failed attempts' part first, then the language's.
	 */
	@Test
	void bothMeasuresCrossingRaiseOneAlarmWithBoth() throws Exception {
		SeededNight night = seededNight();
		try {
			attempts(night.post(), night.range(), null, "2026-09-10T10:00:00Z", 100);
			attempts(night.post(), night.range(), "fabricated_evidence", "2026-09-14T12:00:00Z", 10);
			theses(night.post(), night.range(), POLISH, "2026-09-10T10:00:00Z", 500);
			theses(night.post(), night.range(), ENGLISH, "2026-09-14T12:00:00Z", 20);

			MvcTestResult result = summary(bearer(TOKEN), "{\"runs\":[\"" + night.range() + "\"]}");

			assertThat(result).hasStatusOk();
			String text = JsonPath.read(result.getResponse().getContentAsString(), "$.text");
			InOrder order = inOrder(alerts);
			order.verify(alerts).raise(AlertEvent.NIGHT_SUMMARY, night.range(), text);
			// The attempts with theses are good attempts of the quality window too: 112 attempts, 101 the night before.
			order.verify(alerts).raise(AlertEvent.ASSESSMENT_QUALITY, night.range(), "wykop: failed 8.9% of 112 attempts"
					+ " in 7 days (2026-09-09…2026-09-15), above its threshold 5% (the day before: 0.0% of 101 attempts)"
					+ " (fabricated_evidence 8.9%); wykop: other language 3.8% of 520 theses in 7 days"
					+ " (2026-09-09…2026-09-15), above its threshold 2% (the day before: 0.0% of 500 theses); /admin");
			verify(alerts, times(1)).raise(eq(AlertEvent.ASSESSMENT_QUALITY), any(), any());
		}
		finally {
			delete(night);
		}
	}

	/**
	 * The theses cannot be read (S-36): the summary keeps its quality lines, one line {@code language 7d …
	 * unavailable} stands for the language lines, and the failed attempts' crossing still raises its alarm —
	 * the language window's own transaction leaves the quality window's alone. The query fails inside Postgres,
	 * which aborts its whole transaction, so the night's snapshot read after it proves it runs in a transaction
	 * of its own.
	 */
	@Test
	void thesesThatCannotBeReadCostOnlyTheLanguageLines() throws Exception {
		SeededNight night = seededNight();
		try {
			attempts(night.post(), night.range(), null, "2026-09-10T10:00:00Z", 100);
			attempts(night.post(), night.range(), "fabricated_evidence", "2026-09-14T12:00:00Z", 10);
			doAnswer(invocation -> {
				assertThatThrownBy(() -> jdbc.execute("SELECT 1 / 0")).isInstanceOf(DataAccessException.class);
				assertThatThrownBy(() -> jdbc.queryForObject("SELECT 1", Integer.class))
					.hasMessageContaining("current transaction is aborted");
				throw new QueryTimeoutException("test: the theses' query timed out");
			}).when(assessments).findModelStatements(any(), any());

			MvcTestResult result = summary(bearer(TOKEN), "{\"runs\":[\"" + night.range() + "\"]}");

			assertThat(result).hasStatusOk();
			String text = JsonPath.read(result.getResponse().getContentAsString(), "$.text");
			assertThat(text).contains("\nwykop quality 7d (2026-09-09…2026-09-15): failed 9.1% of 110 attempts"
					+ " (fabricated_evidence 9.1%); threshold 5%; ABOVE THRESHOLD\nlanguage 7d (2026-09-09…2026-09-15):"
					+ " unavailable (QueryTimeoutException)\nDead in admin runs");
			InOrder order = inOrder(alerts);
			order.verify(alerts).raise(AlertEvent.NIGHT_SUMMARY, night.range(), text);
			order.verify(alerts).raise(AlertEvent.ASSESSMENT_QUALITY, night.range(), "wykop: failed 9.1% of 110 attempts"
					+ " in 7 days (2026-09-09…2026-09-15), above its threshold 5% (the day before: 0.0% of 100 attempts)"
					+ " (fabricated_evidence 9.1%); /admin");
			verify(alerts, times(1)).raise(eq(AlertEvent.ASSESSMENT_QUALITY), any(), any());
		}
		finally {
			delete(night);
		}
	}

	/** The cron's range run of 15 September 2026 and a Wykop post for its attempts; the clock at 02:55 in Warsaw. */
	private SeededNight seededNight() {
		UUID range = jdbc.queryForObject("INSERT INTO pipeline_runs (kind, status, requested_by, created_at, started_at,"
				+ " finished_at, range_from, range_to, sources) VALUES ('range', 'succeeded', ?,"
				+ " '2026-09-15T00:30:00Z', '2026-09-15T00:30:01Z', '2026-09-15T00:50:00Z', '2026-09-12T22:00:00Z',"
				+ " '2026-09-14T21:59:59Z', ARRAY['wykop']) RETURNING id", UUID.class, SystemAccount.SCHEDULER.id());
		UUID post = wykopPost(range);
		CLOCK.set(Instant.parse("2026-09-15T00:55:00Z"));
		return new SeededNight(range, post);
	}

	/** An assessed Wykop post first stored by {@code firstRun}, with its raw payload. */
	private UUID wykopPost(UUID firstRun) {
		return jdbc.queryForObject("WITH raw AS (INSERT INTO raw_payloads (source, content_encoding,"
				+ " media_type, payload, sha256) VALUES ('wykop', 'gzip', 'application/json', '\\x00', ?) RETURNING id)"
				+ " INSERT INTO posts (source, item_type, source_item_id, channel, language, published_at, raw_payload_id,"
				+ " content_hash, stage, assessment_attempts, first_run_id) SELECT 'wykop', 'post', ?, 'c', 'pl', now(),"
				+ " raw.id, ?, 'assessed', 1, ? FROM raw RETURNING id", UUID.class, "0".repeat(64),
				UUID.randomUUID().toString(), "0".repeat(64), firstRun);
	}

	/**
	 * Every row {@link #seededNight} (or a test's run and post) and its test made, so no later test of the class
	 * sees its night or its attempts.
	 */
	private void delete(SeededNight night) {
		UUID raw = jdbc.queryForObject("SELECT raw_payload_id FROM posts WHERE id = ?", UUID.class, night.post());
		jdbc.update("DELETE FROM assessments WHERE post_id = ?", night.post());
		jdbc.update("DELETE FROM posts WHERE id = ?", night.post());
		jdbc.update("DELETE FROM raw_payloads WHERE id = ?", raw);
		jdbc.update("DELETE FROM pipeline_runs WHERE id = ?", night.range());
	}

	private record SeededNight(UUID range, UUID post) {
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "{}", "{\"runs\":null}", "{\"runs\":[]}", "{\"runs\":[null]}", "{\"runs\":[\"x\"]}",
			"{\"runs\":\"" + "00000000-0000-4000-8000-000000000001" + "\"}",
			"{\"runs\":[\"00000000-0000-4000-8000-000000000001\"],\"other\":1}" })
	void aNightlySummaryRequestWithoutOneToTenRunIdsIsInvalid(String body) {
		assertThat(summary(bearer(TOKEN), body)).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.isStrictlyEqualTo("{\"detail\":\"invalid_request\"}");
		verify(alerts, never()).raise(any(), any(), any());
	}

	@Test
	void aNightlySummaryOfElevenRunsIsInvalidAndOfAnUnknownRunNotFound() {
		String eleven = Stream.generate(UUID::randomUUID)
			.limit(11)
			.map(id -> "\"" + id + "\"")
			.collect(Collectors.joining(","));

		assertThat(summary(bearer(TOKEN), "{\"runs\":[" + eleven + "]}")).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.isStrictlyEqualTo("{\"detail\":\"invalid_request\"}");
		assertThat(summary(bearer(TOKEN), "{\"runs\":[\"" + UUID.randomUUID() + "\"]}")).hasStatus(HttpStatus.NOT_FOUND)
			.bodyJson()
			.isStrictlyEqualTo("{\"detail\":\"not_found\"}");
		verify(alerts, never()).raise(any(), any(), any());
	}

	/**
	 * Calls every mapped {@code /internal/**} route without a body (401 comes before binding) and collects
	 * the ones that do not answer 401 {@code not_authenticated}; the anchors prove the walk saw the probe
	 * and a real route.
	 */
	private void assertEveryInternalRouteIs401(@Nullable String authorization, @Nullable Cookie cookie) {
		List<MappedRoutes.Route> routes = MappedRoutes.routesUnder(handlerMapping, MappedRoutes.INTERNAL_PATHS);
		assertThat(routes.stream().map(MappedRoutes.Route::key)).contains("GET /internal/probe",
				"POST /internal/pipeline-runs/daily");
		List<String> failures = new ArrayList<>();
		for (MappedRoutes.Route route : routes) {
			MvcTestResult result = internal(HttpMethod.valueOf(route.method()), route.concretePath(), authorization,
					cookie);
			int status = result.getResponse().getStatus();
			String body = bodyOf(result);
			if (result.getUnresolvedException() != null) {
				failures.add(route.key() + " -> " + result.getUnresolvedException());
			}
			else if (status != HttpStatus.UNAUTHORIZED.value() || !NOT_AUTHENTICATED.equals(body)) {
				failures.add(route.key() + " -> " + status + " " + body);
			}
		}
		assertThat(failures).as("internal routes that did not answer 401 not_authenticated").isEmpty();
	}

	private static String bodyOf(MvcTestResult result) {
		try {
			return result.getResponse().getContentAsString();
		}
		catch (java.io.UnsupportedEncodingException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/** {@code POST /internal/pipeline-runs/nightly-summary} with {@code body}, never touching a session. */
	private MvcTestResult summary(@Nullable String authorization, String body) {
		long sessionsBefore = sessionRows();
		MockMvcRequestBuilder request = mvc.post().uri(SUMMARY).contentType(MediaType.APPLICATION_JSON).content(body);
		if (authorization != null) {
			request.header(HttpHeaders.AUTHORIZATION, authorization);
		}
		MvcTestResult result = request.exchange();
		assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).as("POST " + SUMMARY).isEmpty();
		assertThat(sessionRows()).as("POST " + SUMMARY).isEqualTo(sessionsBefore);
		return result;
	}

	/**
	 * {@code count} model attempts of {@code post} in {@code run} at {@code assessedAt}: failed with
	 * {@code failureClass}, or {@code ok} when it is {@code null}.
	 */
	private void attempts(UUID post, UUID run, @Nullable String failureClass, String assessedAt, int count) {
		boolean ok = failureClass == null;
		jdbc.update("INSERT INTO assessments (post_id, run_id, status, failure_class, input, input_hash, output,"
				+ " violations, result_source, flex_fallback, contract_version, assessed_at, duration_ms, attempts)"
				+ " SELECT CAST(? AS UUID), CAST(? AS UUID), CAST(? AS TEXT), CAST(? AS TEXT), '{}', ?,"
				+ " CAST(? AS JSONB), '[]', 'model', false, 'V2', CAST(? AS TIMESTAMPTZ), 0, 1"
				+ " FROM generate_series(1, ?)", post, run, ok ? "ok" : "failed", failureClass, "0".repeat(64),
				ok ? "{}" : null, assessedAt, count);
	}

	/**
	 * One {@code ok} model attempt of {@code post} in {@code run} at {@code assessedAt} with {@code count} theses,
	 * each {@code statement}.
	 */
	private void theses(UUID post, UUID run, String statement, String assessedAt, int count) {
		jdbc.update("INSERT INTO assessments (post_id, run_id, status, input, input_hash, output, violations,"
				+ " result_source, flex_fallback, contract_version, assessed_at, duration_ms, attempts)"
				+ " VALUES (?, ?, 'ok', '{}', ?, (SELECT jsonb_build_object('claims',"
				+ " jsonb_agg(jsonb_build_object('id', 'c' || n, 'statement', CAST(? AS TEXT))))"
				+ " FROM generate_series(1, ?) n), '[]', 'model', false, 'V2', CAST(? AS TIMESTAMPTZ), 0, 1)", post, run,
				"0".repeat(64), statement, count, assessedAt);
	}

	/** {@code text} as a JSON string literal (quotes and line breaks escaped). */
	private static String jsonString(String text) {
		return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
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

	/** The body of {@code result} against the cron's file {@code fileName}. */
	private static void fixture(MvcTestResult result, String fileName) throws Exception {
		ContractFiles.assertMatches(result.getResponse().getContentAsString(), Path.of(FIXTURES + fileName));
	}

	/** The cron's view of {@code runId}, which must match the file {@code fileName}. */
	private Map<String, Object> state(UUID runId, String fileName) throws Exception {
		MvcTestResult status = internal(HttpMethod.GET, "/internal/pipeline-runs/" + runId, bearer(TOKEN), null);
		assertThat(status).hasStatusOk();
		fixture(status, fileName);
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

}
