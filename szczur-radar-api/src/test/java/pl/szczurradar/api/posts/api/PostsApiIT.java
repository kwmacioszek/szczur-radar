package pl.szczurradar.api.posts.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static pl.szczurradar.api.agents.assessor.ContractFixtures.target;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import pl.szczurradar.api.TestcontainersConfiguration;
import pl.szczurradar.api.agents.assessor.ContractFixtures;
import pl.szczurradar.api.agents.assessor.Prefilter;
import pl.szczurradar.api.agents.assessor.contract.Assertiveness;
import pl.szczurradar.api.agents.assessor.contract.AssessmentOutputV2;
import pl.szczurradar.api.agents.assessor.contract.ContractJson;
import pl.szczurradar.api.agents.assessor.contract.Direction;
import pl.szczurradar.api.agents.assessor.contract.Target;
import pl.szczurradar.api.agents.assessor.contract.TargetKind;
import pl.szczurradar.api.agents.assessor.validation.FailureClass;
import pl.szczurradar.api.cli.MigrateCommand;
import pl.szczurradar.api.pipeline.run.PipelineRunRepository;
import pl.szczurradar.api.posts.AssessmentRepository;
import pl.szczurradar.api.posts.PostRepository;
import pl.szczurradar.api.posts.PostSeeds;
import pl.szczurradar.api.posts.PostStage;
import pl.szczurradar.api.raw.RawPayloadStore;
import pl.szczurradar.api.user.Role;
import pl.szczurradar.api.user.UserRepository;

/**
 * {@code GET /api/posts} (the feed), {@code GET /api/posts/{id}} and {@code /raw} (one assessed post and its
 * raw payload) and {@code GET /api/admin/posts/failed}: order, pages, totals, which posts, the latest
 * attempt with its tie rule, the raw payload shown without a changed value, and the feed filter (FR-011a).
 * Each test rolls back.
 */
