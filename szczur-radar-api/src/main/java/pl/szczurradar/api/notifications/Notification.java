package pl.szczurradar.api.notifications;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.jspecify.annotations.Nullable;

/**
 * A notification of one recipient ({@code notifications}, owned by Flyway,
 * {@code V9__create_notifications.sql}), with its own read time. Rows are created and marked read only
 * by the statements of {@link NotificationRepository}; JPA only reads them.
 */
@Entity
@Table(name = "notifications")
public class Notification {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(name = "recipient_id", nullable = false, updatable = false)
	private UUID recipientId;

	@Column(nullable = false, updatable = false)
	private NotificationKind kind;

	/** The run it is about; set for {@link NotificationKind#RUN_ATTENTION} ({@code ck_notifications_run}). */
	@Column(name = "run_id", updatable = false)
	private @Nullable UUID runId;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private Instant createdAt;

	/** When its recipient read it; {@code null} while unread. */
	@Column(name = "read_at", insertable = false, updatable = false)
	private @Nullable Instant readAt;

	/** For JPA. */
	protected Notification() {
	}

	public UUID getId() {
		return id;
	}

	public UUID getRecipientId() {
		return recipientId;
	}

	public NotificationKind getKind() {
		return kind;
	}

	public @Nullable UUID getRunId() {
		return runId;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public @Nullable Instant getReadAt() {
		return readAt;
	}

}
