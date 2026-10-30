package pl.szczurradar.api.posts;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
import pl.szczurradar.api.agents.assessor.contract.ItemType;
import pl.szczurradar.api.agents.assessor.validation.FailureClass;
import pl.szczurradar.api.agents.assessor.validation.Violation;
import pl.szczurradar.api.cli.MigrateCommand;
import pl.szczurradar.api.llm.Provider;
import pl.szczurradar.api.llm.ServiceTier;
import pl.szczurradar.api.pipeline.run.PipelineRun;
import pl.szczurradar.api.pipeline.run.PipelineRunRepository;
import pl.szczurradar.api.pipeline.state.PostTransitions;
import pl.szczurradar.api.raw.RawPayloadStore;
import pl.szczurradar.api.sources.Source;
import pl.szczurradar.api.user.Role;
import pl.szczurradar.api.user.User;
import pl.szczurradar.api.user.UserRepository;

/**
 * The queries of {@link PostRepository} and {@link AssessmentRepository} on Postgres: dedup keys,
 * leftovers (without posts on hold), newest-first pages and the latest attempt with its tie rule; and the
 * conditional writes of {@link PostTransitions}: taking a post, putting a run's posts or orphans back, the
 * technical failures counted on a post. Each test rolls back.
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
	PostTransitions transitions;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	EntityManager entityManager;

	private UUID adminId;

	private UUID runId;

	@BeforeEach
	void run() {
		adminId = users.save(new User("posts-" + UUID.randomUUID() + "@example.com", null, Role.ADMIN)).getId();
		runId = runs.save(PipelineRun.retry(adminId, null)).getId();
	}

	@Test
	void findKeysReturnsTheStoredKeysOfTheSourceOnly() {
		post(ItemType.POST, "80001", "2026-10-01T08:00:00Z");
		post(ItemType.LINK, "80002", "2026-10-01T08:00:00Z");

		assertThat(posts.findKeys(Source.WYKOP, List.of("80001", "80002", "80003"))).containsExactlyInAnyOrder(
				new PostKey(ItemType.POST, "80001"), new PostKey(ItemType.LINK, "80002"));
	}

	@Test
	void leftoversAreStoredAndRetryableFailedPostsOldestFirst() {
		UUID stored = post(ItemType.POST, "80101", "2026-10-01T10:00:00Z");
		UUID failedOnce = stage(post(ItemType.POST, "80102", "2026-10-01T09:00:00Z"), PostStage.FAILED, 1);
		UUID failedTwice = stage(post(ItemType.POST, "80103", "2026-10-01T11:00:00Z"), PostStage.FAILED, 2);
		stage(post(ItemType.POST, "80104", "2026-10-01T08:00:00Z"), PostStage.DEAD, 3);
		stage(post(ItemType.POST, "80105", "2026-10-01T08:00:00Z"), PostStage.ASSESSED, 0);
		assessing(post(ItemType.POST, "80106", "2026-10-01T08:00:00Z"));

		assertThat(posts.findLeftoverIds()).containsExactly(failedOnce, stored, failedTwice);
	}

	@Test
	void aPostOnHoldIsNoLeftoverAndOneTechnicalFailureShortOfItIs() {
		UUID storedOnHold = technicalFailures(post(ItemType.POST, "80111", "2026-10-01T08:00:00Z"),
				Post.MAX_TECHNICAL_FAILURES);
		UUID failedOnHold = technicalFailures(stage(post(ItemType.POST, "80112", "2026-10-01T09:00:00Z"),
				PostStage.FAILED, 1), Post.MAX_TECHNICAL_FAILURES);

		// Posts on hold alone: no leftover, so they queue no retry run.
		assertThat(posts.findLeftoverIds()).isEmpty();
		assertThat(posts.existsLeftover()).isFalse();

		UUID almost = technicalFailures(post(ItemType.POST, "80113", "2026-10-01T10:00:00Z"),
				Post.MAX_TECHNICAL_FAILURES - 1);

		assertThat(posts.findLeftoverIds()).containsExactly(almost).doesNotContain(storedOnHold, failedOnHold);
		assertThat(posts.existsLeftover()).isTrue();
	}

	@Test
	void aReleaseWithATechnicalFailureCountsItOnlyForTheRunHoldingThePost() {
		UUID otherRun = runs.save(PipelineRun.retry(adminId, null)).getId();
		UUID held = post(ItemType.POST, "80121", "2026-10-01T08:00:00Z");
		assessing(held);

		assertThat(transitions.releaseFailed(held, otherRun, "IllegalStateException: not mine")).isEmpty();
		assertThat(technical(held)).containsEntry("stage", PostStage.ASSESSING.value())
			.containsEntry("technical_failures", 0)
			.containsEntry("last_technical_error", null);

		assertThat(transitions.releaseFailed(held, runId, "IllegalStateException: a bug")).contains(1);
		assertThat(technical(held)).containsEntry("stage", PostStage.STORED.value())
			.containsEntry("assessment_attempts", 0)
			.containsEntry("technical_failures", 1)
			.containsEntry("last_technical_error", "IllegalStateException: a bug");
		// Back already: a second release changes nothing.
		assertThat(transitions.release(held, runId)).isFalse();
	}

	@Test
	void aLongTechnicalErrorIsCutAndLosesNul() {
		UUID held = post(ItemType.POST, "80122", "2026-10-01T08:00:00Z");
		assessing(held);

		// PostgreSQL rejects U+0000 in TEXT; an SDK's message may carry it.
		assertThat(transitions.releaseFailed(held, runId, "bad \u0000 byte " + "x".repeat(2_000))).contains(1);

		assertThat((String) technical(held).get("last_technical_error")).hasSize(501)
			.startsWith("bad \uFFFD byte x")
			.endsWith("x…");
	}

	@Test
	void aStoredResultResetsTheTechnicalFailures() {
		UUID held = technicalFailures(post(ItemType.POST, "80131", "2026-10-01T08:00:00Z"), 2);
		assessing(held);

		assertThat(transitions.record(held, runId, 0, new Assessment(held, runId, ContractFixtures.validInput(),
				failed(Instant.parse("2026-10-06T10:00:00Z"))))).isTrue();

		assertThat(technical(held)).containsEntry("stage", PostStage.FAILED.value())
			.containsEntry("assessment_attempts", 1)
			.containsEntry("technical_failures", 0)
			.containsEntry("last_technical_error", null);
	}

	@Test
	void aTechnicalFailureIsCountedOnAPostPutBackOnlyWhileItsRunIsRunning() {
		running(runId);
		UUID stored = post(ItemType.POST, "80141", "2026-10-01T08:00:00Z");
		UUID failed = stage(post(ItemType.POST, "80142", "2026-10-01T08:00:00Z"), PostStage.FAILED, 2);
		UUID assessed = stage(post(ItemType.POST, "80143", "2026-10-01T08:00:00Z"), PostStage.ASSESSED, 0);
		UUID dead = stage(post(ItemType.POST, "80144", "2026-10-01T08:00:00Z"), PostStage.DEAD, 3);
		UUID held = post(ItemType.POST, "80145", "2026-10-01T08:00:00Z");
		assessing(held);

		assertThat(transitions.countTechnicalFailure(stored, runId, "provider unavailable")).isTrue();
		assertThat(transitions.countTechnicalFailure(failed, runId, "provider unavailable")).isTrue();
		assertThat(transitions.countTechnicalFailure(assessed, runId, "provider unavailable")).isFalse();
		assertThat(transitions.countTechnicalFailure(dead, runId, "provider unavailable")).isFalse();
		assertThat(transitions.countTechnicalFailure(held, runId, "provider unavailable")).isFalse();

		assertThat(technical(stored)).containsEntry("stage", PostStage.STORED.value())
			.containsEntry("technical_failures", 1)
			.containsEntry("last_technical_error", "provider unavailable");
		assertThat(technical(failed)).containsEntry("stage", PostStage.FAILED.value())
			.containsEntry("assessment_attempts", 2)
			.containsEntry("technical_failures", 1);
		assertThat(List.of(assessed, dead, held)).allSatisfy(post -> assertThat(technical(post))
			.containsEntry("technical_failures", 0)
			.containsEntry("last_technical_error", null));

		// A run interrupted in the meantime (e.g. a deploy) counts nothing more.
		jdbc.update("UPDATE pipeline_runs SET status = 'interrupted', finished_at = now() WHERE id = ?", runId);

		assertThat(transitions.countTechnicalFailure(stored, runId, "provider unavailable again")).isFalse();
		assertThat(technical(stored)).containsEntry("technical_failures", 1)
			.containsEntry("last_technical_error", "provider unavailable");
	}

	@Test
	void aClaimTakesOnlyAStoredOrFailedPostAndOnlyForARunningRun() {
		UUID stored = post(ItemType.POST, "80151", "2026-10-01T08:00:00Z");
		UUID failed = stage(post(ItemType.POST, "80152", "2026-10-01T08:00:00Z"), PostStage.FAILED, 2);
		UUID assessed = stage(post(ItemType.POST, "80153", "2026-10-01T08:00:00Z"), PostStage.ASSESSED, 0);
		UUID dead = stage(post(ItemType.POST, "80154", "2026-10-01T08:00:00Z"), PostStage.DEAD, 3);
		UUID heldByAnother = post(ItemType.POST, "80155", "2026-10-01T08:00:00Z");
		UUID otherRun = runs.save(PipelineRun.retry(adminId, null)).getId();
		assessing(heldByAnother, otherRun);

		// The run is still queued: it takes nothing.
		assertThat(transitions.claim(stored, runId)).isEmpty();
		assertThat(technical(stored)).containsEntry("stage", PostStage.STORED.value());

		running(runId);

		// The failed attempts so far come back with the post.
		assertThat(transitions.claim(stored, runId)).contains(0);
		assertThat(transitions.claim(failed, runId)).contains(2);
		assertThat(transitions.claim(assessed, runId)).isEmpty();
		assertThat(transitions.claim(dead, runId)).isEmpty();
		assertThat(transitions.claim(heldByAnother, runId)).isEmpty();
		// Taken once: a second claim finds it assessing.
		assertThat(transitions.claim(stored, runId)).isEmpty();

		assertThat(List.of(stored, failed)).allSatisfy(post -> assertThat(owner(post))
			.containsEntry("stage", PostStage.ASSESSING.value())
			.containsEntry("assessing_run_id", runId));
		assertThat(owner(failed)).containsEntry("assessment_attempts", 2);
		assertThat(owner(assessed)).containsEntry("stage", PostStage.ASSESSED.value())
			.containsEntry("assessing_run_id", null);
		assertThat(owner(dead)).containsEntry("stage", PostStage.DEAD.value()).containsEntry("assessing_run_id", null);
		assertThat(owner(heldByAnother)).containsEntry("assessing_run_id", otherRun);
	}

	@Test
	void releaseAllPutsBackOnlyThePostsOfThatRun() {
		UUID otherRun = runs.save(PipelineRun.retry(adminId, null)).getId();
		UUID neverFailed = assessing(post(ItemType.POST, "80161", "2026-10-01T08:00:00Z"), runId);
		UUID failedOnce = assessing(stage(post(ItemType.POST, "80162", "2026-10-01T08:00:00Z"), PostStage.FAILED, 1),
				runId);
		UUID ofAnotherRun = assessing(post(ItemType.POST, "80163", "2026-10-01T08:00:00Z"), otherRun);

		assertThat(transitions.releaseAll(runId)).isEqualTo(2);

		// Back where they were before the attempt, without one.
		assertThat(owner(neverFailed)).containsEntry("stage", PostStage.STORED.value())
			.containsEntry("assessment_attempts", 0)
			.containsEntry("assessing_run_id", null);
		assertThat(owner(failedOnce)).containsEntry("stage", PostStage.FAILED.value())
			.containsEntry("assessment_attempts", 1)
			.containsEntry("assessing_run_id", null);
		assertThat(owner(ofAnotherRun)).containsEntry("stage", PostStage.ASSESSING.value())
			.containsEntry("assessing_run_id", otherRun);
		// Nothing left: a second call puts nothing back.
		assertThat(transitions.releaseAll(runId)).isZero();
	}

	@Test
	void releaseOrphansPutsBackAPostClaimedJustAsItsRunWasInterruptedAndLeavesALiveRunsPosts() {
		UUID claimed = stage(post(ItemType.POST, "80171", "2026-10-01T08:00:00Z"), PostStage.FAILED, 1);
		running(runId);
		assertThat(transitions.claim(claimed, runId)).contains(1);
		// The stopping instance interrupted the run and put its posts back just before this claim
		// committed, then stopped before the worker could put the post back itself.
		jdbc.update("UPDATE pipeline_runs SET status = 'interrupted', finished_at = now() WHERE id = ?", runId);
		// Posts of live runs: a running one and a queued one.
		UUID liveRun = runs.save(PipelineRun.retry(adminId, null)).getId();
		running(liveRun);
		UUID queuedRun = runs.save(PipelineRun.retry(adminId, null)).getId();
		UUID ofRunning = assessing(post(ItemType.POST, "80172", "2026-10-01T08:00:00Z"), liveRun);
		UUID ofQueued = assessing(post(ItemType.POST, "80173", "2026-10-01T08:00:00Z"), queuedRun);

		transitions.releaseOrphans();

		assertThat(owner(claimed)).containsEntry("stage", PostStage.FAILED.value())
			.containsEntry("assessment_attempts", 1)
			.containsEntry("assessing_run_id", null);
		assertThat(owner(ofRunning)).containsEntry("stage", PostStage.ASSESSING.value())
			.containsEntry("assessing_run_id", liveRun);
		assertThat(owner(ofQueued)).containsEntry("stage", PostStage.ASSESSING.value())
			.containsEntry("assessing_run_id", queuedRun);
	}

	@Test
	void aPageIsNewestFirstWithTiesByIdAndCountsTheTotal() {
		UUID older = stage(post(ItemType.POST, "80201", "2026-10-01T08:00:00Z"), PostStage.ASSESSED, 0);
		UUID tieA = stage(post(ItemType.POST, "80202", "2026-10-01T09:00:00Z"), PostStage.ASSESSED, 0);
		UUID tieB = stage(post(ItemType.LINK, "80203", "2026-10-01T09:00:00Z"), PostStage.ASSESSED, 0);
		post(ItemType.POST, "80204", "2026-10-01T12:00:00Z");
		List<UUID> ties = greater(tieA, tieB) == tieA ? List.of(tieA, tieB) : List.of(tieB, tieA);

		Page<Post> first = posts.findPage(List.of(PostStage.ASSESSED), 0, 2);
		Page<Post> second = posts.findPage(List.of(PostStage.ASSESSED), 1, 2);

		assertThat(first.getTotalElements()).isEqualTo(3);
		assertThat(first.getContent()).extracting(Post::getId).containsExactlyElementsOf(ties);
		assertThat(second.getContent()).extracting(Post::getId).containsExactly(older);
	}

	@Test
	void theLatestAttemptBreaksEqualTimesByTheGreatestId() {
		UUID post = post(ItemType.POST, "80301", "2026-10-01T08:00:00Z");
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
		UUID first = post(ItemType.POST, "80401", "2026-10-01T08:00:00Z");
		UUID second = post(ItemType.POST, "80402", "2026-10-01T08:00:00Z");
		UUID withoutOk = post(ItemType.POST, "80403", "2026-10-01T08:00:00Z");
		Instant at = Instant.parse("2026-10-06T10:00:00Z");
		assess(first, ok(at));
		assess(second, ok(at.plusSeconds(1)));
		assess(withoutOk, failed(at));

		assertThat(assessments.findLatestOk(List.of(first, second, withoutOk))).extracting(Assessment::getPostId)
			.containsExactlyInAnyOrder(first, second);
	}

	@Test
	void textsWithNulAreStoredWithoutIt() {
		// PostgreSQL rejects U+0000 in TEXT and jsonb; the entities keep it out (text/Nul).
		Post saved = posts.save(new Post(Source.WYKOP, ItemType.POST, "80501", "gi\u0000elda",
				Instant.parse("2026-10-01T08:00:00Z"), "tytuł\u0000", "treść\u0000wpisu", "autor\u0000", null, null,
				List.of("gi\u0000elda"),
				rawPayloads.store(Source.WYKOP, "{\"id\":80501}".getBytes(StandardCharsets.UTF_8)), runId));
		AssessmentResult failed = new AssessmentResult(AssessmentResult.Status.FAILED, null, FailureClass.SCHEMA_VIOLATION,
				List.of(new Violation(FailureClass.SCHEMA_VIOLATION, "schema:type", "$", "bad \u0000 value")),
				new Provenance(Provenance.Source.MODEL, Provider.GEMINI, "gemini-3.1-flash-lite", null, ServiceTier.DEFAULT,
						false, null, null, "p1", "V2", Instant.parse("2026-10-06T10:00:00Z"), null, null, null, 1_000, 1),
				"{\"x\":\"\u0000\"}");
		Assessment assessment = assess(saved.getId(), failed);
		entityManager.clear();

		Post loaded = posts.findById(saved.getId()).orElseThrow();
		assertThat(loaded.getTitle()).isEqualTo("tytuł");
		assertThat(loaded.getBody()).isEqualTo("treśćwpisu");
		assertThat(loaded.getTags()).containsExactly("gielda");
		assertThat(loaded.getChannel()).isEqualTo("gielda");
		Assessment loadedAssessment = assessments.findById(assessment.getId()).orElseThrow();
		assertThat(loadedAssessment.getRawText()).isEqualTo("{\"x\":\"\uFFFD\"}");
		assertThat(loadedAssessment.getViolations()).singleElement()
			.satisfies(violation -> assertThat(violation.message()).isEqualTo("bad \uFFFD value"));
	}

	/**
	 * The greater of two ids in Postgres' order (unsigned bytes = their hex text), not
	 * {@link UUID#compareTo}, which compares signed longs.
	 */
	private static UUID greater(UUID a, UUID b) {
		return a.toString().compareTo(b.toString()) > 0 ? a : b;
	}

	private UUID post(ItemType itemType, String sourceItemId, String publishedAt) {
		Post post = posts.save(new Post(Source.WYKOP, itemType, sourceItemId, "gielda", Instant.parse(publishedAt),
				null, "treść", null, null, null, List.of("gielda"),
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

	/** Gives a post {@code count} technical failures in a row, as the pipeline's conditional updates would. */
	private UUID technicalFailures(UUID post, int count) {
		entityManager.flush();
		jdbc.update("UPDATE posts SET technical_failures = ?, last_technical_error = 'IllegalStateException: x'"
				+ " WHERE id = ?", count, post);
		entityManager.clear();
		return post;
	}

	/** A post's stage, attempts and technical failures, read from the table. */
	private Map<String, Object> technical(UUID post) {
		return jdbc.queryForMap("SELECT stage, assessment_attempts, technical_failures, last_technical_error"
				+ " FROM posts WHERE id = ?", post);
	}

	/** A post's stage, attempts and owner, read from the table. */
	private Map<String, Object> owner(UUID post) {
		return jdbc.queryForMap("SELECT stage, assessment_attempts, assessing_run_id FROM posts WHERE id = ?", post);
	}

	/** Makes {@code run} the running run (at most one runs: any other is interrupted, rolled back with the test). */
	private void running(UUID run) {
		entityManager.flush();
		jdbc.update("UPDATE pipeline_runs SET status = 'interrupted', finished_at = now() WHERE status = 'running'");
		jdbc.update("UPDATE pipeline_runs SET status = 'running', started_at = now() WHERE id = ?", run);
	}

	private void assessing(UUID post) {
		assessing(post, runId);
	}

	/** Puts a post in assessment by {@code run}, keeping its attempts, as {@link PostTransitions#claim} would. */
	private UUID assessing(UUID post, UUID run) {
		entityManager.flush();
		jdbc.update("UPDATE posts SET stage = 'assessing', assessing_run_id = ? WHERE id = ?", run, post);
		entityManager.clear();
		return post;
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
