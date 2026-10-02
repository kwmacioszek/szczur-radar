package pl.szczurradar.api.user;

import java.util.Arrays;
import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * An account's role. Stored and sent to the SPA as the lower-case {@link #value()} ({@code user},
 * {@code admin} — the {@code ck_users_role} constraint); the constant name is the Spring Security
 * role ({@code hasRole("ADMIN")} matches the authority {@code ROLE_ADMIN}).
 */
public enum Role {

	USER, ADMIN;

	/** {@code user} or {@code admin}: the database and API value. */
	@JsonValue
	public String value() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** The role stored as {@code value}. */
	public static Role fromValue(String value) {
		return Arrays.stream(values())
			.filter(role -> role.value().equals(value))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("unknown role: " + value));
	}

}
