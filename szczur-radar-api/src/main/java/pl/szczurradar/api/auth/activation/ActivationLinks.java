package pl.szczurradar.api.auth.activation;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import pl.szczurradar.api.auth.Passwords;
import pl.szczurradar.api.persistence.Sha256;
import pl.szczurradar.api.user.Role;
import pl.szczurradar.api.user.User;
import pl.szczurradar.api.user.UserRepository;

/**
 * One-time activation links (S-07, FR-018/027/028): the operator commands issue them, the public
 * activation endpoint checks and uses them.
 *
 * <ul>
 *   <li>A link sets the account's password once. An account has at most one link: a new one replaces
 *       the previous, which stops working.
 *   <li>A token is 32 random bytes in base64url without padding (43 characters). The database holds
 *       only its hex SHA-256; the raw token leaves this class only in an {@link IssuedLink}.
 *   <li>Nothing here logs a token, a link or a password.
 * </ul>
 *
 * <p>Works in the server and in the operator commands, which have no Spring Session repository: a
 * reset and an activation delete the account's sessions with SQL.
 */
@Service
public class ActivationLinks {

	/** Random bytes in a token: 256 bits. */
	static final int TOKEN_BYTES = 32;

	private static final SecureRandom RANDOM = new SecureRandom();

	private static final Base64.Encoder BASE64URL = Base64.getUrlEncoder().withoutPadding();

	private final UserRepository users;

	private final ActivationTokenRepository tokens;

	private final PasswordEncoder passwordEncoder;

	private final JdbcClient jdbc;

	private final TransactionTemplate transaction;

	ActivationLinks(UserRepository users, ActivationTokenRepository tokens, PasswordEncoder passwordEncoder,
			JdbcClient jdbc, TransactionTemplate transaction) {
		this.users = users;
		this.tokens = tokens;
		this.passwordEncoder = passwordEncoder;
		this.jdbc = jdbc;
		this.transaction = transaction;
	}

	/**
	 * Creates an account without a password and its link, in one transaction.
	 *
	 * @param email an already normalized and validated address
	 * @return the link, or {@link IssueOutcome.Reason#EMAIL_TAKEN} with nothing written, also when another
	 *     command takes the e-mail between the check and the insert
	 */
	public IssueOutcome createUser(String email, Role role, Duration ttl) {
		requirePositive(ttl);
		try {
			return inTransaction(status -> {
				if (users.existsByEmail(email)) {
					return new IssueOutcome.Refused(IssueOutcome.Reason.EMAIL_TAKEN);
				}
				// Flushed now: the token row references the account.
				User user = users.saveAndFlush(new User(email, null, role));
				return issue(user, ttl);
			});
		}
		catch (DataIntegrityViolationException ex) {
			// The transaction has rolled back; a concurrent command's account is what took the e-mail.
			if (users.existsByEmail(email)) {
				return new IssueOutcome.Refused(IssueOutcome.Reason.EMAIL_TAKEN);
			}
			throw ex;
		}
	}

	/**
	 * Puts an existing account back to "waiting for activation", in one transaction: issues a new link
	 * (the previous one stops working), removes the password and deletes the account's sessions, so it
	 * is logged out everywhere and its old password no longer works.
	 *
	 * @param email an already normalized and validated address
	 * @return the link, or {@link IssueOutcome.Reason#NO_ACCOUNT} with nothing written
	 */
	public IssueOutcome resetPassword(String email, Duration ttl) {
		requirePositive(ttl);
		return inTransaction(status -> {
			Optional<User> found = users.findByEmail(email);
			if (found.isEmpty()) {
				return new IssueOutcome.Refused(IssueOutcome.Reason.NO_ACCOUNT);
			}
			User user = found.get();
			// The token row before the account row: the order activate() locks them in, so a reset and an
			// activation of the same account never deadlock. The account's update is flushed at commit.
			IssuedLink link = issue(user, ttl);
			user.clearPassword();
			deleteSessions(user.getId());
			return link;
		});
	}

	/** The e-mail of the account a valid link activates; changes nothing. */
	public Optional<String> lookup(String token) {
		return tokens.findEmailOfValid(Sha256.hex(token), Instant.now());
	}

	/**
	 * Sets the password through a link, in this order:
	 *
	 * <ol>
	 *   <li>the password policy, before any database work: a refused password leaves the link usable;
	 *   <li>the valid token, locked until the end of the transaction: of two uses of one link, the
	 *       second waits and then finds nothing;
	 *   <li>only then argon2 (16 MB), so invalid links never cost a hash;
	 *   <li>the account's password set, the token deleted and the account's sessions deleted, in the same
	 *       transaction.
	 * </ol>
	 */
	public ActivationResult activate(String token, String password) {
		try {
			Passwords.validatePolicy(password);
		}
		catch (IllegalArgumentException ex) {
			return ActivationResult.PASSWORD_POLICY;
		}
		String tokenHash = Sha256.hex(token);
		return inTransaction(status -> {
			Optional<ActivationToken> valid = tokens.findValidForUpdate(tokenHash, Instant.now());
			if (valid.isEmpty()) {
				return ActivationResult.LINK_INVALID;
			}
			User user = users.findById(valid.get().getUserId()).orElseThrow();
			user.setPassword(passwordEncoder.encode(password));
			tokens.delete(valid.get());
			// A login already past its password check when a reset committed stores its session after the
			// reset's DELETE: a new password ends every session of the account, so that one ends here.
			deleteSessions(user.getId());
			return ActivationResult.ACTIVATED;
		});
	}

	/** A new token for {@code user}, replacing its previous one; call inside a transaction. */
	private IssuedLink issue(User user, Duration ttl) {
		String token = newToken();
		// Whole seconds: what the database stores and the operator reads are the same instant.
		Instant expiresAt = Instant.now().plus(ttl).truncatedTo(ChronoUnit.SECONDS);
		tokens.upsert(user.getId(), Sha256.hex(token), expiresAt);
		return new IssuedLink(user.getEmail(), user.getRole(), token, expiresAt);
	}

	/**
	 * Deletes every session of the account: Spring Session stores the user's id as the principal name
	 * ({@code auth/AccountDetailsService}); the attributes go with it ({@code ON DELETE CASCADE}).
	 */
	private void deleteSessions(UUID userId) {
		jdbc.sql("DELETE FROM spring_session WHERE principal_name = :principal")
			.param("principal", userId.toString())
			.update();
	}

	private <T> T inTransaction(TransactionCallback<T> work) {
		return Objects.requireNonNull(transaction.execute(work), "transaction result");
	}

	private static String newToken() {
		byte[] bytes = new byte[TOKEN_BYTES];
		RANDOM.nextBytes(bytes);
		return BASE64URL.encodeToString(bytes);
	}

	private static void requirePositive(Duration ttl) {
		if (ttl.isNegative() || ttl.isZero()) {
			throw new IllegalArgumentException("a link's validity must be positive (got " + ttl + ")");
		}
	}

}
