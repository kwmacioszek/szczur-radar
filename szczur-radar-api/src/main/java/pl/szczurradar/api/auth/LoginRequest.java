package pl.szczurradar.api.auth;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import pl.szczurradar.api.user.Emails;

/**
 * Body of {@code POST /api/auth/login}. Unknown fields are rejected (closed model).
 *
 * @param password capped well above the policy maximum so huge inputs never reach argon2
 */
record LoginRequest(@NotNull @Size(max = Emails.MAX_EMAIL_LENGTH) String email,
		@NotNull @Size(max = 1024) String password) {
}
