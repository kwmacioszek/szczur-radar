package pl.szczurradar.api.auth.activation;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The one-time activation link of an account ({@code activation_tokens}, owned by Flyway,
 * {@code V6__create_activation_tokens.sql}): at most one per account, holding only the hex SHA-256 of
 * the token. Rows are written by {@link ActivationTokenRepository#upsert} (a new link replaces the
 * previous one) and deleted when the link is used; JPA only reads and deletes them.
 */
@Entity
@Table(name = "activation_tokens")
public class ActivationToken {

	@Id
	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(name = "token_hash", nullable = false, updatable = false)
	private String tokenHash;

	@Column(name = "expires_at", nullable = false, updatable = false)
	private Instant expiresAt;

	/** When the current link was issued. */
	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private Instant createdAt;

	/** For JPA. */
	protected ActivationToken() {
	}

	/** The account (also the row's id). */
	public UUID getUserId() {
		return userId;
	}

	public String getTokenHash() {
		return tokenHash;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