@SpringBootTest(properties = MigrateCommand.ENABLE_FLYWAY)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class PostsApiIT {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final String POST_NOT_FOUND = "{\"detail\":\"post_not_found\"}";

	@Autowired
	MockMvcTester mvc;

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

	@Autowired
	JdbcTemplate jdbc;

	private PostSeeds seeds;

	@BeforeEach
	void seeds() {
		seeds = new PostSeeds(posts, assessments, rawPayloads, runs, users, jdbc, entityManager);
	}

	@Test
	void theFeedIsAssessedPostsNewestFirstWithTheirLatestOkAssessment() {
		UUID older = seeds.post(PostStage.ASSESSED, 0, "2026-10-01T08:00:00Z");
		UUID newer = seeds.post(PostStage.ASSESSED, 1, "2026-10-01T09:00:00Z");
		UUID failed = seeds.post(PostStage.FAILED, 1, "2026-10-01T10:00:00Z");
		UUID dead = seeds.post(PostStage.DEAD, 3, "2026-10-01T11:00:00Z");
		UUID stored = seeds.post(PostStage.STORED, 0, "2026-10-01T12:00:00Z");
		seeds.ok(older, "2026-10-02T08:00:00Z");
		seeds.failed(newer, "2026-10-02T08:00:00Z", FailureClass.SCHEMA_VIOLATION);
		seeds.ok(newer, "2026-10-02T09:00:00Z");

		MvcTestResult result = mvc.get().uri("/api/posts").with(asUser()).exchange();

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.items[*].id").asArray()
			.containsExactly(newer.toString(), older.toString())
			.doesNotContain(failed.toString(), dead.toString(), stored.toString());
		assertThat(result).bodyJson().extractingPath("$.total").isEqualTo(2);
		assertThat(result).bodyJson().extractingPath("$.size").isEqualTo(50);
		assertThat(result).bodyJson().extractingPath("$.items[0].itemType").isEqualTo("post");
		assertThat(result).bodyJson().extractingPath("$.items[0].assessment.resultSource").isEqualTo("prefilter");
		assertThat(result).bodyJson().extractingPath("$.items[0].assessment.assessedAt").isEqualTo("2026-10-02T09:00:00Z");
		assertThat(result).bodyJson().extractingPath("$.items[0].assessment.output.relevance").isNotNull();
	}

	@Test
	void pagesAreStableForEqualPublicationTimesAndCountTheTotal() {
		UUID a = seeds.post(PostStage.ASSESSED, 0, "2026-10-01T09:00:00Z");
		UUID b = seeds.post(PostStage.ASSESSED, 0, "2026-10-01T09:00:00Z");
		UUID oldest = seeds.post(PostStage.ASSESSED, 0, "2026-10-01T08:00:00Z");
		List.of(a, b, oldest).forEach(post -> seeds.ok(post, "2026-10-02T08:00:00Z"));
		UUID first = PostSeeds.greater(a, b);
		UUID second = first.equals(a) ? b : a;

		MvcTestResult page0 = mvc.get().uri("/api/posts?page=0&size=2").with(asUser()).exchange();
		MvcTestResult page1 = mvc.get().uri("/api/posts?page=1&size=2").with(asUser()).exchange();

		assertThat(page0).bodyJson().extractingPath("$.items[*].id").asArray()
			.containsExactly(first.toString(), second.toString());
		assertThat(page1).bodyJson().extractingPath("$.items[*].id").asArray().containsExactly(oldest.toString());
		assertThat(page1).bodyJson().extractingPath("$.total").isEqualTo(3);
		assertThat(page1).bodyJson().extractingPath("$.page").isEqualTo(1);
	}

	@Test
	void equalAssessmentTimesPickTheLatestOkByTheGreatestId() {
		UUID post = seeds.post(PostStage.ASSESSED, 0, "2026-10-01T09:00:00Z");
		UUID one = seeds.ok(post, "2026-10-02T08:00:00Z").getId();
		UUID two = seeds.ok(post, "2026-10-02T08:00:00Z").getId();
		jdbc.update("UPDATE assessments SET output = jsonb_set(output, '{relevance}', '\"unclear\"') WHERE id = ?",
				PostSeeds.greater(one, two));
		entityManager.clear();

		assertThat(mvc.get().uri("/api/posts").with(asUser())).bodyJson()
			.extractingPath("$.items[0].assessment.output.relevance")
			.isEqualTo("unclear");
	}

	@Test
	void badPagingIsAnInvalidRequest() {
		for (String query : List.of("size=101", "size=0", "page=-1", "page=x", "size=x")) {
			assertThat(mvc.get().uri("/api/posts?" + query).with(asUser())).as(query)
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson()
				.isStrictlyEqualTo("{\"detail\":\"invalid_request\"}");
		}
	}

	@Test
	void anAssessedPostIsTheFeedItemWithItsLatestOkAssessment() {
		UUID post = seeds.post(PostStage.ASSESSED, 1, "2026-10-01T09:00:00Z");
		seeds.failed(post, "2026-10-02T08:00:00Z", FailureClass.SCHEMA_VIOLATION);
		seeds.ok(post, "2026-10-02T09:00:00Z");

		MvcTestResult result = mvc.get().uri("/api/posts/" + post).with(asUser()).exchange();

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.id").isEqualTo(post.toString());
		assertThat(result).bodyJson().extractingPath("$.assessment.assessedAt").isEqualTo("2026-10-02T09:00:00Z");
		JsonNode detail = tree(result);
		assertThat(detail.at("/assessment/output/claims"))
			.isEqualTo(ContractJson.MAPPER.valueToTree(ContractFixtures.validOutput().claims()));
		JsonNode listItem = tree(mvc.get().uri("/api/posts").with(asUser()).exchange()).get("items").get(0);
		assertThat(detail).isEqualTo(listItem);
	}

	@Test
	void aPostThatIsNotAssessedIsNotFoundWithItsRawPayload() {
		for (PostStage stage : List.of(PostStage.STORED, PostStage.FAILED, PostStage.DEAD)) {
			int attempts = switch (stage) {
				case STORED -> 0;
				case DEAD -> 3;
				default -> 1;
			};
			UUID post = seeds.post(stage, attempts, "2026-10-01T09:00:00Z");
			for (String uri : List.of("/api/posts/" + post, "/api/posts/" + post + "/raw")) {
				assertThat(mvc.get().uri(uri).with(asUser())).as(stage + " " + uri)
					.hasStatus(HttpStatus.NOT_FOUND)
					.bodyJson()
					.isStrictlyEqualTo(POST_NOT_FOUND);
			}
		}
	}

	@Test
	void anUnknownPostIsNotFoundAndAMalformedIdIsAnInvalidRequest() {
		UUID unknown = UUID.randomUUID();
		for (String uri : List.of("/api/posts/" + unknown, "/api/posts/" + unknown + "/raw")) {
			assertThat(mvc.get().uri(uri).with(asUser())).as(uri)
				.hasStatus(HttpStatus.NOT_FOUND)
				.bodyJson()
				.isStrictlyEqualTo(POST_NOT_FOUND);
		}
		for (String uri : List.of("/api/posts/abc", "/api/posts/abc/raw")) {
			assertThat(mvc.get().uri(uri).with(asUser())).as(uri)
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson()
				.isStrictlyEqualTo("{\"detail\":\"invalid_request\"}");
		}
	}

	@Test
	void theRawPayloadIsTheStoredJsonIndented() {
		UUID post = seeds.post(PostStage.ASSESSED, 0, "2026-10-01T09:00:00Z");
		seeds.ok(post, "2026-10-02T08:00:00Z");

		MvcTestResult result = mvc.get().uri("/api/posts/" + post + "/raw").with(asUser()).exchange();

		assertThat(result).hasStatusOk().hasContentType(MediaType.APPLICATION_JSON);
		String body = body(result);
		assertThat(body).contains("\n").contains("będzie");
		assertThat(JSON.readTree(body)).isEqualTo(JSON.readTree(PostSeeds.entry()));
	}

	@Test
	void theRawPayloadKeepsEveryNumberAndCharacterLiterally() {
		String raw = "{\"big\":12345678901234567890,\"pi\":3.14159265358979323846264338327950288,"
				+ "\"price\":1.50,\"text\":\"zażółć gęślą jaźń\"}";
		UUID post = seeds.post(PostStage.ASSESSED, 0, "2026-10-01T09:00:00Z", raw.getBytes(StandardCharsets.UTF_8));
		seeds.ok(post, "2026-10-02T08:00:00Z");

		MvcTestResult result = mvc.get().uri("/api/posts/" + post + "/raw").with(asUser()).exchange();

		assertThat(result).hasStatusOk();
		assertThat(body(result)).contains("\n")
			.contains("12345678901234567890")
			.contains("3.14159265358979323846264338327950288")
			.contains("1.50")
			.contains("zażółć gęślą jaźń");
	}

	@Test
	void theFilterMatchesTextAndDirectionOfTheSameTarget() {
		// validOutput(): XTB bullish, CDR not_expressed.
		UUID post = seeds.post(PostStage.ASSESSED, 0, "2026-10-01T09:00:00Z");
		seeds.ok(post, "2026-10-02T08:00:00Z");

		assertFeed(feed("q", "cdr", "direction", "bullish"), 0);
		assertFeed(feed("q", "xtb", "direction", "bullish"), 1, post);
		assertFeed(feed("q", "CDR", "direction", "not_expressed"), 1, post);
	}

	@Test
	void theTextIgnoresCaseButNotPolishLetters() {
		UUID post = seeds.post(PostStage.ASSESSED, 0, "2026-10-01T09:00:00Z");
		seeds.ok(post, "2026-10-02T08:00:00Z",
				output(target("t1", TargetKind.COMPANY, "ŻABKA", Direction.NOT_EXPRESSED, null)));

		assertFeed(feed("q", "żabka"), 1, post);
		// No unaccent: a query without Polish letters does not find a quote with them.
		assertFeed(feed("q", "zabka"), 0);
	}

	@Test
	void theKindIsACondition() {
		UUID market = seeds.post(PostStage.ASSESSED, 0, "2026-10-01T09:00:00Z");
		UUID orlen = seeds.post(PostStage.ASSESSED, 0, "2026-10-01T10:00:00Z");
		seeds.ok(market, "2026-10-02T08:00:00Z",
				output(target("t1", TargetKind.MACRO_TOPIC, "rynek", Direction.BEARISH, Assertiveness.HEDGED)));
		seeds.ok(orlen, "2026-10-02T08:00:00Z",
				output(target("t1", TargetKind.COMPANY, "Orlen", Direction.NOT_EXPRESSED, null)));

		assertFeed(feed("kind", "macro_topic"), 1, market);
		assertFeed(feed("q", "orlen"), 1, orlen);
		assertFeed(feed("q", "orlen", "kind", "macro_topic"), 0);
	}

	@Test
	void wildcardsInTheTextMatchOnlyThemselves() {
		UUID plain = seeds.post(PostStage.ASSESSED, 0, "2026-10-01T09:00:00Z");
		UUID percent = seeds.post(PostStage.ASSESSED, 0, "2026-10-01T10:00:00Z");
		UUID exclamation = seeds.post(PostStage.ASSESSED, 0, "2026-10-01T11:00:00Z");
		seeds.ok(plain, "2026-10-02T08:00:00Z");
		seeds.ok(percent, "2026-10-02T08:00:00Z",
				output(target("t1", TargetKind.MACRO_TOPIC, "WIG20 +5%", Direction.BULLISH, Assertiveness.UNHEDGED)));
		seeds.ok(exclamation, "2026-10-02T08:00:00Z",
				output(target("t1", TargetKind.COMPANY, "Kupuj PKO!", Direction.BULLISH, Assertiveness.UNHEDGED)));

		assertFeed(feed("q", "%"), 1, percent);
		assertFeed(feed("q", "_"), 0);
		// The escape character itself.
		assertFeed(feed("q", "!"), 1, exclamation);
	}

	@Test
	void theFilterLooksOnlyAtTheLatestOkAssessment() {
		UUID post = seeds.post(PostStage.ASSESSED, 0, "2026-10-01T09:00:00Z");
		seeds.ok(post, "2026-10-02T08:00:00Z",
				output(target("t1", TargetKind.COMPANY, "CDR", Direction.BEARISH, Assertiveness.UNHEDGED)));
		seeds.ok(post, "2026-10-02T09:00:00Z",
				output(target("t1", TargetKind.COMPANY, "XTB", Direction.BULLISH, Assertiveness.UNHEDGED)));

		assertFeed(feed("q", "cdr"), 0);
		assertFeed(feed("q", "xtb"), 1, post);
	}

	@Test
	void filteredPagesAreNewestFirstAndCountOnlyMatches() {
		UUID oldest = seeds.post(PostStage.ASSESSED, 0, "2026-10-01T08:00:00Z");
		UUID other = seeds.post(PostStage.ASSESSED, 0, "2026-10-01T09:00:00Z");
		UUID middle = seeds.post(PostStage.ASSESSED, 0, "2026-10-01T10:00:00Z");
		UUID newest = seeds.post(PostStage.ASSESSED, 0, "2026-10-01T11:00:00Z");
		List.of(oldest, middle, newest).forEach(post -> seeds.ok(post, "2026-10-02T08:00:00Z"));
		seeds.ok(other, "2026-10-02T08:00:00Z",
				output(target("t1", TargetKind.COMPANY, "Orlen", Direction.BULLISH, Assertiveness.UNHEDGED)));

		MvcTestResult page0 = feed("q", "xtb", "page", "0", "size", "2");
		MvcTestResult page1 = feed("q", "xtb", "page", "1", "size", "2");

		assertFeed(page0, 3, newest, middle);
		assertFeed(page1, 3, oldest);
		assertThat(page1).bodyJson().extractingPath("$.page").isEqualTo(1);
		assertThat(page1).bodyJson().extractingPath("$.size").isEqualTo(2);
	}

	@Test
	void postsWithoutTargetsAndPostsNotAssessedNeverMatch() {
		UUID match = seeds.post(PostStage.ASSESSED, 0, "2026-10-01T09:00:00Z");
		UUID noContent = seeds.post(PostStage.ASSESSED, 0, "2026-10-01T10:00:00Z");
		// Not assessed, yet with a matching ok attempt: only the stage keeps them out.
		UUID stored = seeds.post(PostStage.STORED, 0, "2026-10-01T11:00:00Z");
		UUID failed = seeds.post(PostStage.FAILED, 1, "2026-10-01T12:00:00Z");
		List.of(match, stored, failed).forEach(post -> seeds.ok(post, "2026-10-02T08:00:00Z"));
		seeds.ok(noContent, "2026-10-02T08:00:00Z", Prefilter.noContentOutput());

		for (String[] filter : List.of(new String[] { "q", "xtb" }, new String[] { "kind", "company" },
				new String[] { "direction", "bullish" }, new String[] { "direction", "not_expressed" })) {
			assertFeed(feed(filter), 1, match);
		}
		// White space only is no filter: the feed as without one.
		assertFeed(feed("q", " "), 2, noContent, match);
	}

	@Test
	void badFilterValuesAreAnInvalidRequest() {
		for (String[] query : List.of(new String[] { "kind", "spolka" }, new String[] { "direction", "BULLISH" },
				new String[] { "kind", "COMPANY" }, new String[] { "q", "a".repeat(101) },
				new String[] { "q", "or\u0000len" })) {
			assertThat(feed(query)).as(String.join("=", query))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson()
				.isStrictlyEqualTo("{\"detail\":\"invalid_request\"}");
		}
		assertThat(feed("q", "a".repeat(100))).hasStatusOk();
	}

	@Test
	void theFailedListHasFailedAndDeadPostsWithTheirLatestAttempt() {
		UUID failed = seeds.post(PostStage.FAILED, 1, "2026-10-01T09:00:00Z");
		UUID dead = seeds.post(PostStage.DEAD, 3, "2026-10-01T10:00:00Z");
		UUID assessed = seeds.post(PostStage.ASSESSED, 0, "2026-10-01T11:00:00Z");
		seeds.failed(failed, "2026-10-02T08:00:00Z", FailureClass.TIMEOUT);
		seeds.failed(dead, "2026-10-02T07:00:00Z", FailureClass.TIMEOUT);
		UUID tieA = seeds.failed(dead, "2026-10-02T08:00:00Z", FailureClass.SCHEMA_VIOLATION).getId();
		UUID tieB = seeds.failed(dead, "2026-10-02T08:00:00Z", FailureClass.FABRICATED_EVIDENCE).getId();
		String latestClass = PostSeeds.greater(tieA, tieB).equals(tieA) ? "schema_violation" : "fabricated_evidence";

		MvcTestResult result = mvc.get().uri("/api/admin/posts/failed").with(asAdmin()).exchange();

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.items[*].id").asArray()
			.containsExactly(dead.toString(), failed.toString())
			.doesNotContain(assessed.toString());
		assertThat(result).bodyJson().extractingPath("$.items[0].stage").isEqualTo("dead");
		assertThat(result).bodyJson().extractingPath("$.items[0].assessmentAttempts").isEqualTo(3);
		assertThat(result).bodyJson().extractingPath("$.items[0].lastAttempt.failureClass").isEqualTo(latestClass);
		assertThat(result).bodyJson().extractingPath("$.items[0].lastAttempt.provider").isEqualTo("gemini");
		assertThat(result).bodyJson().extractingPath("$.items[0].lastAttempt.model").isEqualTo("gemini-3.1-flash-lite");
		assertThat(result).bodyJson().extractingPath("$.items[1].lastAttempt.failureClass").isEqualTo("timeout");
		assertThat(result).bodyJson().extractingPath("$.items[1].lastAttempt.violations[0].rule").isEqualTo("rule");
		assertThat(result).bodyJson().extractingPath("$.total").isEqualTo(2);
	}

	private static String body(MvcTestResult result) {
		return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
	}

	private static JsonNode tree(MvcTestResult result) {
		return JSON.readTree(body(result));
	}

	/** {@code GET /api/posts} as a user, with {@code params} as name–value pairs. */
	private MvcTestResult feed(String... params) {
		MockMvcTester.MockMvcRequestBuilder request = mvc.get().uri("/api/posts").with(asUser());
		for (int i = 0; i < params.length; i += 2) {
			request = request.param(params[i], params[i + 1]);
		}
		return request.exchange();
	}

	private static void assertFeed(MvcTestResult result, int total, UUID... ids) {
		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.items[*].id").asArray()
			.containsExactly(Arrays.stream(ids).map(UUID::toString).toArray());
		assertThat(result).bodyJson().extractingPath("$.total").isEqualTo(total);
	}

	private static AssessmentOutputV2 output(Target... targets) {
		return ContractFixtures.investment(List.of(targets), List.of());
	}

	private static RequestPostProcessor asUser() {
		return user(UUID.randomUUID().toString()).roles(Role.USER.name());
	}

	private static RequestPostProcessor asAdmin() {
		return user(UUID.randomUUID().toString()).roles(Role.ADMIN.name());
	}

}
