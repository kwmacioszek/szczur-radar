package pl.szczurradar.api.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
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
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import pl.szczurradar.api.TestcontainersConfiguration;
import pl.szczurradar.api.alerts.AlertEvent;
import pl.szczurradar.api.alerts.AlertSettings;
import pl.szczurradar.api.alerts.LocalNtfy;
import pl.szczurradar.api.cli.MigrateCommand;
import pl.szczurradar.api.config.MutableClock;
import pl.szczurradar.api.internal.PipelineTriggerToken;
import pl.szczurradar.api.llm.LlmClient;
import pl.szczurradar.api.llm.LlmClientFactory;
import pl.szczurradar.api.llm.LlmProviderProperties;
import pl.szczurradar.api.llm.Provider;
import pl.szczurradar.api.llm.ServiceTier;
import pl.szczurradar.api.pipeline.OwnRuns;
import pl.szczurradar.api.pipeline.PipelineRunService;
import pl.szczurradar.api.pipeline.RandomOrderSeed;
import pl.szczurradar.api.pipeline.assess.AssessmentPool;
import pl.szczurradar.api.sources.LocalApiServer;
import pl.szczurradar.api.sources.reddit.LocalReddit;
import pl.szczurradar.api.sources.wykop.LocalWykop;
import pl.szczurradar.api.user.Role;
import pl.szczurradar.api.user.User;
import pl.szczurradar.api.user.UserRepository;

/**
 * The PR environment's verification steps that fakes can replay (S-31), as scenarios on the whole API: a
 * run starts over HTTP (the admin's route or the cron's), really ends with assessed posts, and its result is
 * read through the API's routes and the messages the operator's topic received. One class, one Spring
 * context and one Postgres for every scenario:
 *
 * <ul>
 *   <li><b>Sources:</b> Wykop ({@link LocalWykop}: #gielda of 2026-10-01, 12 entries and 4 links) and Reddit
 *       ({@link LocalReddit}: r/inwestowanie of 2026-10-27, 14 posts, and r/stocks of the same day, 6 posts and
 *       19 taken down), their production clients over local servers;</li>
 *   <li><b>LLM:</b> the production OpenRouter client ({@link LlmClientFactory}) on a {@link LocalApiServer}
 *       answering each post with its recorded provider answer ({@link RecordedProvider},
 *       {@code fixtures/llm/scenario-days/}): of the {@value RecordedDays#EXPECTED} posts that go to the
 *       model, those of the {@link #REJECTED} recordings fail the validator on every attempt, the others pass;</li>
 *   <li><b>Alerts:</b> the real queue and {@code NtfyAlerts} to a local topic ({@link LocalNtfy}), on a virtual
 *       clock;</li>
 *   <li><b>Clock:</b> "today" is {@link #NOW} ({@link MutableClock}). Only the context's clock is fixed: the
 *       assessor stamps {@code assessed_at} with the JVM's clock, so the night summary's {@code quality 7d}
 *       window never sees this class's attempts and {@code ASSESSMENT_QUALITY} cannot be raised here (both are
 *       {@code NightSummaryIT}'s).</li>
 * </ul>
 *
 * <p>Each scenario stands alone, as in {@code PipelineRunIT}, and the methods run in a random order (the seed is
 * logged and repeatable, {@link RandomOrderSeed}): before each one the run and post tables are emptied and the
 * fakes reset ({@link #reset}); after each one the API is left quiet ({@link #settle}) and no request reached the
 * provider without a recording. A run's status is written before its queue thread closes it (the admins'
 * notification, {@code RUN_NEEDS_ATTENTION}), and the queue sends on its own thread after its {@code taken} line,
 * so every wait here follows the order: the run's status, then the instance letting it go, then the alerts.
 */
@SpringBootTest(properties = { MigrateCommand.ENABLE_FLYWAY, PipelineTriggerToken.NAME + "=" + PrEnvScenariosIT.TOKEN,
		// Empty values override a real NTFY_URL / NTFY_TOKEN of the machine; the topic is LocalNtfy's.
		AlertSettings.NAME + "=", AlertSettings.TOKEN_NAME + "=" })
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, LocalWykop.class, LocalReddit.class, LocalNtfy.class,
		PrEnvScenariosIT.ScenarioClock.class })
