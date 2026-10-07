package pl.szczurradar.api.posts;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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
	 * after an interruption) and failed posts with attempts left.
	 */
	default List<UUID> findLeftoverIds() {
		return findIdsInStageOrFailedBelow(PostStage.STORED, PostStage.FAILED, Post.MAX_ASSESSMENT_ATTEMPTS);
	}

	@Query("select p.id from Post p where p.stage = :stage or (p.stage = :failed and p.assessmentAttempts < :maxAttempts)"
			+ " order by p.publishedAt, p.id")
	List<UUID> findIdsInStageOrFailedBelow(PostStage stage, PostStage failed, int maxAttempts);

	/** A page of posts in {@code stages}, newest first. */
	default Page<Post> findPage(Collection<PostStage> stages, int page, int size) {
		return findByStageIn(stages, PageRequest.of(page, size, NEWEST_FIRST));
	}

	Page<Post> findByStageIn(Collection<PostStage> stages, Pageable page);

}
