package pl.szczurradar.api.posts;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import pl.szczurradar.api.agents.assessor.AssessmentResult;
import pl.szczurradar.api.sources.Source;

/**
 * Posts. Lists are newest first, by {@code published_at} and then {@code id} (a stable order for
 * equal times); index {@code ix_posts_stage_published}.
 */
public interface PostRepository extends JpaRepository<Post, UUID> {

	/** Newest first, ties broken by id. */
	Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("id"));

	/** The keys among {@code sourceItemIds} already stored for {@code source} (any item type). */
	@Query("select new pl.szczurradar.api.posts.PostKey(p.itemType, p.sourceItemId) from Post p"
			+ " where p.source = :source and p.sourceItemId in :sourceItemIds")
	List<PostKey> findKeys(Source source, Collection<String> sourceItemIds);

	/**
	 * The leftovers of earlier runs, oldest first: stored posts never assessed (including those put back
	 * after an interruption) and failed posts with attempts left — except posts on hold
	 * ({@link Post#MAX_TECHNICAL_FAILURES} technical failures in a row), which only an admin's run of
	 * chosen posts takes.
	 */
	default List<UUID> findLeftoverIds() {
		return findIdsInStageOrFailedBelow(PostStage.STORED, PostStage.FAILED, Post.MAX_ASSESSMENT_ATTEMPTS,
				Post.MAX_TECHNICAL_FAILURES);
	}

	@Query("select p.id from Post p where (p.stage = :stage or (p.stage = :failed and p.assessmentAttempts < :maxAttempts))"
			+ " and p.technicalFailures < :maxTechnicalFailures order by p.publishedAt, p.id")
	List<UUID> findIdsInStageOrFailedBelow(PostStage stage, PostStage failed, int maxAttempts, int maxTechnicalFailures);

	/**
	 * Whether there is any leftover ({@link #findLeftoverIds()} is not empty); a post on hold alone queues
	 * no retry run.
	 */
	default boolean existsLeftover() {
		return existsInStageOrFailedBelow(PostStage.STORED, PostStage.FAILED, Post.MAX_ASSESSMENT_ATTEMPTS,
				Post.MAX_TECHNICAL_FAILURES);
	}

	@Query("select count(p) > 0 from Post p where (p.stage = :stage or (p.stage = :failed and p.assessmentAttempts < :maxAttempts))"
			+ " and p.technicalFailures < :maxTechnicalFailures")
	boolean existsInStageOrFailedBelow(PostStage stage, PostStage failed, int maxAttempts, int maxTechnicalFailures);

	/** Those of {@code ids} in {@code stage}. */
	@Query("select p.id from Post p where p.id in :ids and p.stage = :stage order by p.id")
	List<UUID> findIdsInStage(Collection<UUID> ids, PostStage stage);

	/** The ids of the posts {@code runId} stored first that are in {@code stage}, oldest first. */
	@Query("select p.id from Post p where p.firstRunId = :runId and p.stage = :stage order by p.publishedAt, p.id")
	List<UUID> findIdsFirstStoredBy(UUID runId, PostStage stage);

	/** A page of posts in {@code stages}, newest first. */
	default Page<Post> findPage(Collection<PostStage> stages, int page, int size) {
		return findByStageIn(stages, PageRequest.of(page, size, NEWEST_FIRST));
	}

	Page<Post> findByStageIn(Collection<PostStage> stages, Pageable page);

	/**
	 * A page of the admin's failed list, newest first: failed and dead posts, and stored posts on hold
	 * ({@link Post#MAX_TECHNICAL_FAILURES} technical failures in a row, never assessed since). A failed post on
	 * hold is in by its stage.
	 */
	default Page<Post> findFailedPage(int page, int size) {
		return findInStagesOrOnHold(List.of(PostStage.FAILED, PostStage.DEAD), PostStage.STORED,
				Post.MAX_TECHNICAL_FAILURES, PageRequest.of(page, size, NEWEST_FIRST));
	}

	@Query("select p from Post p where p.stage in :stages"
			+ " or (p.stage = :stored and p.technicalFailures >= :maxTechnicalFailures)")
	Page<Post> findInStagesOrOnHold(Collection<PostStage> stages, PostStage stored, int maxTechnicalFailures,
			Pageable page);

	/**
	 * A page of the feed (FR-011a), newest first, with {@code total} counted after the filter. Without a
	 * condition it is every assessed post ({@link #findPage}). With one, an assessed post is in only if
	 * its latest {@code ok} assessment (the one the feed shows: latest {@code assessed_at}, then greatest
	 * id, as {@link AssessmentRepository#findLatestOk}) has a target that meets every given condition
	 * itself — the same element of {@code targets[]}, so a quote of one target and the direction of
	 * another never make a match. A post without targets (the prefilter's output) never matches.
	 */
	default Page<Post> findAssessedPage(PostFilter filter, int page, int size) {
		if (filter.isEmpty()) {
			return findPage(List.of(PostStage.ASSESSED), page, size);
		}
		return findMatching(PostStage.ASSESSED.value(), AssessmentResult.Status.OK.value(), filter.quotePattern(),
				filter.kind() == null ? null : filter.kind().value(),
				filter.direction() == null ? null : filter.direction().value(), PageRequest.of(page, size));
	}

	/**
	 * Posts in {@code stage} joined with their latest assessment in {@code status}, and the target condition
	 * of {@link #findAssessedPage}: a {@code null} parameter is no condition (cast, so Postgres knows its
	 * type even when it is {@code null}).
	 */
	String MATCHING = " FROM posts p CROSS JOIN LATERAL (SELECT a.output FROM assessments a"
			+ " WHERE a.post_id = p.id AND a.status = :status ORDER BY a.assessed_at DESC, a.id DESC LIMIT 1) latest"
			+ " WHERE p.stage = :stage AND EXISTS (SELECT 1 FROM jsonb_array_elements(latest.output -> 'targets') t"
			+ " WHERE (CAST(:pattern AS text) IS NULL"
			+ " OR t -> 'anchor' ->> 'quote' ILIKE CAST(:pattern AS text) ESCAPE '" + PostFilter.LIKE_ESCAPE + "')"
			+ " AND (CAST(:kind AS text) IS NULL OR t ->> 'kind' = CAST(:kind AS text))"
			+ " AND (CAST(:direction AS text) IS NULL OR t -> 'stance' ->> 'direction' = CAST(:direction AS text)))";

	/** Unsorted {@code page} only: the order is the query's own (native SQL knows no entity field names). */
	@Query(value = "SELECT p.*" + MATCHING + " ORDER BY p.published_at DESC, p.id DESC",
			countQuery = "SELECT count(*)" + MATCHING, nativeQuery = true)
	Page<Post> findMatching(String stage, String status, @Nullable String pattern, @Nullable String kind,
			@Nullable String direction, Pageable page);

}
