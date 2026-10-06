package pl.szczurradar.api.raw;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import pl.szczurradar.api.sources.Source;

/**
 * A stored raw payload ({@code raw_payloads}, owned by Flyway, {@code V3__create_pipeline_and_posts.sql}):
 * the compressed bytes and the hash of the uncompressed ones. Used only by {@link PostgresRawPayloadStore};
 * everything else holds a {@link RawPayloadRef}.
 */
@Entity
@Table(name = "raw_payloads")
class RawPayload {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, updatable = false)
	private Source source;

	@Column(name = "content_encoding", nullable = false, updatable = false)
	private ContentEncoding contentEncoding;

	@Column(name = "media_type", nullable = false, updatable = false)
	private RawMediaType mediaType;

	@Column(nullable = false, updatable = false)
	private byte[] payload;

	@Column(nullable = false, updatable = false)
	private String sha256;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	/** For JPA. */
	protected RawPayload() {
	}

	RawPayload(Source source, ContentEncoding contentEncoding, RawMediaType mediaType, byte[] payload, String sha256) {
		this.source = source;
		this.contentEncoding = contentEncoding;
		this.mediaType = mediaType;
		this.payload = payload;
		this.sha256 = sha256;
	}

	UUID getId() {
		return id;
	}

	Source getSource() {
		return source;
	}

	ContentEncoding getContentEncoding() {
		return contentEncoding;
	}

	RawMediaType getMediaType() {
		return mediaType;
	}

	byte[] getPayload() {
		return payload;
	}

	String getSha256() {
		return sha256;
	}

}
