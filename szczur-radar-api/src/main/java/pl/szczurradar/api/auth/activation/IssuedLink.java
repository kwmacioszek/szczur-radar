package pl.szczurradar.api.auth.activation;

import java.time.Instant;

import pl.szczurradar.api.user.Role;

/**
 * A newly issued link: the account it activates, the raw token for building the link
 * ({@link ActivationSettings#link}) and when it expires. The token exists only here and in the link:
 * the database holds its hash. Never log the token; {@link #toString()} hides it.
 */
public record IssuedLink(String email, Role role, String token, Instant expiresAt) implements IssueOutcome {

	@Override
	public String toString() {
		return "IssuedLink[email=" + email + ", role=" + role.value() + ", token=<hidden>, expiresAt=" + expiresAt
				+ "]";
	}

}
