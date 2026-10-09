package pl.szczurradar.api.posts.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import pl.szczurradar.api.TestcontainersConfiguration;
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
 * {@code GET /api/posts} (the feed) and {@code GET /api/admin/posts/failed}: order, pages, totals, which
 * posts, and the latest attempt with its tie rule. Each test rolls back.
 */
@SpringBootTest(properties = MigrateCommand.ENABLE_FLYWAY)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class PostsApiIT {

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

	private static RequestPostProcessor asUser() {
		return user(UUID.randomUUID().toString()).roles(Role.USER.name());
	}

	private static RequestPostProcessor asAdmin() {
		return user(UUID.randomUUID().toString()).roles(Role.ADMIN.name());
	}

}
