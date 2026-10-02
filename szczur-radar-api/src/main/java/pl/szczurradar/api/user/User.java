package pl.szczurradar.api.user;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * A row of {@code users}.
 *
 * @param passwordHash argon2 hash; {@code null} for an account without a password (activation
 *     pending, S-07), which cannot log in
 */
public record User(UUID id, String email, @Nullable String passwordHash, Role role, Instant createdAt) {
}
