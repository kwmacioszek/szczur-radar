package pl.szczurradar.api.posts;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Assessment attempts. The latest attempt of a post is the one with the latest {@code assessed_at},
 * then the greatest {@code id}: equal times never leave the choice to chance (index
 * {@code ix_assessments_post_latest}).
 */
public interface AssessmentRepository extends JpaRepository<Assessment, UUID> {

	/** The latest {@code ok} attempt of each of {@code postIds} that has one. */
	default List<Assessment> findLatestOk(Collection<UUID> postIds) {
		return postIds.isEmpty() ? List.of() : findLatestOkOf(postIds);
	}

	/** The latest attempt, ok or failed, of each of {@code postIds} that has one. */
	default List<Assessment> findLatest(Collection<UUID> postIds) {
		return postIds.isEmpty() ? List.of() : findLatestOf(postIds);
	}

	@Query(value = "SELECT DISTINCT ON (a.post_id) a.* FROM assessments a WHERE a.post_id IN (:postIds)"
			+ " AND a.status = 'ok' ORDER BY a.post_id, a.assessed_at DESC, a.id DESC", nativeQuery = true)
	List<Assessment> findLatestOkOf(Collection<UUID> postIds);

	@Query(value = "SELECT DISTINCT ON (a.post_id) a.* FROM assessments a WHERE a.post_id IN (:postIds)"
			+ " ORDER BY a.post_id, a.assessed_at DESC, a.id DESC", nativeQuery = true)
	List<Assessment> findLatestOf(Collection<UUID> postIds);

}
