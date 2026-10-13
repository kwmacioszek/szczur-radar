package pl.szczurradar.api.auth.activation;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Activation tokens, found by the hash of the token (index of {@code uq_activation_tokens_token_hash});
 * a token is valid while {@code expiresAt} is after {@code now}. Hashes passed in come from
 * {@code persistence/Sha256}.
 */
public interface ActivationTokenRepository extends JpaRepository<ActivationToken, UUID> {

	/**
	 * The valid token with this hash, locked ({@code FOR UPDATE}) until the caller's transaction ends:
	 * a second use of the same link waits, then finds no row.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Transactional(propagation = Propagation.MANDATORY)
	@Query("select t from ActivationToken t where t.tokenHash = :tokenHash and t.expiresAt > :now")
	Optional<ActivationToken> findValidForUpdate(String tokenHash, Instant now);

	/**
	 * The e-mail of the account whose valid token has this hash; reads only. A system account
	 * ({@code login_enabled = false}) has none.
	 */
	@Query("select u.email from ActivationToken t join User u on u.id = t.userId"
			+ " where t.tokenHash = :tokenHash and t.expiresAt > :now and u.loginEnabled = true")
	Optional<String> findEmailOfValid(String tokenHash, Instant now);

	/**
	 * Stores the account's token, replacing the previous one in a single statement (the previous link
	 * stops working). Leaves the persistence context as it is, so entities loaded before stay managed.
	 */
	@Transactional
	@Modifying
	@Query(value = "INSERT INTO activation_tokens (user_id, token_hash, expires_at)"
			+ " VALUES (:userId, :tokenHash, :expiresAt)"
			+ " ON CONFLICT (user_id) DO UPDATE SET token_hash = EXCLUDED.token_hash,"
			+ " expires_at = EXCLUDED.expires_at, created_at = now()", nativeQuery = true)
	void upsert(UUID userId, String tokenHash, Instant expiresAt);

}
