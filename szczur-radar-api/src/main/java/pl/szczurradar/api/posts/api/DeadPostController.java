package pl.szczurradar.api.posts.api;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import pl.szczurradar.api.config.ApiError;
import pl.szczurradar.api.config.ErrorCode;
import pl.szczurradar.api.config.PostIdLimits;
import pl.szczurradar.api.pipeline.state.PostTransitions;

/**
 * The admin restores dead posts of the failed list ({@link FailedPostController}) for assessment (S-33, FR-034):
 * each goes back to {@code stored} with no attempt ({@link PostTransitions#restore}), and the next run (the
 * nightly retry, or the retry before an admin's range) assesses it again. It starts no run, and it is allowed
 * while a run is queued or running: no run ever holds a dead post.
 */
@RestController
class DeadPostController {

	/** The most posts one request may name: as many as a posts run ({@link PostIdLimits#MAX_POSTS}). */
	static final int MAX_POSTS = PostIdLimits.MAX_POSTS;

	private static final Logger log = LoggerFactory.getLogger(DeadPostController.class);

	private final PostTransitions transitions;

	DeadPostController(PostTransitions transitions) {
		this.transitions = transitions;
	}

	/**
	 * 200 with how many of the named posts were dead and are restored now; the others (restored already,
	 * another stage, unknown) stay as they are. 400 {@code invalid_request} unless {@code postIds} has
	 * 1–{@value #MAX_POSTS} ids and no {@code null}.
	 */
	@PostMapping(path = "/api/admin/posts/restore", consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<?> restore(@RequestBody RestoreRequest request, Authentication authentication) {
		if (!request.valid()) {
			return ApiError.response(ErrorCode.INVALID_REQUEST);
		}
		List<UUID> postIds = request.postIds();
		int restored = transitions.restore(postIds);
		log.info("Admin {} restored {} of {} dead post(s) for assessment: {}", authentication.getName(), restored,
				postIds.size(), postIds);
		return ResponseEntity.ok(new RestoreResult(restored));
	}

	/** The posts to restore: 1–{@value #MAX_POSTS} ids, repeats allowed. */
	record RestoreRequest(@Nullable List<UUID> postIds) {

		boolean valid() {
			return postIds != null && !postIds.isEmpty() && postIds.size() <= MAX_POSTS && !postIds.contains(null);
		}

	}

	/** @param restored how many of the named posts were dead and are restored now */
	record RestoreResult(int restored) {
	}

}
