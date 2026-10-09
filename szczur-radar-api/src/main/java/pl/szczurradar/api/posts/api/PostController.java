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
import pl.szczurradar.api.agents.assessor.contract.AssessmentOutputV2;
import pl.szczurradar.api.agents.assessor.contract.ItemType;
import pl.szczurradar.api.posts.Assessment;
import pl.szczurradar.api.posts.AssessmentRepository;
import pl.szczurradar.api.posts.Post;
import pl.szczurradar.api.posts.PostRepository;
import pl.szczurradar.api.posts.PostStage;

/**
 * The feed (US-01, FR-006, FR-007): assessed posts, newest first, each with its latest {@code ok}
 * assessment. Any logged-in user (deny-by-default in {@code SecurityConfig}).
 */
@RestController
class PostController {

	private final PostRepository posts;

	private final AssessmentRepository assessments;

	PostController(PostRepository posts, AssessmentRepository assessments) {
		this.posts = posts;
		this.assessments = assessments;
	}

	/** {@code page} from 0; {@code size} 1–100, default 50; ordered by {@code publishedAt} then id, newest first. */
	@GetMapping("/api/posts")
	PostPage list(@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
		Page<Post> found = posts.findPage(List.of(PostStage.ASSESSED), page, size);
		Map<UUID, Assessment> latestOk = assessments.findLatestOk(found.map(Post::getId).getContent())
			.stream()
			.collect(Collectors.toMap(Assessment::getPostId, Function.identity()));
		return new PostPage(found.map(post -> PostItem.of(post, latestOk.get(post.getId()))).getContent(), page, size,
				found.getTotalElements());
	}

	/** A page of the feed. */
	record PostPage(List<PostItem> items, int page, int size, long total) {
	}

	/**
	 * One post of the feed.
	 *
	 * @param itemType the shared model's type: {@code post}, {@code link} or {@code comment}
	 * @param assessment its latest {@code ok} assessment; {@code null} only if none was found (should not happen
	 *     for an assessed post)
	 */
	record PostItem(UUID id, ItemType itemType, Instant publishedAt, @Nullable String title, @Nullable String body,
			@Nullable String author, @Nullable String webUrl, @Nullable AssessmentView assessment) {

		static PostItem of(Post post, @Nullable Assessment assessment) {
			return new PostItem(post.getId(), post.getItemType(), post.getPublishedAt(), post.getTitle(), post.getBody(),
					post.getAuthor(), post.getWebUrl(), assessment == null ? null : AssessmentView.of(assessment));
		}

	}

	/**
	 * The assessment shown in the feed: the contract V2 output and where it came from.
	 *
	 * @param resultSource {@code model}, or {@code prefilter} for a post without content (§11)
	 */
	record AssessmentView(AssessmentOutputV2 output, Provenance.Source resultSource, Instant assessedAt) {

		static AssessmentView of(Assessment assessment) {
			Provenance provenance = assessment.getProvenance();
			return new AssessmentView(assessment.getOutput(), provenance.source(), provenance.assessedAt());
		}

	}

}
