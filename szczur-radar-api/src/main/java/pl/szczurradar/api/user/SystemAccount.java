package pl.szczurradar.api.user;

import java.util.UUID;

/**
 * Accounts that are not people, created by a migration ({@code V6__users_login_enabled_scheduler.sql}):
 * {@code login_enabled = false} and no password, so they can never log in. They exist so that work
 * the application starts by itself names a real account, e.g. {@code pipeline_runs.requested_by}.
 */
public enum SystemAccount {

	/** Requests the daily pipeline run (the cron's {@code POST /internal/pipeline-runs/daily}). */
	SCHEDULER(UUID.fromString("00000000-0000-4000-8000-000000000001"), "scheduler@system.invalid");

	private final UUID id;

	private final String email;

	SystemAccount(UUID id, String email) {
		this.id = id;
		this.email = email;
	}

	/** The account's {@code users.id}, as the migration inserted it. */
	public UUID id() {
		return id;
	}

	/** The account's (normalized) {@code users.email}; {@code .invalid} never receives mail. */
	public String email() {
		return email;
	}

}
