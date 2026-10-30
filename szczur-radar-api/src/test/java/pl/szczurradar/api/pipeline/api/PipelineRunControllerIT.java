package pl.szczurradar.api.pipeline.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import pl.szczurradar.api.TestcontainersConfiguration;
import pl.szczurradar.api.agents.assessor.Prefilter;
import pl.szczurradar.api.agents.assessor.contract.ContractJson;
import pl.szczurradar.api.cli.MigrateCommand;
import pl.szczurradar.api.llm.Channel;
import pl.szczurradar.api.llm.LlmClient;
import pl.szczurradar.api.llm.LlmResponse;
import pl.szczurradar.api.llm.OutputMode;
import pl.szczurradar.api.llm.Provider;
import pl.szczurradar.api.llm.ServiceTier;
import pl.szczurradar.api.pipeline.PipelineRunService;
import pl.szczurradar.api.pipeline.run.PipelineRunRepository;
import pl.szczurradar.api.posts.AssessmentRepository;
import pl.szczurradar.api.posts.PostRepository;
import pl.szczurradar.api.posts.PostSeeds;
import pl.szczurradar.api.posts.PostStage;
import pl.szczurradar.api.raw.RawPayloadStore;
import pl.szczurradar.api.sources.LocalApiServer.Reply;
import pl.szczurradar.api.sources.wykop.LocalWykopServer;
import pl.szczurradar.api.sources.wykop.WykopApiClient;
import pl.szczurradar.api.sources.wykop.WykopRepository;
import pl.szczurradar.api.user.Role;
import pl.szczurradar.api.user.SystemAccount;
import pl.szczurradar.api.user.User;
import pl.szczurradar.api.user.UserRepository;

/**
 * {@code POST} and {@code GET /api/admin/pipeline-runs}, and one run's page, against the real queue: runs
 * execute in the background with Wykop on a local server (which a test can hold, to keep a run active)
 * and a stubbed LLM. Every test waits until no run is active before it ends.
 *
 * <p>A held search waits for the gate without a bound; the clean-up ({@link #awaitIdle}) opens it, after a
 * red test too, and a test that hangs ends at the class's {@link Timeout}, with a dump of every thread in
 * the log ({@code junit-platform.properties}).
 */
