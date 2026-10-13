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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.JsonNodeFeature;
import tools.jackson.databind.json.JsonMapper;

import pl.szczurradar.api.agents.assessor.Provenance;
import pl.szczurradar.api.agents.assessor.contract.AssessmentOutputV2;
import pl.szczurradar.api.agents.assessor.contract.ItemType;
import pl.szczurradar.api.config.ApiError;
import pl.szczurradar.api.config.ErrorCode;
import pl.szczurradar.api.posts.Assessment;
import pl.szczurradar.api.posts.AssessmentRepository;
import pl.szczurradar.api.posts.Post;
import pl.szczurradar.api.posts.PostNotFoundException;
import pl.szczurradar.api.posts.PostRepository;
import pl.szczurradar.api.posts.PostStage;
import pl.szczurradar.api.raw.RawPayloadStore;

/**
 * The feed (US-01, FR-006, FR-007): assessed posts, newest first, each with its latest {@code ok}
 * assessment; and one assessed post (US-01, FR-009, its original's link FR-010) with its source's raw payload
 * (FR-009). Any logged-in user (deny-by-default in {@code SecurityConfig}).
 */
@RestController
class PostController {

	/**
	 * Reads a raw payload for display without changing a value: decimals as {@code BigDecimal} (never
	 * rounded through {@code double}, trailing zeros kept), integers of any size exact.
	 */
	private static final JsonMapper RAW_JSON = JsonMapper.builder()
		.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
		.disable(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES)
		.build();

	private final PostRepository posts;

	private final AssessmentRepository assessments;

	private final RawPayloadStore rawPayloads;

	PostController(PostRepository posts, AssessmentRepository assessments, RawPayloadStore rawPayloads) {
		this.posts = posts;
		this.assessments = assessments;
		this.rawPayloads = rawPayloads;
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

	/** One assessed post, as an item of the feed. */
	@GetMapping("/api/posts/{id}")
	PostItem detail(@PathVariable UUID id) {
		Post post = assessed(id);
		return PostItem.of(post, assessments.findLatestOk(List.of(id)).stream().findFirst().orElse(null));
	}

	/**
	 * The raw payload of one assessed post with every value as stored, indented for reading. Bytes with
	 * an explicit JSON type, so neither a message converter nor a charset default changes the content.
	 */
	@GetMapping("/api/posts/{id}/raw")
	ResponseEntity<byte[]> raw(@PathVariable UUID id) {
		byte[] stored = rawPayloads.load(assessed(id).getRawPayload());
		byte[] indented = RAW_JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(RAW_JSON.readTree(stored));
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(indented);
	}

	@ExceptionHandler(PostNotFoundException.class)
	ResponseEntity<ApiError> notFound() {
		return ApiError.response(ErrorCode.POST_NOT_FOUND);
	}

	/** The post {@code id} if it is assessed; an unknown id and any other stage are alike not found. */
	private Post assessed(UUID id) {
		return posts.findById(id)
			.filter(post -> post.getStage() == PostStage.ASSESSED)
			.orElseThrow(() -> new PostNotFoundException(id));
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
