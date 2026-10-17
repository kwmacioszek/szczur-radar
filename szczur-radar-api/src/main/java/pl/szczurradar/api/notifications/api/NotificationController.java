package pl.szczurradar.api.notifications.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pl.szczurradar.api.config.ApiError;
import pl.szczurradar.api.config.ErrorCode;
import pl.szczurradar.api.notifications.Notification;
import pl.szczurradar.api.notifications.NotificationKind;
import pl.szczurradar.api.notifications.NotificationRepository;
import pl.szczurradar.api.pipeline.run.InterruptionCause;
import pl.szczurradar.api.pipeline.run.PipelineRun;
import pl.szczurradar.api.pipeline.run.PipelineRunRepository;
import pl.szczurradar.api.pipeline.run.RunKind;
import pl.szczurradar.api.pipeline.run.RunStatus;

/**
 * The logged-in admin's notifications (admin only: {@code /api/admin/**}): the newest with the number
 * still unread, and marking them read. Every admin has their own notifications and read times, and never
 * sees or marks another admin's.
 */
@RestController
@RequestMapping("/api/admin/notifications")
class NotificationController {

	/** How many of the newest notifications the list shows. */
	static final int LATEST = 10;

	private final NotificationRepository notifications;

	private final PipelineRunRepository runs;

	NotificationController(NotificationRepository notifications, PipelineRunRepository runs) {
		this.notifications = notifications;
		this.runs = runs;
	}

	/** The {@value #LATEST} newest notifications, newest first, each with its run; and how many are unread. */
	@GetMapping
	NotificationList list(Authentication authentication) {
		UUID recipient = recipient(authentication);
		List<Notification> latest = notifications.findLatest(recipient, LATEST);
		Map<UUID, PipelineRun> runsById = runs
			.findAllById(latest.stream().map(Notification::getRunId).filter(Objects::nonNull).distinct().toList())
			.stream()
			.collect(Collectors.toMap(PipelineRun::getId, Function.identity()));
		return new NotificationList(notifications.countByRecipientIdAndReadAtIsNull(recipient),
				latest.stream()
					.map(notification -> NotificationItem.of(notification,
							notification.getRunId() == null ? null : runsById.get(notification.getRunId())))
					.toList());
	}

	/**
	 * 204 once the notification is read; reading it again keeps its first read time. 404 {@code not_found}
	 * for an unknown id and for another admin's.
	 */
	@PostMapping("/{id}/read")
	ResponseEntity<?> read(@PathVariable UUID id, Authentication authentication) {
		if (notifications.markRead(id, recipient(authentication)) == 0) {
			return ApiError.response(ErrorCode.NOT_FOUND);
		}
		return ResponseEntity.noContent().build();
	}

	/** 204 once every notification is read, also those beyond the {@value #LATEST} the list shows. */
	@PostMapping("/read")
	ResponseEntity<Void> readAll(Authentication authentication) {
		notifications.markAllRead(recipient(authentication));
		return ResponseEntity.noContent().build();
	}

	private static UUID recipient(Authentication authentication) {
		return UUID.fromString(authentication.getName());
	}

	/**
	 * The admin's newest notifications.
	 *
	 * @param unread how many of all their notifications are unread, not only of {@code items}
	 */
	record NotificationList(long unread, List<NotificationItem> items) {
	}

	/**
	 * One notification.
	 *
	 * @param readAt when the admin read it; {@code null} while unread
	 * @param run the run it is about ({@link NotificationKind#RUN_ATTENTION})
	 */
	record NotificationItem(UUID id, NotificationKind kind, Instant createdAt, @Nullable Instant readAt,
			@Nullable NotifiedRun run) {

		static NotificationItem of(Notification notification, @Nullable PipelineRun run) {
			return new NotificationItem(notification.getId(), notification.getKind(), notification.getCreatedAt(),
					notification.getReadAt(), run == null ? null : NotifiedRun.of(run));
		}

	}

	/**
	 * What the notification says about its run now: enough to tell why it needs attention; the run's page
	 * ({@code GET /api/admin/pipeline-runs/{id}}) has the rest.
	 *
	 * @param processingErrors posts with a technical error and no stored assessment (not failed attempts)
	 * @param error the reason of a failed or interrupted run, or the summary of technical errors
	 */
	record NotifiedRun(UUID id, RunKind kind, RunStatus status, @Nullable InterruptionCause interruptionCause,
			@Nullable Instant finishedAt, int assessedFailed, int dead, int processingErrors, @Nullable String error) {

		static NotifiedRun of(PipelineRun run) {
			return new NotifiedRun(run.getId(), run.getKind(), run.getStatus(), run.getInterruptionCause(),
					run.getFinishedAt(), run.getAssessedFailed(), run.getDead(), run.getProcessingErrors(),
					run.getError());
		}

	}

}