@SpringBootTest(properties = MigrateCommand.ENABLE_FLYWAY)
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, PipelineRunControllerIT.LocalWykop.class })
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class PipelineRunControllerIT {

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

	@MockitoBean
	LlmClient llmClient;

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
		admin = users.save(new User("runs-" + UUID.randomUUID() + "@example.com", null, Role.ADMIN)).getId();
		seeds = new PostSeeds(posts, assessments, rawPayloads, runs, users, jdbc, entityManager);
		doAnswer(invocation -> new LlmResponse(ContractJson.MAPPER.writeValueAsString(Prefilter.noContentOutput()),
				Provider.GEMINI, "gemini-3.1-flash-lite", Channel.GEMINI, ServiceTier.DEFAULT, false, null,
				OutputMode.JSON_SCHEMA, 100, 50, null, 1)).when(llmClient).complete(any());
	}

	@AfterEach
	void awaitIdle() {
		wykopGate.countDown();
		long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
		while (activeRuns() > 0) {
			assertThat(System.nanoTime()).as("runs still active").isLessThan(deadline);
			sleep(100);
		}
		// Leftovers of this test would turn the next range request into retry + range.
		jdbc.update("UPDATE posts SET stage = 'dead', assessment_attempts = 3 WHERE stage IN ('stored', 'failed')");
	}

	@Test
	void theSourcesListSaysWhichAreConfigured() {
		assertThat(mvc.get().uri("/api/admin/pipeline-runs/sources").with(asAdmin())).hasStatusOk()
			.bodyJson()
			.isStrictlyEqualTo("[{\"source\":\"wykop\",\"configured\":true}]");
	}

	@Test
	void aRangeIsQueuedAfterARetryRunWhenThereAreLeftovers() {
		UUID leftover = seeds.post(PostStage.FAILED, 1, "2026-10-01T08:00:00Z");

		MvcTestResult result = start("{\"from\":\"2026-10-01\",\"to\":\"2026-10-02\"}");

		assertThat(result).hasStatus(HttpStatus.ACCEPTED);
		assertThat(result).bodyJson().extractingPath("$[*].kind").asArray().containsExactly("retry", "range");
		assertThat(result).bodyJson().extractingPath("$[1].sources").asArray().containsExactly("wykop");
		assertThat(result).bodyJson().extractingPath("$[1].rangeFrom").isEqualTo("2026-09-30T22:00:00Z");
		assertThat(result).bodyJson().extractingPath("$[1].rangeTo").isEqualTo("2026-10-02T21:59:59Z");
		awaitIdle();
		assertThat(jdbc.queryForObject("SELECT stage FROM posts WHERE id = ?", String.class, leftover)).isEqualTo("assessed");
	}

	@Test
	void withoutLeftoversOnlyTheRangeIsQueuedWithTheNamedSources() {
		MvcTestResult result = start("{\"from\":\"2026-10-01\",\"to\":\"2026-10-01\",\"sources\":[\"wykop\"]}");

		assertThat(result).hasStatus(HttpStatus.ACCEPTED);
		assertThat(result).bodyJson().extractingPath("$[*].kind").asArray().containsExactly("range");
		assertThat(result).bodyJson().extractingPath("$[0].status").isEqualTo("queued");
		// An admin's run, which needs nothing yet and assesses nothing yet.
		assertThat(result).bodyJson().extractingPath("$[0].scheduled").isEqualTo(false);
		assertThat(result).bodyJson().extractingPath("$[0].needsAttention").isEqualTo(false);
		assertThat(result).bodyJson().extractingPath("$[0].attentionReasons").asArray().isEmpty();
		assertThat(result).bodyJson().extractingPath("$[0].inFlight").isEqualTo(0);
	}

	@Test
	void aRequestWhileARunIsActiveIsBusy() {
		UUID queued = jdbc.queryForObject("INSERT INTO pipeline_runs (kind, requested_by) VALUES ('retry', ?) RETURNING id",
				UUID.class, admin);
		try {
			assertThat(start("{\"from\":\"2026-10-01\",\"to\":\"2026-10-01\"}")).hasStatus(HttpStatus.CONFLICT)
				.bodyJson()
				.isStrictlyEqualTo("{\"detail\":\"pipeline_busy\"}");
		}
		finally {
			jdbc.update("UPDATE pipeline_runs SET status = 'interrupted' WHERE id = ?", queued);
		}
	}

	@Test
	void twoConcurrentRequestsCreateExactlyOneSetOfRuns() throws Exception {
		wykopGate = new CountDownLatch(1);
		int before = jdbc.queryForObject("SELECT count(*) FROM pipeline_runs", Integer.class);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			CountDownLatch go = new CountDownLatch(1);
			List<Future<Integer>> statuses = new ArrayList<>();
			for (int i = 0; i < 2; i++) {
				statuses.add(pool.submit((Callable<Integer>) () -> {
					go.await();
					return start("{\"from\":\"2026-10-01\",\"to\":\"2026-10-01\"}").getResponse().getStatus();
				}));
			}
			go.countDown();
			List<Integer> codes = new ArrayList<>();
			for (Future<Integer> status : statuses) {
				codes.add(status.get(30, TimeUnit.SECONDS));
			}

			assertThat(codes).containsExactlyInAnyOrder(202, 409);
			assertThat(jdbc.queryForObject("SELECT count(*) FROM pipeline_runs", Integer.class)).isEqualTo(before + 1);
		}
		finally {
			pool.shutdownNow();
		}
	}

	@Test
	void malformedRequestsAreInvalid() {
		String today = LocalDate.now(PipelineRunService.ZONE).toString();
		String tomorrow = LocalDate.now(PipelineRunService.ZONE).plusDays(1).toString();
		String hundredAndOne = IntStream.range(0, 101)
			.mapToObj(i -> "\"" + UUID.randomUUID() + "\"")
			.collect(Collectors.joining(","));
		for (String body : List.of("{}", "{\"from\":\"2026-10-02\",\"to\":\"2026-10-01\"}",
				"{\"from\":\"2026-10-01\",\"to\":\"2026-10-08\"}", "{\"from\":\"" + today + "\",\"to\":\"" + tomorrow + "\"}",
				"{\"from\":\"2026-10-01\"}", "{\"from\":\"2026-10-01\",\"to\":\"2026-10-01\",\"postIds\":[\"" + UUID.randomUUID()
						+ "\"]}",
				"{\"postIds\":[]}", "{\"postIds\":[" + hundredAndOne + "]}",
				"{\"from\":\"2026-10-01\",\"to\":\"2026-10-01\",\"sources\":[\"twitter\"]}",
				"{\"from\":\"2026-10-01\",\"to\":\"2026-10-01\",\"extra\":1}", "{\"from\":\"01.10.2026\",\"to\":\"2026-10-01\"}")) {
			assertThat(start(body)).as(body)
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson()
				.isStrictlyEqualTo("{\"detail\":\"invalid_request\"}");
		}
		// Seven days, ending today, are fine.
		assertThat(start("{\"from\":\"" + LocalDate.now(PipelineRunService.ZONE).minusDays(6) + "\",\"to\":\"" + today
				+ "\"}")).hasStatus(HttpStatus.ACCEPTED);
	}

	@Test
	void aRegisteredSourceWithoutAFetcherIsNotConfigured() {
		int before = jdbc.queryForObject("SELECT count(*) FROM pipeline_runs", Integer.class);

		// Reddit is registered (sources/Source.REDDIT), but has no fetcher before S-27.
		assertThat(start("{\"from\":\"2026-10-01\",\"to\":\"2026-10-01\",\"sources\":[\"reddit\"]}"))
			.hasStatus(HttpStatus.SERVICE_UNAVAILABLE)
			.bodyJson()
			.isStrictlyEqualTo("{\"detail\":\"source_not_configured\"}");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM pipeline_runs", Integer.class)).isEqualTo(before);
	}

	@Test
	void retryingADeadPostIsRefusedAndARetryableOneIsQueued() {
		UUID dead = seeds.post(PostStage.DEAD, 3, "2026-10-01T08:00:00Z");
		UUID failed = seeds.post(PostStage.FAILED, 1, "2026-10-01T09:00:00Z");

		assertThat(start("{\"postIds\":[\"" + failed + "\",\"" + dead + "\"]}")).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.isStrictlyEqualTo("{\"detail\":\"post_dead\"}");
		MvcTestResult retried = start("{\"postIds\":[\"" + failed + "\"]}");
		assertThat(retried).hasStatus(HttpStatus.ACCEPTED);
		assertThat(retried).bodyJson().extractingPath("$[0].kind").isEqualTo("posts");
		assertThat(retried).bodyJson().extractingPath("$[0].requestedPostIds").asArray().containsExactly(failed.toString());
		awaitIdle();
		assertThat(jdbc.queryForObject("SELECT stage FROM posts WHERE id = ?", String.class, failed)).isEqualTo("assessed");
	}

	@Test
	void theListShowsRunsNewestFirstWithStepsCountersAndErrorsAndStablePages() {
		// Three runs created at the same instant, later than any other run: ordered by id, descending.
		List<UUID> ids = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			ids.add(jdbc.queryForObject("INSERT INTO pipeline_runs (kind, status, requested_by, created_at)"
					+ " VALUES ('retry', 'succeeded', ?, '2099-01-01T00:00:00Z') RETURNING id", UUID.class, admin));
		}
		ids.sort((a, b) -> b.toString().compareTo(a.toString()));
		UUID partial = ids.getFirst();
		jdbc.update("UPDATE pipeline_runs SET status = 'partial', processing_errors = 1, assessed_ok = 4,"
				+ " error = '1 post(s) with a technical error, no assessment stored: x (IllegalStateException: boom)'"
				+ " WHERE id = ?", partial);
		jdbc.update("INSERT INTO pipeline_run_steps (run_id, step, status, items_in, items_out) VALUES (?, 'select',"
				+ " 'succeeded', NULL, 5)", partial);
		jdbc.update("INSERT INTO pipeline_run_steps (run_id, step, status, items_in, items_out, started_at) VALUES (?,"
				+ " 'assess', 'succeeded', 5, 4, now() + interval '1 second')", partial);
		// The oldest of the three, alone on the second page: every reason a partial run can have.
		jdbc.update("UPDATE pipeline_runs SET status = 'partial', dead = 1, processing_errors = 2, skipped = 3 WHERE id = ?",
				ids.get(2));

		MvcTestResult page0 = mvc.get().uri("/api/admin/pipeline-runs?page=0&size=2").with(asAdmin()).exchange();
		MvcTestResult page1 = mvc.get().uri("/api/admin/pipeline-runs?page=1&size=2").with(asAdmin()).exchange();

		assertThat(page0).hasStatusOk();
		assertThat(page0).bodyJson().extractingPath("$.items[*].id").asArray()
			.containsExactly(ids.get(0).toString(), ids.get(1).toString());
		assertThat(page1).bodyJson().extractingPath("$.items[0].id").isEqualTo(ids.get(2).toString());
		assertThat(page0).bodyJson().extractingPath("$.items[0].status").isEqualTo("partial");
		assertThat(page0).bodyJson().extractingPath("$.items[0].processingErrors").isEqualTo(1);
		assertThat(page0).bodyJson().extractingPath("$.items[0].assessedOk").isEqualTo(4);
		assertThat(page0).bodyJson().extractingPath("$.items[0].error").asString().contains("IllegalStateException");
		assertThat(page0).bodyJson().extractingPath("$.items[0].steps[*].step").asArray().containsExactly("select", "assess");
		assertThat(page0).bodyJson().extractingPath("$.items[0].steps[1].itemsOut").isEqualTo(4);
		assertThat(page0).bodyJson().extractingPath("$.items[1].steps").asArray().isEmpty();
		// A run that fetched nothing has no source result, but the field is there.
		assertThat(page0).bodyJson().extractingPath("$.items[0].sourceResults").asArray().isEmpty();
		// A technical error needs attention; a run that succeeded does not.
		assertThat(page0).bodyJson().extractingPath("$.items[*].needsAttention").asArray().containsExactly(true, false);
		assertThat(page0).bodyJson().extractingPath("$.items[*].attentionReasons").asArray()
			.containsExactly(List.of("processing_errors"), List.of());
		// The API values, in the rule's order.
		assertThat(page1).bodyJson().extractingPath("$.items[0].attentionReasons").asArray()
			.containsExactly("dead_posts", "processing_errors", "skipped");
		assertThat(page0).bodyJson().extractingPath("$.items[*].scheduled").asArray().containsExactly(false, false);
		assertThat(mvc.get().uri("/api/admin/pipeline-runs?size=101").with(asAdmin())).hasStatus(HttpStatus.BAD_REQUEST);
		jdbc.update("DELETE FROM pipeline_run_steps WHERE run_id = ANY(?)", (Object) ids.toArray(UUID[]::new));
		jdbc.update("DELETE FROM pipeline_runs WHERE id = ANY(?)", (Object) ids.toArray(UUID[]::new));
	}

	@Test
	void aRunsPageShowsTheScheduledRunWithItsInterruptionProgressAndSteps() {
		UUID run = jdbc.queryForObject("INSERT INTO pipeline_runs (kind, status, requested_by, range_from, range_to,"
				+ " sources, started_at, finished_at, last_activity_at, fetched, new_posts, assessed_ok,"
				+ " assessed_failed, llm_retries, flex_fallbacks, deployment_id, interruption_cause, error)"
				+ " VALUES ('range', 'interrupted', ?, '2026-10-13T22:00:00Z', '2026-10-15T21:59:59Z', ARRAY['wykop'],"
				+ " '2026-10-16T00:30:05Z', '2026-10-16T00:41:00Z', '2026-10-16T00:40:30Z', 250, 200, 120, 2, 5, 3,"
				+ " 'deployment-a', 'deploy', 'interrupted: a new deployment took over') RETURNING id", UUID.class,
				SystemAccount.SCHEDULER.id());
		jdbc.update("INSERT INTO pipeline_run_steps (run_id, step, status, started_at, finished_at, items_out) VALUES"
				+ " (?, 'fetch', 'succeeded', '2026-10-16T00:30:06Z', '2026-10-16T00:31:00Z', 250)", run);
		jdbc.update("INSERT INTO pipeline_run_steps (run_id, step, status, started_at, finished_at, items_in) VALUES"
				+ " (?, 'assess', 'interrupted', '2026-10-16T00:32:00Z', '2026-10-16T00:41:00Z', 200)", run);

		MvcTestResult page = mvc.get().uri("/api/admin/pipeline-runs/" + run).with(asAdmin()).exchange();

		assertThat(page).hasStatusOk();
		assertThat(page).bodyJson().extractingPath("$.id").isEqualTo(run.toString());
		assertThat(page).bodyJson().extractingPath("$.status").isEqualTo("interrupted");
		assertThat(page).bodyJson().extractingPath("$.interruptionCause").isEqualTo("deploy");
		assertThat(page).bodyJson().extractingPath("$.needsAttention").isEqualTo(true);
		assertThat(page).bodyJson().extractingPath("$.attentionReasons").asArray().containsExactly("interrupted");
		assertThat(page).bodyJson().extractingPath("$.scheduled").isEqualTo(true);
		assertThat(page).bodyJson().extractingPath("$.lastActivityAt").isEqualTo("2026-10-16T00:40:30Z");
		assertThat(page).bodyJson().extractingPath("$.llmRetries").isEqualTo(5);
		assertThat(page).bodyJson().extractingPath("$.flexFallbacks").isEqualTo(3);
		assertThat(page).bodyJson().extractingPath("$.assessedOk").isEqualTo(120);
		assertThat(page).bodyJson().extractingPath("$.inFlight").isEqualTo(0);
		assertThat(page).bodyJson().extractingPath("$.error").isEqualTo("interrupted: a new deployment took over");
		assertThat(page).bodyJson().extractingPath("$.steps[*].step").asArray().containsExactly("fetch", "assess");
		assertThat(page).bodyJson().extractingPath("$.steps[*].status").asArray()
			.containsExactly("succeeded", "interrupted");
		assertThat(page).bodyJson().extractingPath("$.steps[1].itemsIn").isEqualTo(200);
	}

	@Test
	void aRangeRunShowsEachSourcesResultInSourceOrderOnItsPageAndInTheList() {
		// Created later than any other run, so it is the first of the list's first page.
		UUID run = jdbc.queryForObject("INSERT INTO pipeline_runs (kind, status, requested_by, range_from, range_to,"
				+ " sources, created_at, started_at, finished_at, fetched, skipped, new_posts, assessed_ok, sources_failed,"
				+ " error) VALUES ('range', 'partial', ?, '2026-10-13T22:00:00Z', '2026-10-14T21:59:59Z',"
				+ " ARRAY['wykop', 'reddit'], '2099-02-01T00:00:00Z', '2026-10-16T00:30:05Z', '2026-10-16T00:41:00Z', 12, 2,"
				+ " 10, 10, 1, 'reddit failed: IllegalStateException: 503') RETURNING id", UUID.class,
				SystemAccount.SCHEDULER.id());
		// Written in the opposite order: the response puts them in Source order.
		jdbc.update("INSERT INTO pipeline_run_sources (run_id, source, status, fetched, skipped, error, finished_at)"
				+ " VALUES (?, 'reddit', 'failed', 0, 0, 'IllegalStateException: 503', '2026-10-16T00:31:00Z')", run);
		jdbc.update("INSERT INTO pipeline_run_sources (run_id, source, status, fetched, skipped, error, finished_at)"
				+ " VALUES (?, 'wykop', 'succeeded', 12, 2, NULL, '2026-10-16T00:30:40Z')", run);
		try {
			MvcTestResult page = mvc.get().uri("/api/admin/pipeline-runs/" + run).with(asAdmin()).exchange();
			MvcTestResult list = mvc.get().uri("/api/admin/pipeline-runs").with(asAdmin()).exchange();

			assertThat(page).hasStatusOk();
			assertThat(page).bodyJson().extractingPath("$.sourcesFailed").isEqualTo(1);
			assertThat(page).bodyJson().extractingPath("$.sourcesEmpty").isEqualTo(0);
			assertThat(page).bodyJson().extractingPath("$.attentionReasons").asArray()
				.containsExactly("source_failed", "skipped");
			assertThat(page).bodyJson().extractingPath("$.sourceResults[*].source").asArray()
				.containsExactly("wykop", "reddit");
			assertThat(page).bodyJson().extractingPath("$.sourceResults[*].status").asArray()
				.containsExactly("succeeded", "failed");
			assertThat(page).bodyJson().extractingPath("$.sourceResults[*].fetched").asArray().containsExactly(12, 0);
			assertThat(page).bodyJson().extractingPath("$.sourceResults[*].skipped").asArray().containsExactly(2, 0);
			assertThat(page).bodyJson().extractingPath("$.sourceResults[0].error").isNull();
			assertThat(page).bodyJson().extractingPath("$.sourceResults[1].error").isEqualTo("IllegalStateException: 503");
			assertThat(page).bodyJson().extractingPath("$.sourceResults[*].finishedAt").asArray()
				.containsExactly("2026-10-16T00:30:40Z", "2026-10-16T00:31:00Z");
			assertThat(list).bodyJson().extractingPath("$.items[0].id").isEqualTo(run.toString());
			assertThat(list).bodyJson().extractingPath("$.items[0].sourceResults[*].source").asArray()
				.containsExactly("wykop", "reddit");
			assertThat(list).bodyJson().extractingPath("$.items[0].sourceResults[*].status").asArray()
				.containsExactly("succeeded", "failed");
			assertThat(list).bodyJson().extractingPath("$.items[0].sourcesFailed").isEqualTo(1);
			assertThat(list).bodyJson().extractingPath("$.items[0].sourcesEmpty").isEqualTo(0);
		}
		finally {
			jdbc.update("DELETE FROM pipeline_run_sources WHERE run_id = ?", run);
			jdbc.update("DELETE FROM pipeline_runs WHERE id = ?", run);
		}
	}

	@Test
	void aRunningRunCountsThePostsItIsAssessing() {
		UUID post = seeds.post(PostStage.STORED, 0, "2026-10-01T08:00:00Z");
		UUID run = jdbc.queryForObject("INSERT INTO pipeline_runs (kind, status, requested_by, started_at,"
				+ " last_activity_at) VALUES ('retry', 'running', ?, now(), now()) RETURNING id", UUID.class, admin);
		try {
			jdbc.update("UPDATE posts SET stage = 'assessing', assessing_run_id = ? WHERE id = ?", run, post);

			MvcTestResult page = mvc.get().uri("/api/admin/pipeline-runs/" + run).with(asAdmin()).exchange();
			MvcTestResult list = mvc.get().uri("/api/admin/pipeline-runs").with(asAdmin()).exchange();

			assertThat(page).bodyJson().extractingPath("$.status").isEqualTo("running");
			assertThat(page).bodyJson().extractingPath("$.inFlight").isEqualTo(1);
			assertThat(page).bodyJson().extractingPath("$.needsAttention").isEqualTo(false);
			assertThat(page).bodyJson().extractingPath("$.attentionReasons").asArray().isEmpty();
			assertThat(page).bodyJson().extractingPath("$.scheduled").isEqualTo(false);
			assertThat(page).bodyJson().extractingPath("$.interruptionCause").isNull();
			assertThat(page).bodyJson().extractingPath("$.lastActivityAt").isNotNull();
			assertThat(list).bodyJson().extractingPath("$.items[?(@.id == '" + run + "')].inFlight").asArray()
				.containsExactly(1);
		}
		finally {
			jdbc.update("UPDATE posts SET stage = 'stored', assessing_run_id = NULL WHERE id = ?", post);
			jdbc.update("UPDATE pipeline_runs SET status = 'failed', finished_at = now() WHERE id = ?", run);
		}
	}

	@Test
	void anUnknownRunIsNotFoundAndAMalformedIdInvalid() {
		assertThat(mvc.get().uri("/api/admin/pipeline-runs/" + UUID.randomUUID()).with(asAdmin()))
			.hasStatus(HttpStatus.NOT_FOUND)
			.bodyJson()
			.isStrictlyEqualTo("{\"detail\":\"not_found\"}");
		assertThat(mvc.get().uri("/api/admin/pipeline-runs/not-a-uuid").with(asAdmin()))
			.hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.isStrictlyEqualTo("{\"detail\":\"invalid_request\"}");
	}

	private MvcTestResult start(String body) {
		return mvc.post().uri("/api/admin/pipeline-runs").with(asAdmin()).contentType(MediaType.APPLICATION_JSON).content(body)
			.exchange();
	}

	private RequestPostProcessor asAdmin() {
		return user(admin.toString()).roles(Role.ADMIN.name());
	}

	private int activeRuns() {
		return jdbc.queryForObject("SELECT count(*) FROM pipeline_runs WHERE status IN ('queued', 'running')", Integer.class);
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
			return new WykopRepository(new WykopApiClient(WYKOP.baseUrl(), "k", "s", Duration.ofSeconds(60)), duration -> {
			}, Duration.ZERO);
		}

	}

}
