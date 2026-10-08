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
import pl.szczurradar.api.config.LlmProviderProperties.Provider;
import pl.szczurradar.api.config.LlmProviderProperties.ServiceTier;
import pl.szczurradar.api.llm.Channel;
import pl.szczurradar.api.llm.LlmClient;
import pl.szczurradar.api.llm.LlmResponse;
import pl.szczurradar.api.llm.OutputMode;
import pl.szczurradar.api.pipeline.PipelineRunService;
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
 * {@code POST} and {@code GET /api/admin/pipeline-runs} against the real queue: runs execute in the
 * background with Wykop on a local server (which a test can hold, to keep a run active) and a stubbed
 * LLM. Every test waits until no run is active before it ends.
 */
@SpringBootTest(properties = MigrateCommand.ENABLE_FLYWAY)
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, PipelineRunControllerIT.LocalWykop.class })
class PipelineRunControllerIT {

	/** Closed while a test needs a run to stay active; Wykop's searches wait for it. */
	static volatile CountDownLatch wykopGate = new CountDownLatch(0);

	private static final LocalWykopServer WYKOP = new LocalWykopServer(request -> {
		if (request.path().endsWith("/auth")) {
			return Reply.ok("{\"data\":{\"token\":\"t\"}}");
		}
		try {
			wykopGate.await(30, TimeUnit.SECONDS);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
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
				"{\"from\":\"2026-10-01\",\"to\":\"2026-10-01\",\"sources\":[\"reddit\"]}",
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
		assertThat(mvc.get().uri("/api/admin/pipeline-runs?size=101").with(asAdmin())).hasStatus(HttpStatus.BAD_REQUEST);
		jdbc.update("DELETE FROM pipeline_run_steps WHERE run_id = ANY(?)", (Object) ids.toArray(UUID[]::new));
		jdbc.update("DELETE FROM pipeline_runs WHERE id = ANY(?)", (Object) ids.toArray(UUID[]::new));
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
