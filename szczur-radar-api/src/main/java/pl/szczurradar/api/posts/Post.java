package pl.szczurradar.api.posts;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

import pl.szczurradar.api.persistence.Sha256;
import pl.szczurradar.api.raw.RawPayloadRef;
import pl.szczurradar.api.sources.Source;

/**
 * A post of a source, stored once per {@code (source, resource, sourceItemId)} ({@code posts}, owned by
 * Flyway, {@code V3__create_pipeline_and_posts.sql}). Its raw payload is kept apart
 * ({@link RawPayloadRef}). {@link #getStage()} and {@link #getAssessmentAttempts()} are the post's
 * progress; they change only through conditional updates of the pipeline.
 */
@Entity
@Table(name = "posts")
public class Post {

	/** A post whose third stored assessment fails is {@link PostStage#DEAD}. */
	public static final int MAX_ASSESSMENT_ATTEMPTS = 3;

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, updatable = false)
	private Source source;

	@Column(nullable = false, updatable = false)
	private PostResource resource;

	@Column(name = "source_item_id", nullable = false, updatable = false)
	private String sourceItemId;

	@Column(name = "published_at", nullable = false)
	private Instant publishedAt;

	private @Nullable String title;

	private @Nullable String body;

	private @Nullable String author;

	private @Nullable String slug;

	@Column(name = "web_url")
	private @Nullable String webUrl;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(nullable = false)
	private List<String> tags;

	@Column(name = "raw_payload_id", nullable = false, updatable = false)
	private UUID rawPayloadId;

	@Column(name = "content_hash", nullable = false)
	private String contentHash;

	@Column(nullable = false)
	private PostStage stage;

	@Column(name = "assessment_attempts", nullable = false)
	private int assessmentAttempts;

	/** The run assessing the post; set exactly when {@link #getStage()} is {@link PostStage#ASSESSING}. */
	@Column(name = "assessing_run_id")
	private @Nullable UUID assessingRunId;

	@Column(name = "first_run_id", nullable = false, updatable = false)
	private UUID firstRunId;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	/** For JPA. */
	protected Post() {
	}

	/** A new post, {@link PostStage#STORED}, first stored by the run {@code firstRunId}. */
	public Post(Source source, PostResource resource, String sourceItemId, Instant publishedAt, @Nullable String title,
			@Nullable String body, @Nullable String author, @Nullable String slug, @Nullable String webUrl,
			List<String> tags, RawPayloadRef raw, UUID firstRunId) {
		this.source = Objects.requireNonNull(source, "source");
		this.resource = Objects.requireNonNull(resource, "resource");
		this.sourceItemId = Objects.requireNonNull(sourceItemId, "sourceItemId");
		this.publishedAt = Objects.requireNonNull(publishedAt, "publishedAt");
		this.title = title;
		this.body = body;
		this.author = author;
		this.slug = slug;
		this.webUrl = webUrl;
		this.tags = List.copyOf(tags);
		this.rawPayloadId = raw.id();
		this.contentHash = contentHash(title, body);
		this.stage = PostStage.STORED;
		this.firstRunId = Objects.requireNonNull(firstRunId, "firstRunId");
	}

	/** Hex SHA-256 of the post's text: title and body, a missing one as empty, separated by U+0000. */
	static String contentHash(@Nullable String title, @Nullable String body) {
		return Sha256.hex(Objects.requireNonNullElse(title, "") + '\u0000' + Objects.requireNonNullElse(body, ""));
	}

	public UUID getId() {
		return id;
	}

	public Source getSource() {
		return source;
	}

	public PostResource getResource() {
		return resource;
	}

	public String getSourceItemId() {
		return sourceItemId;
	}

	public Instant getPublishedAt() {
		return publishedAt;
	}

	public @Nullable String getTitle() {
		return title;
	}

	public @Nullable String getBody() {
		return body;
	}

	public @Nullable String getAuthor() {
		return author;
	}

	public @Nullable String getSlug() {
		return slug;
	}

	public @Nullable String getWebUrl() {
		return webUrl;
	}

	public List<String> getTags() {
		return tags;
	}

	public RawPayloadRef getRawPayload() {
		return new RawPayloadRef(rawPayloadId);
	}

	public String getContentHash() {
		return contentHash;
	}

	public PostStage getStage() {
		return stage;
	}

	public int getAssessmentAttempts() {
		return assessmentAttempts;
	}

	public @Nullable UUID getAssessingRunId() {
		return assessingRunId;
	}

	public UUID getFirstRunId() {
		return firstRunId;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
