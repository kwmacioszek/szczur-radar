package pl.szczurradar.api.posts.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pl.szczurradar.api.agents.assessor.Provenance;
import pl.szczurradar.api.agents.assessor.contract.ItemType;
import pl.szczurradar.api.agents.assessor.validation.FailureClass;
import pl.szczurradar.api.agents.assessor.validation.Violation;
import pl.szczurradar.api.config.LlmProviderProperties.Provider;
import pl.szczurradar.api.posts.Assessment;
import pl.szczurradar.api.posts.AssessmentRepository;
import pl.szczurradar.api.posts.Post;
import pl.szczurradar.api.posts.PostRepository;
import pl.szczurradar.api.posts.PostStage;

/**
 * The admin's list of posts whose assessment failed (1–2 attempts, retryable) or that are dead (3), with
 * their latest attempt — to diagnose them and retry the retryable ones ({@code POST /api/admin/pipeline-runs}
 * with {@code postIds}).
 */
@RestController
class FailedPostController {

	private final PostRepository posts;

	private final AssessmentRepository assessments;

	FailedPostController(PostRepository posts, AssessmentRepository assessments) {
		this.posts = posts;
		this.assessments = assessments;
	}

	/** {@code page} from 0; {@code size} 1–100, default 50; newest first. */
	@GetMapping("/api/admin/posts/failed")
	FailedPostPage list(@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
		Page<Post> found = posts.findPage(List.of(PostStage.FAILED, PostStage.DEAD), page, size);
		Map<UUID, Assessment> latest = assessments.findLatest(found.map(Post::getId).getContent())
			.stream()
			.collect(Collectors.toMap(Assessment::getPostId, Function.identity()));
		return new FailedPostPage(found.map(post -> FailedPost.of(post, latest.get(post.getId()))).getContent(), page,
				size, found.getTotalElements());
	}

	/** A page of failed and dead posts. */
	record FailedPostPage(List<FailedPost> items, int page, int size, long total) {
	}

	/**
	 * A failed or dead post.
	 *
	 * @param stage {@code failed} (retryable) or {@code dead}
	 * @param lastAttempt the latest assessment attempt (by time, then id)
	 */
	record FailedPost(UUID id, ItemType itemType, Instant publishedAt, @Nullable String title, @Nullable String body,
			@Nullable String webUrl, PostStage stage, int assessmentAttempts, @Nullable LastAttempt lastAttempt) {

		static FailedPost of(Post post, @Nullable Assessment latest) {
			return new FailedPost(post.getId(), post.getItemType(), post.getPublishedAt(), post.getTitle(), post.getBody(),
					post.getWebUrl(), post.getStage(), post.getAssessmentAttempts(),
					latest == null ? null : LastAttempt.of(latest));
		}

	}

	/** Why the latest attempt failed and whom it was for. */
	record LastAttempt(@Nullable FailureClass failureClass, List<Violation> violations, Instant assessedAt,
			@Nullable Provider provider, @Nullable String model) {

		static LastAttempt of(Assessment assessment) {
			Provenance provenance = assessment.getProvenance();
			return new LastAttempt(assessment.getFailureClass(), assessment.getViolations(), provenance.assessedAt(),
					provenance.provider(), provenance.model());
		}

	}

}
