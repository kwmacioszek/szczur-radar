package pl.szczurradar.api.auth;

import java.util.UUID;

import pl.szczurradar.api.user.Role;
import pl.szczurradar.api.user.User;

/** The logged-in account as the SPA sees it ({@code szczur-radar-web/src/api/auth.ts}). */
record UserResponse(UUID id, String email, Role role) {

	static UserResponse of(User user) {
		return new UserResponse(user.getId(), user.getEmail(), user.getRole());
	}

}
