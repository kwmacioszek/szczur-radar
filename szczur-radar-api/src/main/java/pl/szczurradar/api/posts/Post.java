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

import pl.szczurradar.api.agents.assessor.contract.ItemType;
import pl.szczurradar.api.persistence.Sha256;
import pl.szczurradar.api.raw.RawPayloadRef;
import pl.szczurradar.api.sources.Source;
import pl.szczurradar.api.text.Nul;

/**
 * A post of a source, stored once per {@code (source, itemType, sourceItemId)} ({@code posts}, owned by
 * Flyway, {@code V3__create_pipeline_and_posts.sql}, {@code V4__posts_shared_item_type.sql}). Its type is
 * the shared model's {@link ItemType} — a source maps its own types onto it, so a new source changes
 * nothing here (PRD § Non-Functional Requirements). Its raw payload is kept apart
 * ({@link RawPayloadRef}). {@link #getStage()}, {@link #getAssessmentAttempts()} and
 * {@link #getTechnicalFailures()} are the post's progress; they change only through conditional updates of the pipeline, and every such update
 * also sets {@code updated_at = now()} ({@code @UpdateTimestamp} covers only entity saves).
 */
@Entity
@Table(name = "posts")
public class Post {

	/** A post whose third stored assessment fails is {@link PostStage#DEAD}. */
	public static final int MAX_ASSESSMENT_ATTEMPTS = 3;

	/**
	 * A post with this many technical failures in a row is on hold: it stays {@link PostStage#STORED} or
	 * {@link PostStage#FAILED}, but the retry run no longer takes it ({@code V10__posts_technical_failures.sql}).
	 */
	public static final int MAX_TECHNICAL_FAILURES = 3;

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, updatable = false)
	private Source source;

	@Column(name = "item_type", nullable = false, updatable = false)
	private ItemType itemType;

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

	/**
	 * Technical failures in a row since the last stored assessment — errors of our code and single
	 * outages of the LLM provider after their retries (no stored assessment, no attempt); at
	 * {@link #MAX_TECHNICAL_FAILURES} the post is on hold.
	 */
	@Column(name = "technical_failures", nullable = false)
	private int technicalFailures;

	/** The message of the latest of {@link #getTechnicalFailures()}; {@code null} when there is none. */
	@Column(name = "last_technical_error")
	private @Nullable String lastTechnicalError;

	@Column(name = "first_run_id", nullable = false, updatable = false)
	private UUID firstRunId;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	/** Set on entity saves; conditional updates set it themselves (no trigger). */
	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	/** For JPA. */
	protected Post() {
	}

	/**
	 * A new post, {@link PostStage#STORED}, first stored by the run {@code firstRunId}. Its texts lose
	 * U+0000 ({@link Nul}), which the database rejects.
	 */
	public Post(Source source, ItemType itemType, String sourceItemId, Instant publishedAt, @Nullable String title,
			@Nullable String body, @Nullable String author, @Nullable String slug, @Nullable String webUrl,
			List<String> tags, RawPayloadRef raw, UUID firstRunId) {
		this.source = Objects.requireNonNull(source, "source");
		this.itemType = Objects.requireNonNull(itemType, "itemType");
		this.sourceItemId = Objects.requireNonNull(sourceItemId, "sourceItemId");
		this.publishedAt = Objects.requireNonNull(publishedAt, "publishedAt");
		this.title = Nul.removed(title);
		this.body = Nul.removed(body);
		this.author = Nul.removed(author);
		this.slug = Nul.removed(slug);
		this.webUrl = Nul.removed(webUrl);
		this.tags = tags.stream().map(Nul::removed).toList();
		this.rawPayloadId = raw.id();
		this.contentHash = contentHash(this.title, this.body);
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

	public ItemType getItemType() {
		return itemType;
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

	public int getTechnicalFailures() {
		return technicalFailures;
	}

	public @Nullable String getLastTechnicalError() {
		return lastTechnicalError;
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