@TestMethodOrder(MethodOrderer.Random.class)
@ExtendWith(OutputCaptureExtension.class)
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class PrEnvScenariosIT {

	/** A test-only cron token, as long as {@code openssl rand -hex 32} makes one. */
	static final String TOKEN = "3c5e7a9b1d3f5a7c9e1b3d5f7a9c1e3b5d7f9a1c3e5b7d9f1a3c5e7b9d1f3a5c";

	/** 11:00 in Warsaw on 29 October 2026: D-2 is r/inwestowanie's fixture day. */
	static final Instant NOW = Instant.parse("2026-10-29T10:00:00Z");

	/** The one day of a scenario's range. The fakes answer their fixture day for any range. */
	private static final LocalDate DAY = LocalDate.parse("2026-10-27");

	private static final MutableClock CLOCK = new MutableClock(NOW);

	private static final JsonMapper JSON = JsonMapper.builder().build();

	/** Wykop's day: its entries and links, none skipped. */
	private static final int WYKOP_FETCHED = LocalWykop.ENTRIES + LocalWykop.LINKS;

	/** r/inwestowanie's day (14 posts) and r/stocks' visible posts (6 of 25). */
	private static final int REDDIT_FETCHED = 14 + 6;

	private static final int FETCHED = WYKOP_FETCHED + REDDIT_FETCHED;

	/** The posts the prefilter decides without a call (Wykop's entries 1, 4 and 8). */
	private static final int PREFILTERED = FETCHED - RecordedDays.EXPECTED;

	/**
	 * The recordings the validator rejects, from the verdicts in {@code fixtures/llm/scenario-days/README.md}: they
	 * fail on every attempt, so their posts are {@code failed} after a run and leftovers for the next.
	 */
	private static final Set<String> REJECTED = Set.of("reddit-1xgrbst.json", "reddit-1xgnw3d.json");

	/** The recordings the validator accepts. */
	private static final int MODEL_OK = RecordedDays.EXPECTED - REJECTED.size();

	/** A run with a rejected recording ends {@code partial}: its failed posts are retried, nothing needs attention. */
	private static final String RANGE_STATUS = REJECTED.isEmpty() ? "succeeded" : "partial";

	/** The queue's line once it took an event ({@code OperatorAlertQueue.raise}). */
	private static final Pattern TAKEN = Pattern.compile("Operator alert [A-Z_]+ \\((run [0-9a-f-]+|no run)\\) taken");

	private static final Duration WAIT = Duration.ofSeconds(60);

	private static final RecordedProvider PROVIDER = new RecordedProvider();

	private static final LocalApiServer LLM = new LocalApiServer(RecordedProvider.BASE_PATH, PROVIDER);

	/** The production OpenRouter client on the recordings' server, without waits between attempts. */
	@TestBean
	LlmClient llmClient;

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	PipelineRunService service;

	/** The dispatcher's LLM threads, whose post tasks may outlive a run's end. */
	@Autowired
	AssessmentPool llmPool;

	@Autowired
	UserRepository users;

	/** This scenario's output (the extension captures each test on its own). */
	private CapturedOutput output;

	private UUID admin;

	static LlmClient llmClient() {
		return LlmClientFactory.create(new LlmProviderProperties(Provider.OPENROUTER, "openai/gpt-6-luna",
				"synthetic-key", ServiceTier.DEFAULT, null, LlmProviderProperties.MIN_TIMEOUT),
				RecordedProvider.baseUrl(LLM), wait -> {
				});
	}

	@BeforeAll
	static void seed() {
		RandomOrderSeed.log(PrEnvScenariosIT.class);
	}

	@AfterAll
	static void stopProvider() {
		LLM.close();
	}

	/**
	 * Starts from empty run and post tables, once the previous scenario left nothing running: one
	 * {@code TRUNCATE} closed under the foreign keys, as in {@code PipelineRunIT}. {@code users} stays: it holds
	 * {@code pipeline_runs.requested_by} and the cron's {@code SCHEDULER}. The admin (who can log in, so a run
	 * that needs attention notifies them and is raised as {@code RUN_NEEDS_ATTENTION}) is created once. Wykop
	 * answers its day, Reddit reads r/inwestowanie and r/stocks, each its own fixture page, and the topic, the
	 * provider's unmatched requests and the clock start again.
	 */
	@BeforeEach
	void reset(CapturedOutput output) {
		this.output = output;
		assertThat(OwnRuns.of(service)).as("runs of the service left by the previous scenario").isEmpty();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM pipeline_runs WHERE status IN ('queued', 'running')",
				Integer.class)).as("queued or running runs left by the previous scenario").isZero();
		jdbc.execute("TRUNCATE notifications, pipeline_run_steps, pipeline_run_sources, assessments, posts,"
				+ " raw_payloads, pipeline_runs");
		LocalWykop.reset();
		redditOfTheDay();
		LocalNtfy.clear();
		PROVIDER.clearUnmatched();
		CLOCK.set(NOW);
		User account = users.findByEmail("scenario-admin@example.com")
			.orElseGet(() -> users.save(new User("scenario-admin@example.com", null, Role.ADMIN)));
		assertThat(account.isLoginEnabled()).isTrue();
		admin = account.getId();
	}

	/**
	 * Waits until the API is quiet — no run of the service's own, the LLM pool idle, and every event the queue
	 * took reported (as many returns from {@code NtfyAlerts} as {@code taken} lines in this scenario's output) —
	 * then requires that every request to the provider had its recording.
	 */
	@AfterEach
	void settle() {
		awaitQuiet();
		assertThat(PROVIDER.unmatched()).extracting(request -> RecordedProvider.userMessage(request.body()))
			.as("requests to the provider without a recording")
			.isEmpty();
	}

	/**
	 * S-17 step 1, S-20 step 5.2, S-27 steps 5.4–5.5 and S-32's PR-env step: an admin's range of one day, no
	 * source named, ends with every step closed, each source's result, the deploy guard released, Reddit's post in
	 * the feed with its raw payload and Wykop's post with the model's recorded targets.
	 */
	@Test
	void aRangeOfTwoSourcesRunsFromItsStartToTheFeed() {
		List<Started> started = startRange(DAY, DAY);

		assertThat(started).extracting(Started::kind).containsExactly("range");
		JsonNode run = awaitEnded(started.getFirst().id());
		assertRangeOfTheDay(run);
		assertThat(run.get("newPosts").asInt()).isEqualTo(FETCHED);
		assertThat(run.get("assessedOk").asInt()).isEqualTo(PREFILTERED + MODEL_OK);
		assertThat(run.get("assessedFailed").asInt()).isEqualTo(REJECTED.size());
		// The failed posts are those of the rejected recordings, not merely as many.
		assertThat(jdbc.queryForList("SELECT source_item_id FROM posts WHERE stage = 'failed'", String.class))
			.containsExactlyInAnyOrderElementsOf(RecordedDays.posts()
				.stream()
				.filter(post -> REJECTED.contains(post.file()))
				.map(RecordedDays.Post::id)
				.toList());
		assertThat(run.get("dead").asInt()).isZero();
		assertThat(run.get("processingErrors").asInt()).isZero();
		assertThat(run.get("needsAttention").asBoolean()).isFalse();
		assertThat(run.get("error").isNull()).as("error").isTrue();
		assertThat(steps(run)).containsExactly("fetch:succeeded", "store:succeeded", "select:succeeded",
				"assess:succeeded", "summarize:succeeded");

		// The deploy guard lets a deploy through once the run ended.
		assertThat(mvc.get().uri("/api/deploy-safety")).hasStatusOk()
			.bodyJson()
			.isStrictlyEqualTo("{\"safe\":true,\"reason\":null}");

		// The feed shows the assessed posts: every one but the failed ones.
		JsonNode feed = get("/api/posts?size=100");
		assertThat(feed.get("total").asInt()).isEqualTo(PREFILTERED + MODEL_OK);
		List<JsonNode> items = new ArrayList<>();
		feed.get("items").forEach(items::add);
		JsonNode reddit = items.stream()
			.filter(item -> "reddit".equals(item.get("source").asString())
					&& "inwestowanie".equals(item.get("channel").asString())
					&& item.get("webUrl").asString().startsWith("https://www.reddit.com/r/inwestowanie/comments/"))
			.findFirst()
			.orElseThrow(() -> new AssertionError("no post of r/inwestowanie in the feed: " + feed));
		JsonNode raw = get("/api/posts/" + reddit.get("id").asString() + "/raw");
		assertThat(raw.has("subreddit")).as("the raw payload of a Reddit post has its subreddit: %s", raw).isTrue();

		// A real assessment: Wykop's entry 2 carries the targets of its recorded answer.
		UUID entry = jdbc.queryForObject("SELECT id FROM posts WHERE source = 'wykop' AND item_type = 'post'"
				+ " AND source_item_id = '2'", UUID.class);
		JsonNode assessed = items.stream()
			.filter(item -> entry.toString().equals(item.get("id").asString()))
			.findFirst()
			.orElseThrow(() -> new AssertionError("Wykop's entry 2 is not in the feed"));
		JsonNode recordedTargets = recordedOutput("wykop-entry-2.json").get("targets");
		assertThat(recordedTargets).isNotEmpty();
		assertThat(assessed.at("/assessment/resultSource").asString()).isEqualTo("model");
		assertThat(assessed.at("/assessment/output/targets")).isEqualTo(recordedTargets);

		// Reddit's removed posts are counted only in the log, and nothing logged an error.
		assertThat(output.getAll())
			.contains("Reddit r/stocks: 6 post(s) in range, 19 removed or deleted left out, 0 of unexpected shape");
		assertThat(output.getAll().lines().filter(line -> line.contains(" ERROR "))).isEmpty();
	}

	/**
	 * S-27 step 5.6: the same range a second time stores no post twice. The posts whose recording the validator
	 * rejects are leftovers, so the start queues a retry run before the range and the scenario waits for both.
	 */
	@Test
	void theSameRangeAgainStoresNoDuplicates() {
		assertRangeOfTheDay(awaitEnded(startRange(DAY, DAY).getLast().id()));
		int posts = jdbc.queryForObject("SELECT count(*) FROM posts", Integer.class);
		assertThat(posts).isEqualTo(FETCHED);

		List<Started> again = startRange(DAY, DAY);

		assertThat(again).extracting(Started::kind)
			.containsExactlyElementsOf(REJECTED.isEmpty() ? List.of("range") : List.of("retry", "range"));
		List<JsonNode> ended = again.stream().map(run -> awaitEnded(run.id())).toList();
		if (!REJECTED.isEmpty()) {
			// The retry run tries the rejected posts once more; they fail again (their second attempt).
			JsonNode retry = ended.getFirst();
			assertThat(retry.get("status").asString()).isEqualTo("partial");
			assertThat(retry.get("assessedOk").asInt()).isZero();
			assertThat(retry.get("assessedFailed").asInt()).isEqualTo(REJECTED.size());
			assertThat(retry.get("dead").asInt()).isZero();
			assertThat(attentionReasons(retry)).isEmpty();
		}
		JsonNode range = ended.getLast();
		assertThat(range.get("status").asString()).isEqualTo("succeeded");
		assertThat(range.get("fetched").asInt()).isEqualTo(FETCHED);
		assertThat(range.get("newPosts").asInt()).isZero();
		assertThat(sourceResults(range)).containsExactly("wykop:succeeded:" + WYKOP_FETCHED + ":0:",
				"reddit:succeeded:" + REDDIT_FETCHED + ":0:");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM posts", Integer.class)).isEqualTo(posts);
	}

	/**
	 * S-20 (a source's failure is its own result) with S-18's signals: Reddit down in an admin's range, so the run
	 * ends {@code partial} with {@code source_failed}, Wykop's posts reach the feed, the admin gets the bell and the
	 * operator one {@code RUN_NEEDS_ATTENTION} message.
	 */
	@Test
	void aSourceDownInAnAdminsRunIsItsOwnResult() {
		LocalReddit.down();

		List<Started> started = startRange(DAY, DAY);

		assertThat(started).extracting(Started::kind).containsExactly("range");
		UUID id = started.getFirst().id();
		JsonNode run = awaitEnded(id);
		assertThat(run.get("status").asString()).isEqualTo("partial");
		assertThat(attentionReasons(run)).first().isEqualTo("source_failed");
		assertThat(run.get("error").asString()).startsWith("reddit failed: RedditApiException");
		List<String> sources = sourceResults(run);
		assertThat(sources).hasSize(2);
		assertThat(sources.getFirst()).isEqualTo("wykop:succeeded:" + WYKOP_FETCHED + ":0:");
		assertThat(sources.getLast()).startsWith("reddit:failed:0:0:RedditApiException");

		// Wykop's day is assessed (its recordings all pass) and in the feed; Reddit gave nothing.
		JsonNode feed = get("/api/posts?size=100");
		assertThat(feed.get("total").asInt()).isEqualTo(WYKOP_FETCHED);
		assertThat(feed.get("items")).allSatisfy(item -> assertThat(item.get("source").asString()).isEqualTo("wykop"));

		assertThat(notifiedRuns()).contains(id);
		LocalNtfy.awaitMessage(AlertEvent.RUN_NEEDS_ATTENTION, id, WAIT);
		awaitQuiet();
		assertThat(LocalNtfy.messages(AlertEvent.RUN_NEEDS_ATTENTION, id)).singleElement()
			.extracting(LocalNtfy.Message::body)
			.asString()
			.contains("(range, admin) ended partial", "error: reddit failed", "/admin/runs/" + id);
	}

	/**
	 * S-20: Reddit silent next to Wykop's posts in the cron's run gives {@code partial} (the cron's exit 0), and
	 * the API raises it for a run of the cron too.
	 */
	@Test
	void anEmptySourceInTheCronsRunEndsPartial() {
		LocalReddit.empty();

		List<Started> started = startDaily();

		assertThat(started).extracting(Started::kind).containsExactly("range");
		UUID id = started.getFirst().id();
		JsonNode run = awaitEnded(id);
		assertThat(getInternal("/internal/pipeline-runs/" + id).get("status").asString()).isEqualTo("partial");
		assertThat(run.get("scheduled").asBoolean()).isTrue();
		assertThat(attentionReasons(run)).contains("source_empty");
		assertThat(run.get("error").asString()).contains("reddit gave no post to store (fetched 0, skipped 0)");
		// The night's range: the day before yesterday and yesterday of the clock.
		LocalDate today = LocalDate.ofInstant(NOW, PipelineRunService.ZONE);
		assertThat(day(run.get("rangeFrom"))).isEqualTo(today.minusDays(2));
		assertThat(day(run.get("rangeTo"))).isEqualTo(today.minusDays(1));

		assertThat(LocalNtfy.awaitMessage(AlertEvent.RUN_NEEDS_ATTENTION, id, WAIT).body())
			.contains("(range, cron) ended partial");
	}

	/**
	 * S-18 steps 3–5 (the run of 2004-01-01) and S-08/S-20's rule: no source gave a post, so the admin's run fails
	 * in its fetch, the admin gets the bell and the operator one message, which carries neither the topic's token
	 * nor its address, delivered.
	 */
	@Test
	void noSourceWithAPostFailsTheAdminsRunAndRaisesIt() {
		LocalWykop.empty();
		LocalReddit.empty();

		List<Started> started = startRange(DAY, DAY);

		assertThat(started).extracting(Started::kind).containsExactly("range");
		UUID id = started.getFirst().id();
		JsonNode run = awaitEnded(id);
		assertThat(run.get("status").asString()).isEqualTo("failed");
		assertThat(run.get("error").asString()).contains("NoPostsFetchedException", "wykop gave no post to store",
				"reddit gave no post to store");
		assertThat(steps(run)).contains("fetch:failed")
			.noneMatch(step -> step.startsWith("store:") || step.startsWith("select:") || step.startsWith("assess:"));

		assertThat(notifiedRuns()).contains(id);
		assertThat(output.getAll()).contains("Operator alert RUN_NEEDS_ATTENTION (run " + id + ") taken");
		LocalNtfy.Message message = LocalNtfy.awaitMessage(AlertEvent.RUN_NEEDS_ATTENTION, id, WAIT);
		assertThat(message.body()).contains("(range, admin) ended failed: dead 0, errors 0, skipped 0; error:",
				"/admin/runs/" + id)
			.doesNotContain(LocalNtfy.TOKEN, LocalNtfy.topicUrl().toString());
		awaitLog("Operator alert RUN_NEEDS_ATTENTION (run " + id + ") reported: DELIVERED");
		awaitQuiet();
		assertThat(LocalNtfy.messages(AlertEvent.RUN_NEEDS_ATTENTION, id)).hasSize(1);
	}

	/**
	 * S-18's rule: the cron's run that ended {@code failed} is reported by the cron's exit code, so the API raises
	 * nothing for it — though the admin still gets the bell.
	 */
	@Test
	void aFailedCronRunIsReportedOnlyByTheCron() {
		LocalWykop.empty();
		LocalReddit.empty();

		List<Started> started = startDaily();

		assertThat(started).extracting(Started::kind).containsExactly("range");
		UUID id = started.getFirst().id();
		awaitEnded(id);
		assertThat(getInternal("/internal/pipeline-runs/" + id).get("status").asString()).isEqualTo("failed");
		assertThat(notifiedRuns()).contains(id);
		// The run's thread raises before the instance lets the run go, so the line would be there by now.
		assertThat(output.getAll()).doesNotContain("Operator alert RUN_NEEDS_ATTENTION (run " + id + ") taken");
		awaitQuiet();
		assertThat(LocalNtfy.messages()).noneMatch(message -> message.body().contains(id.toString()));
	}

	/**
	 * S-13 step 3.2 and S-20's change of coverage: a day counts as covered for a source only from its
	 * {@code succeeded} result, not because the run requested it. "Today" is the clock's day D; the window is
	 * D-15…D-2, and a source's days start at its first covered day.
	 */
	@Test
	void aDayWithoutDataIsListedUntilEachSourceCoversIt() {
		LocalDate today = LocalDate.ofInstant(NOW, PipelineRunService.ZONE);
		LocalDate dayBeforeYesterday = today.minusDays(2);

		// No run yet: no source was ever covered, so none misses a day.
		assertThat(missingDays(today)).isEmpty();

		// D-3 with both sources: D-2 is missing for both.
		awaitAll(startRange(today.minusDays(3), today.minusDays(3)));
		assertThat(missingDays(today)).containsExactly(dayBeforeYesterday + ":[wykop, reddit]");

		// D-2 with Reddit down: the run requested Reddit, but only Wykop covers the day.
		LocalReddit.down();
		List<JsonNode> withRedditDown = awaitAll(startRange(dayBeforeYesterday, dayBeforeYesterday));
		assertThat(sourceResults(withRedditDown.getLast())).extracting(result -> result.split(":")[1])
			.containsExactly("succeeded", "failed");
		assertThat(missingDays(today)).containsExactly(dayBeforeYesterday + ":[reddit]");

		// D-2 again with Reddit back: nothing is missing.
		redditOfTheDay();
		awaitAll(startRange(dayBeforeYesterday, dayBeforeYesterday));
		assertThat(missingDays(today)).isEmpty();
	}

	/**
	 * S-34's PR-env check: the cron's night — its run, then the night's summary of its runs, returned for the
	 * cron's log and sent to the operator as {@code NIGHT_SUMMARY}; a run that needs no attention (or only for a
	 * dead post) raises no {@code RUN_NEEDS_ATTENTION} of its own.
	 */
	@Test
	void theCronsNightEndsWithItsSummaryInTheTopic() {
		List<Started> started = startDaily();
		List<JsonNode> night = awaitAll(started);

		MvcTestResult result = mvc.post()
			.uri("/internal/pipeline-runs/nightly-summary")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"runs\":" + started.stream().map(run -> "\"" + run.id() + "\"").toList() + "}")
			.exchange();

		assertThat(result).hasStatusOk();
		String text = JSON.readTree(body(result)).get("text").asString();
		assertThat(text.lines()).anySatisfy(
				line -> assertThat(line).startsWith("wykop: fetched " + WYKOP_FETCHED + ", skipped 0, "));
		assertThat(text.lines()).anySatisfy(
				line -> assertThat(line).startsWith("reddit: fetched " + REDDIT_FETCHED + ", skipped 0, "));
		assertThat(LocalNtfy.awaitMessage(AlertEvent.NIGHT_SUMMARY, text.strip(), WAIT).body()).contains(text.strip());

		for (JsonNode run : night) {
			UUID id = UUID.fromString(run.get("id").asString());
			assertThat(attentionReasons(run)).as("run %s needs no attention, or only for a dead post", id)
				.isSubsetOf("dead_posts");
			assertThat(output.getAll()).doesNotContain("Operator alert RUN_NEEDS_ATTENTION (run " + id + ") taken");
		}
		awaitQuiet();
		for (Started run : started) {
			assertThat(LocalNtfy.messages(AlertEvent.RUN_NEEDS_ATTENTION, run.id())).isEmpty();
		}
	}

	/**
	 * The range of the day as its first run ends it: every element fetched and stored, none skipped, each source
	 * {@code succeeded}, Wykop then Reddit, and nothing that needs attention.
	 */
	private static void assertRangeOfTheDay(JsonNode run) {
		assertThat(run.get("kind").asString()).isEqualTo("range");
		assertThat(run.get("status").asString()).isEqualTo(RANGE_STATUS);
		assertThat(run.get("fetched").asInt()).isEqualTo(FETCHED);
		assertThat(run.get("skipped").asInt()).isZero();
		assertThat(attentionReasons(run)).isEmpty();
		assertThat(sourceResults(run)).containsExactly("wykop:succeeded:" + WYKOP_FETCHED + ":0:",
				"reddit:succeeded:" + REDDIT_FETCHED + ":0:");
	}

	/** {@code POST /api/admin/pipeline-runs} for the range {@code from}–{@code to}, every configured source. */
	private List<Started> startRange(LocalDate from, LocalDate to) {
		MvcTestResult result = mvc.post()
			.uri("/api/admin/pipeline-runs")
			.with(asAdmin())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"from\":\"" + from + "\",\"to\":\"" + to + "\"}")
			.exchange();
		assertThat(result).hasStatus(HttpStatus.ACCEPTED);
		return started(result);
	}

	/** {@code POST /internal/pipeline-runs/daily} with the cron's token: D-2…D-1 of the clock. */
	private List<Started> startDaily() {
		MvcTestResult result = mvc.post()
			.uri("/internal/pipeline-runs/daily")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
			.exchange();
		assertThat(result).hasStatus(HttpStatus.ACCEPTED);
		return started(result);
	}

	/**
	 * Waits until the run's page shows it ended, then until the service let it go (its notification written and
	 * its alert raised or left out), and returns the page as it is then.
	 */
	private JsonNode awaitEnded(UUID runId) {
		long deadline = System.nanoTime() + WAIT.toNanos();
		while (Set.of("queued", "running").contains(get("/api/admin/pipeline-runs/" + runId).get("status").asString())) {
			if (System.nanoTime() > deadline) {
				throw new AssertionError("run " + runId + " did not end: " + get("/api/admin/pipeline-runs/" + runId));
			}
			pause();
		}
		while (OwnRuns.of(service).contains(runId)) {
			if (System.nanoTime() > deadline) {
				throw new AssertionError("run " + runId + " was never let go by the service");
			}
			pause();
		}
		return get("/api/admin/pipeline-runs/" + runId);
	}

	/** Waits for each run of a start, in the order the queue runs them, and returns their pages. */
	private List<JsonNode> awaitAll(List<Started> started) {
		return started.stream().map(run -> awaitEnded(run.id())).toList();
	}

	/** {@code GET uri} with the admin's session: 200, its JSON. */
	private JsonNode get(String uri) {
		MvcTestResult result = mvc.get().uri(uri).with(asAdmin()).exchange();
		assertThat(result).as("GET " + uri).hasStatusOk();
		return JSON.readTree(body(result));
	}

	/** {@code GET uri} with the cron's token: 200, its JSON. */
	private JsonNode getInternal(String uri) {
		MvcTestResult result = mvc.get().uri(uri).header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN).exchange();
		assertThat(result).as("GET " + uri).hasStatusOk();
		return JSON.readTree(body(result));
	}

	/** The runs of the admin's notifications ({@code GET /api/admin/notifications}). */
	private List<UUID> notifiedRuns() {
		List<UUID> runs = new ArrayList<>();
		get("/api/admin/notifications").get("items").forEach(item -> {
			if (!item.get("run").isNull()) {
				runs.add(UUID.fromString(item.at("/run/id").asString()));
			}
		});
		return runs;
	}

	/**
	 * {@code GET /api/admin/pipeline-runs/missing-days}, its window checked to be D-15…D-2 of {@code today}: the
	 * days as {@code day:[sources]}.
	 */
	private List<String> missingDays(LocalDate today) {
		JsonNode missing = get("/api/admin/pipeline-runs/missing-days");
		assertThat(missing.get("from").asString()).isEqualTo(today.minusDays(15).toString());
		assertThat(missing.get("to").asString()).isEqualTo(today.minusDays(2).toString());
		List<String> days = new ArrayList<>();
		missing.get("days").forEach(day -> {
			List<String> sources = new ArrayList<>();
			day.get("sources").forEach(source -> sources.add(source.asString()));
			days.add(day.get("day").asString() + ":" + sources);
		});
		return days;
	}

	/**
	 * Waits until the API is quiet — no run of the service's own, the LLM pool idle, and every event the queue
	 * took reported (as many returns from {@code NtfyAlerts} as {@code taken} lines in this scenario's output).
	 */
	private void awaitQuiet() {
		long deadline = System.nanoTime() + WAIT.toNanos();
		while (!quiet()) {
			if (System.nanoTime() > deadline) {
				throw new AssertionError("the API did not settle: own runs " + OwnRuns.of(service) + ", LLM pool active "
						+ llmPool.activeTasks() + ", queued " + llmPool.queuedTasks() + ", alerts taken " + taken()
						+ ", reported " + LocalNtfy.reported());
			}
			pause();
		}
	}

	/** Waits until this scenario's output has {@code line}: the queue logs its reports on its own thread. */
	private void awaitLog(String line) {
		long deadline = System.nanoTime() + WAIT.toNanos();
		while (!output.getAll().contains(line)) {
			if (System.nanoTime() > deadline) {
				throw new AssertionError("no log line \"" + line + "\" within " + WAIT.toSeconds() + " s");
			}
			pause();
		}
	}

	/** No run of the service's own, the LLM pool idle and every alert the queue took reported. */
	private boolean quiet() {
		return OwnRuns.of(service).isEmpty() && llmPool.idle() && LocalNtfy.reported() == taken();
	}

	/** The events the queue took in this scenario: its {@code taken} lines. */
	private int taken() {
		return (int) output.getAll().lines().filter(line -> TAKEN.matcher(line).find()).count();
	}

	/** Reddit's day: r/inwestowanie and r/stocks, each its own fixture page, nothing down or empty. */
	private static void redditOfTheDay() {
		LocalReddit.reset();
		LocalReddit.configure(List.of("inwestowanie", "stocks"));
		LocalReddit.page("inwestowanie", "search-posts-inwestowanie.json");
		LocalReddit.page("stocks", "search-posts-stocks.json");
	}

	/** An instant of a run's page as its Warsaw calendar day. */
	private static LocalDate day(JsonNode instant) {
		return LocalDate.ofInstant(Instant.parse(instant.asString()), PipelineRunService.ZONE);
	}

	private RequestPostProcessor asAdmin() {
		return user(admin.toString()).roles(Role.ADMIN.name());
	}

	/** The model's output in the recording {@code file}: the answer's text, parsed. */
	private static JsonNode recordedOutput(String file) {
		RecordedDays.Post post = RecordedDays.posts()
			.stream()
			.filter(candidate -> candidate.file().equals(file))
			.findFirst()
			.orElseThrow();
		JsonNode answer = JSON.readTree(RecordedDays.recording(post));
		return JSON.readTree(answer.at("/choices/0/message/content").asString());
	}

	/** The run's steps as {@code step:status}, in the order they started. */
	private static List<String> steps(JsonNode run) {
		List<String> steps = new ArrayList<>();
		run.get("steps").forEach(step -> steps.add(step.get("step").asString() + ":" + step.get("status").asString()));
		return steps;
	}

	/** The run's sources' results as {@code source:status:fetched:skipped:error}, in the page's order. */
	private static List<String> sourceResults(JsonNode run) {
		List<String> results = new ArrayList<>();
		run.get("sourceResults")
			.forEach(result -> results.add(result.get("source").asString() + ":" + result.get("status").asString() + ":"
					+ result.get("fetched").asInt() + ":" + result.get("skipped").asInt() + ":"
					+ (result.get("error").isNull() ? "" : result.get("error").asString())));
		return results;
	}

	private static List<String> attentionReasons(JsonNode run) {
		List<String> reasons = new ArrayList<>();
		run.get("attentionReasons").forEach(reason -> reasons.add(reason.asString()));
		return reasons;
	}

	private static List<Started> started(MvcTestResult result) {
		List<Started> started = new ArrayList<>();
		JSON.readTree(body(result))
			.forEach(run -> started.add(new Started(UUID.fromString(run.get("id").asString()), run.get("kind").asString())));
		return started;
	}

	private static String body(MvcTestResult result) {
		return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
	}

	private static void pause() {
		try {
			Thread.sleep(50);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(ex);
		}
	}

	/** One run a start created, in the order the queue runs them. */
	private record Started(UUID id, String kind) {
	}

	/** "Today" of the scenarios: the range request's check, the night's range and {@code missing-days}. */
	@TestConfiguration(proxyBeanMethods = false)
	static class ScenarioClock {

		@Bean
		@Primary
		Clock scenarioClock() {
			return CLOCK;
		}

	}

}
