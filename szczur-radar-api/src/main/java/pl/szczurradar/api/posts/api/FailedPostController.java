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
import pl.szczurradar.api.llm.Provider;
import pl.szczurradar.api.posts.Assessment;
import pl.szczurradar.api.posts.AssessmentRepository;
import pl.szczurradar.api.posts.Post;
import pl.szczurradar.api.posts.PostRepository;
import pl.szczurradar.api.posts.PostStage;

/**
 * The admin's list of posts whose assessment failed (1–2 attempts, retryable), that are dead (3) or that are
 * on hold ({@link Post#MAX_TECHNICAL_FAILURES} technical failures in a row), with their latest attempt and
 * their latest technical error — to diagnose them and retry the retryable ones
 * ({@code POST /api/admin/pipeline-runs} with {@code postIds}).
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
		Page<Post> found = posts.findFailedPage(page, size);
		Map<UUID, Assessment> latest = assessments.findLatest(found.map(Post::getId).getContent())
			.stream()
			.collect(Collectors.toMap(Assessment::getPostId, Function.identity()));
		return new FailedPostPage(found.map(post -> FailedPost.of(post, latest.get(post.getId()))).getContent(), page,
				size, found.getTotalElements());
	}

	/** A page of failed, dead and on-hold posts. */
	record FailedPostPage(List<FailedPost> items, int page, int size, long total) {
	}

	/**
	 * A post of the failed list, one of three cases:
	 * <ul>
	 * <li>{@code failed}: its latest stored attempt failed, attempts are left (retryable); it may also have
	 * technical failures, and with {@link Post#MAX_TECHNICAL_FAILURES} of them it is on hold too;</li>
	 * <li>{@code dead}: its {@link Post#MAX_ASSESSMENT_ATTEMPTS}-th attempt failed (not retryable, never on
	 * hold);</li>
	 * <li>{@code stored} on hold: never assessed since its {@link Post#MAX_TECHNICAL_FAILURES} technical
	 * failures in a row, so the retry run no longer takes it, an admin's run of chosen posts does; it has no
	 * stored attempt, so no {@code lastAttempt}.</li>
	 * </ul>
	 *
	 * @param stage {@code failed} (retryable), {@code dead} or {@code stored} (on hold)
	 * @param lastAttempt the latest assessment attempt (by time, then id)
	 * @param technicalFailures errors of our code or single provider outages in a row since the last stored
	 * assessment
	 * @param lastTechnicalError the message of the latest of {@code technicalFailures}
	 * @param onHold whether the retry run no longer takes the post ({@code technicalFailures} reached
	 * {@link Post#MAX_TECHNICAL_FAILURES} and the post is not dead)
	 */
	record FailedPost(UUID id, ItemType itemType, Instant publishedAt, @Nullable String title, @Nullable String body,
			@Nullable String webUrl, PostStage stage, int assessmentAttempts, @Nullable LastAttempt lastAttempt,
			int technicalFailures, @Nullable String lastTechnicalError, boolean onHold) {

		static FailedPost of(Post post, @Nullable Assessment latest) {
			return new FailedPost(post.getId(), post.getItemType(), post.getPublishedAt(), post.getTitle(), post.getBody(),
					post.getWebUrl(), post.getStage(), post.getAssessmentAttempts(),
					latest == null ? null : LastAttempt.of(latest), post.getTechnicalFailures(),
					post.getLastTechnicalError(),
					post.getTechnicalFailures() >= Post.MAX_TECHNICAL_FAILURES && post.getStage() != PostStage.DEAD);
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
