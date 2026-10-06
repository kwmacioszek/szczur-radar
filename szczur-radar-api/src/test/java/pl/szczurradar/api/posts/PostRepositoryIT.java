package pl.szczurradar.api.posts;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import pl.szczurradar.api.TestcontainersConfiguration;
import pl.szczurradar.api.agents.assessor.AssessmentResult;
import pl.szczurradar.api.agents.assessor.ContractFixtures;
import pl.szczurradar.api.agents.assessor.Provenance;
import pl.szczurradar.api.agents.assessor.validation.FailureClass;
import pl.szczurradar.api.agents.assessor.validation.Violation;
import pl.szczurradar.api.cli.MigrateCommand;
import pl.szczurradar.api.config.LlmProviderProperties.Provider;
import pl.szczurradar.api.config.LlmProviderProperties.ServiceTier;
import pl.szczurradar.api.pipeline.PipelineRun;
import pl.szczurradar.api.pipeline.PipelineRunRepository;
import pl.szczurradar.api.raw.RawPayloadStore;
import pl.szczurradar.api.sources.Source;
import pl.szczurradar.api.user.Role;
import pl.szczurradar.api.user.User;
import pl.szczurradar.api.user.UserRepository;

/**
 * The queries of {@link PostRepository} and {@link AssessmentRepository} on Postgres: dedup keys,
 * leftovers, newest-first pages and the latest attempt with its tie rule. Each test rolls back.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = MigrateCommand.ENABLE_FLYWAY)
@Import(TestcontainersConfiguration.class)
@Transactional
class PostRepositoryIT {

	@Autowired
	PostRepository posts;

	@Autowired
	AssessmentRepository assessments;

	@Autowired
	PipelineRunRepository runs;

	@Autowired
	UserRepository users;

	@Autowired
	RawPayloadStore rawPayloads;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	EntityManager entityManager;

	private UUID runId;

	@BeforeEach
	void run() {
		User admin = users.save(new User("posts-" + UUID.randomUUID() + "@example.com", null, Role.ADMIN));
		runId = runs.save(PipelineRun.retry(admin.getId())).getId();
	}

	@Test
	void findKeysReturnsTheStoredKeysOfTheSourceOnly() {
		post(PostResource.ENTRY, "80001", "2026-10-01T08:00:00Z");
		post(PostResource.LINK, "80002", "2026-10-01T08:00:00Z");

		assertThat(posts.findKeys(Source.WYKOP, List.of("80001", "80002", "80003"))).containsExactlyInAnyOrder(
				new PostKey(PostResource.ENTRY, "80001"), new PostKey(PostResource.LINK, "80002"));
	}

	@Test
	void leftoversAreStoredAndRetryableFailedPostsOldestFirst() {
		UUID stored = post(PostResource.ENTRY, "80101", "2026-10-01T10:00:00Z");
		UUID failedOnce = stage(post(PostResource.ENTRY, "80102", "2026-10-01T09:00:00Z"), PostStage.FAILED, 1);
		UUID failedTwice = stage(post(PostResource.ENTRY, "80103", "2026-10-01T11:00:00Z"), PostStage.FAILED, 2);
		stage(post(PostResource.ENTRY, "80104", "2026-10-01T08:00:00Z"), PostStage.DEAD, 3);
		stage(post(PostResource.ENTRY, "80105", "2026-10-01T08:00:00Z"), PostStage.ASSESSED, 0);
		assessing(post(PostResource.ENTRY, "80106", "2026-10-01T08:00:00Z"));

		assertThat(posts.findLeftoverIds()).containsExactly(failedOnce, stored, failedTwice);
	}

	@Test
	void aPageIsNewestFirstWithTiesByIdAndCountsTheTotal() {
		UUID older = stage(post(PostResource.ENTRY, "80201", "2026-10-01T08:00:00Z"), PostStage.ASSESSED, 0);
		UUID tieA = stage(post(PostResource.ENTRY, "80202", "2026-10-01T09:00:00Z"), PostStage.ASSESSED, 0);
		UUID tieB = stage(post(PostResource.LINK, "80203", "2026-10-01T09:00:00Z"), PostStage.ASSESSED, 0);
		post(PostResource.ENTRY, "80204", "2026-10-01T12:00:00Z");
		List<UUID> ties = greater(tieA, tieB) == tieA ? List.of(tieA, tieB) : List.of(tieB, tieA);

		Page<Post> first = posts.findPage(List.of(PostStage.ASSESSED), 0, 2);
		Page<Post> second = posts.findPage(List.of(PostStage.ASSESSED), 1, 2);

		assertThat(first.getTotalElements()).isEqualTo(3);
		assertThat(first.getContent()).extracting(Post::getId).containsExactlyElementsOf(ties);
		assertThat(second.getContent()).extracting(Post::getId).containsExactly(older);
	}

	@Test
	void theLatestAttemptBreaksEqualTimesByTheGreatestId() {
		UUID post = post(PostResource.ENTRY, "80301", "2026-10-01T08:00:00Z");
		Instant at = Instant.parse("2026-10-06T10:00:00Z");
		Assessment okEarlier = assess(post, ok(at.minusSeconds(60)));
		Assessment okA = assess(post, ok(at));
		Assessment okB = assess(post, ok(at));
		Assessment failedLater = assess(post, failed(at.plusSeconds(60)));
		UUID tieWinner = greater(okA.getId(), okB.getId());

		assertThat(assessments.findLatestOk(List.of(post))).extracting(Assessment::getId).containsExactly(tieWinner);
		assertThat(assessments.findLatest(List.of(post))).extracting(Assessment::getId)
			.containsExactly(failedLater.getId());
		assertThat(okEarlier.getId()).isNotIn(tieWinner, failedLater.getId());
		assertThat(assessments.findLatest(List.of())).isEmpty();
	}

	@Test
	void theLatestAttemptIsChosenPerPost() {
		UUID first = post(PostResource.ENTRY, "80401", "2026-10-01T08:00:00Z");
		UUID second = post(PostResource.ENTRY, "80402", "2026-10-01T08:00:00Z");
		UUID withoutOk = post(PostResource.ENTRY, "80403", "2026-10-01T08:00:00Z");
		Instant at = Instant.parse("2026-10-06T10:00:00Z");
		assess(first, ok(at));
		assess(second, ok(at.plusSeconds(1)));
		assess(withoutOk, failed(at));

		assertThat(assessments.findLatestOk(List.of(first, second, withoutOk))).extracting(Assessment::getPostId)
			.containsExactlyInAnyOrder(first, second);
	}

	/**
	 * The greater of two ids in Postgres' order (unsigned bytes = their hex text), not
	 * {@link UUID#compareTo}, which compares signed longs.
	 */
	private static UUID greater(UUID a, UUID b) {
		return a.toString().compareTo(b.toString()) > 0 ? a : b;
	}

	private UUID post(PostResource resource, String sourceItemId, String publishedAt) {
		Post post = posts.save(new Post(Source.WYKOP, resource, sourceItemId, Instant.parse(publishedAt), null, "treść",
				null, null, null, List.of("gielda"),
				rawPayloads.store(Source.WYKOP, ("{\"id\":" + sourceItemId + "}").getBytes(StandardCharsets.UTF_8)),
				runId));
		return post.getId();
	}

	/** Puts a post in {@code stage} as the pipeline's conditional updates would. */
	private UUID stage(UUID post, PostStage stage, int attempts) {
		entityManager.flush();
		jdbc.update("UPDATE posts SET stage = ?, assessment_attempts = ? WHERE id = ?", stage.value(), attempts, post);
		entityManager.clear();
		return post;
	}

	private void assessing(UUID post) {
		entityManager.flush();
		jdbc.update("UPDATE posts SET stage = 'assessing', assessing_run_id = ? WHERE id = ?", runId, post);
		entityManager.clear();
	}

	private Assessment assess(UUID post, AssessmentResult result) {
		Assessment saved = assessments.save(new Assessment(post, runId, ContractFixtures.validInput(), result));
		entityManager.flush();
		return saved;
	}

	private static AssessmentResult ok(Instant at) {
		return new AssessmentResult(AssessmentResult.Status.OK, ContractFixtures.validOutput(), null, List.of(),
				Provenance.prefilter("V2", at), null);
	}

	private static AssessmentResult failed(Instant at) {
		return new AssessmentResult(AssessmentResult.Status.FAILED, null, FailureClass.SCHEMA_VIOLATION,
				List.of(new Violation(FailureClass.SCHEMA_VIOLATION, "schema:type", "$", "bad")),
				new Provenance(Provenance.Source.MODEL, Provider.GEMINI, "gemini-3.1-flash-lite", null, ServiceTier.DEFAULT,
						false, null, null, "p1", "V2", at, null, null, null, 1_000, 1),
				"{}");
	}

}
