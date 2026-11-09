package pl.szczurradar.api.posts;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import pl.szczurradar.api.agents.assessor.AssessmentResult;

/**
 * Assessment attempts. The latest attempt of a post is the one with the latest {@code assessed_at},
 * then the greatest {@code id}: equal times never leave the choice to chance (index
 * {@code ix_assessments_post_latest}).
 */
public interface AssessmentRepository extends JpaRepository<Assessment, UUID> {

	/** The latest {@code ok} attempt of each of {@code postIds} that has one. */
	default List<Assessment> findLatestOk(Collection<UUID> postIds) {
		return postIds.isEmpty() ? List.of() : findLatestWithStatusOf(postIds, AssessmentResult.Status.OK.value());
	}

	/** The latest attempt, ok or failed, of each of {@code postIds} that has one. */
	default List<Assessment> findLatest(Collection<UUID> postIds) {
		return postIds.isEmpty() ? List.of() : findLatestOf(postIds);
	}

	@Query(value = "SELECT DISTINCT ON (a.post_id) a.* FROM assessments a WHERE a.post_id IN (:postIds)"
			+ " AND a.status = :status ORDER BY a.post_id, a.assessed_at DESC, a.id DESC", nativeQuery = true)
	List<Assessment> findLatestWithStatusOf(Collection<UUID> postIds, String status);

	@Query(value = "SELECT DISTINCT ON (a.post_id) a.* FROM assessments a WHERE a.post_id IN (:postIds)"
			+ " ORDER BY a.post_id, a.assessed_at DESC, a.id DESC", nativeQuery = true)
	List<Assessment> findLatestOf(Collection<UUID> postIds);

	/**
	 * How many different posts of each source {@code runId} assessed (a post with two attempts in the run
	 * counts once; one it put back without an attempt not at all) — the leftovers a retry run took, for the
	 * night's summary.
	 */
	@Query("select new pl.szczurradar.api.posts.SourceCount(p.source, count(distinct a.postId))"
			+ " from Assessment a join Post p on p.id = a.postId where a.runId = :runId group by p.source")
	List<SourceCount> countDistinctPostsBySource(UUID runId);

	/**
	 * The assessments of {@code runIds} summed per source of their posts ({@link SourceAssessmentTotals}): the
	 * {@code ok} and {@code failed} ones and their tokens, a {@code NULL} count as 0. A source without an
	 * assessment in them is missing.
	 */
	default List<SourceAssessmentTotals> sumBySource(Collection<UUID> runIds) {
		return runIds.isEmpty() ? List.of()
				: sumWithStatusesBySource(runIds, AssessmentResult.Status.OK, AssessmentResult.Status.FAILED);
	}

	@Query("select new pl.szczurradar.api.posts.SourceAssessmentTotals(p.source,"
			+ " sum(case when a.status = :ok then 1L else 0L end), sum(case when a.status = :failed then 1L else 0L end),"
			+ " coalesce(sum(a.inputTokens), 0L), coalesce(sum(a.cachedTokens), 0L), coalesce(sum(a.outputTokens), 0L))"
			+ " from Assessment a join Post p on p.id = a.postId where a.runId in :runIds group by p.source")
	List<SourceAssessmentTotals> sumWithStatusesBySource(Collection<UUID> runIds, AssessmentResult.Status ok,
			AssessmentResult.Status failed);

}
