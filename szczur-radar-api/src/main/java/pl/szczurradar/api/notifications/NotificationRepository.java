package pl.szczurradar.api.notifications;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import pl.szczurradar.api.user.Role;

/**
 * Notifications. Each write is one statement whose row count says what happened: creating them for the
 * admins skips the recipients who already have one ({@code uq_notifications_recipient_kind_run}), and
 * marking them read touches only the recipient's own. Kind and role values are bound from their enums,
 * never written into the SQL.
 */
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

	/**
	 * The {@code limit} newest notifications of {@code recipientId}, newest first; equal creation times are
	 * ordered by id (index {@code ix_notifications_recipient_created}).
	 */
	default List<Notification> findLatest(UUID recipientId, int limit) {
		return findByRecipientId(recipientId,
				PageRequest.of(0, limit, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
	}

	List<Notification> findByRecipientId(UUID recipientId, Pageable pageable);

	/** How many notifications of {@code recipientId} are unread. */
	long countByRecipientIdAndReadAtIsNull(UUID recipientId);

	/**
	 * A {@code kind} notification about {@code runId} for every admin who can log in, in one statement;
	 * an admin who already has it gets no second one.
	 *
	 * @return how many were created
	 */
	default int notifyAdmins(NotificationKind kind, UUID runId) {
		return insertForRole(kind.value(), runId, Role.ADMIN.value());
	}

	/** {@link #notifyAdmins}, with the values bound as stored; call that instead. */
	@Transactional
	@Modifying
	@Query(value = "INSERT INTO notifications (recipient_id, kind, run_id)"
			+ " SELECT id, :kind, :runId FROM users WHERE role = :role AND login_enabled"
			+ " ON CONFLICT (recipient_id, kind, run_id) DO NOTHING", nativeQuery = true)
	int insertForRole(String kind, UUID runId, String role);

	/**
	 * Marks the notification {@code id} of {@code recipientId} read; one already read keeps its first read
	 * time.
	 *
	 * @return 1 when the recipient has it, 0 for an unknown id or another recipient's
	 */
	@Transactional
	@Modifying
	@Query(value = "UPDATE notifications SET read_at = COALESCE(read_at, now())"
			+ " WHERE id = :id AND recipient_id = :recipientId", nativeQuery = true)
	int markRead(UUID id, UUID recipientId);

	/**
	 * Marks every unread notification of {@code recipientId} read.
	 *
	 * @return how many were unread
	 */
	@Transactional
	@Modifying
	@Query(value = "UPDATE notifications SET read_at = now() WHERE recipient_id = :recipientId AND read_at IS NULL",
			nativeQuery = true)
	int markAllRead(UUID recipientId);

}
