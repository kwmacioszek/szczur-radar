package pl.szczurradar.api.notifications;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * What a notification is about; {@link #value()} is the stored ({@code ck_notifications_kind}) and API
 * value.
 */
public enum NotificationKind {

	/** A pipeline run needs attention ({@code PipelineRun.needsAttention}); the notification names the run. */
	RUN_ATTENTION("run_attention");

	private final String value;

	NotificationKind(String value) {
		this.value = value;
	}

	@JsonValue
	public String value() {
		return value;
	}

}
