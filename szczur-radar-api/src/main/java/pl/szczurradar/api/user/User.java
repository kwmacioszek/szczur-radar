package pl.szczurradar.api.user;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.jspecify.annotations.Nullable;

/**
 * An account ({@code users}; the table itself is owned by Flyway, {@code V1__create_users.sql}).
 * Emails are stored normalized ({@link Emails}).
 */
@Entity
@Table(name = "users")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private String email;

	/** argon2 hash; {@code null} for an account without a password (activation pending, S-07). */
	@Column(name = "password_hash")
	private @Nullable String passwordHash;

	@Column(nullable = false)
	private Role role;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	/** For JPA. */
	protected User() {
	}

	public User(String email, @Nullable String passwordHash, Role role) {
		this.email = email;
		this.passwordHash = passwordHash;
		this.role = role;
	}

	public UUID getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	/** {@code null} for an account without a password, which cannot log in. */
	public @Nullable String getPasswordHash() {
		return passwordHash;
	}

	public Role getRole() {
		return role;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
