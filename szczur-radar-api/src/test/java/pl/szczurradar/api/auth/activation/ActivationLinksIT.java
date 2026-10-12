package pl.szczurradar.api.auth.activation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;

import pl.szczurradar.api.TestcontainersConfiguration;
import pl.szczurradar.api.auth.Passwords;
import pl.szczurradar.api.cli.MigrateCommand;
import pl.szczurradar.api.persistence.Sha256;
import pl.szczurradar.api.user.Role;
import pl.szczurradar.api.user.User;
import pl.szczurradar.api.user.UserRepository;

/**
 * {@link ActivationLinks} against a real database: issuing, checking and using links, a newer link
 * replacing an older one, a reset that logs only that account out, and two uses of one link at once.
 *
 * <p>Sessions are created through the application's Spring Session repository, so the reset's SQL is
 * checked against what Spring Session really stores. Each test uses its own e-mails.
 */
@SpringBootTest(properties = MigrateCommand.ENABLE_FLYWAY)
@Import(TestcontainersConfiguration.class)
class ActivationLinksIT {

	private static final String PASSWORD = "correct horse battery";

	private static final Duration TTL = Duration.ofHours(12);

	private static final AtomicInteger SEQUENCE = new AtomicInteger();

	@Autowired
	ActivationLinks links;

	@Autowired
	ActivationTokenRepository tokens;

	@Autowired
	UserRepository users;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	DataSource dataSource;

	@Autowired
	FindByIndexNameSessionRepository<? extends Session> sessions;

	// --- issuing --------------------------------------------------------------------------------

	@Test
	void createUserMakesAnAccountWithoutAPasswordAndItsLink() {
		String email = newEmail();
		Instant before = Instant.now();

		IssuedLink link = issued(links.createUser(email, Role.USER, TTL));

		User user = users.findByEmail(email).orElseThrow();
		assertThat(user.getRole()).isEqualTo(Role.USER);
		assertThat(user.getPasswordHash()).isNull();
		assertThat(link.email()).isEqualTo(email);
		assertThat(link.role()).isEqualTo(Role.USER);
		assertThat(link.token()).matches("[A-Za-z0-9_-]{43}");
		assertThat(link.expiresAt()).isBetween(before.plus(TTL).minusSeconds(1), Instant.now().plus(TTL));
		// Only the hash is stored, with the expiry the operator is shown.
		ActivationToken stored = tokens.findById(user.getId()).orElseThrow();
		assertThat(stored.getTokenHash()).isEqualTo(Sha256.hex(link.token()));
		assertThat(stored.getExpiresAt()).isEqualTo(link.expiresAt());
		assertThat(link.toString()).doesNotContain(link.token());
	}

	@Test
	void createUserCanMakeAnAdmin() {
		String email = newEmail();

		IssuedLink link = issued(links.createUser(email, Role.ADMIN, TTL));

		assertThat(link.role()).isEqualTo(Role.ADMIN);
		assertThat(users.findByEmail(email).orElseThrow().getRole()).isEqualTo(Role.ADMIN);
	}

	@Test
	void createUserRefusesATakenEmailAndWritesNothing() {
		String email = newEmail();
		UUID id = users.save(new User(email, passwordEncoder.encode(PASSWORD), Role.USER)).getId();

		IssueOutcome outcome = links.createUser(email, Role.ADMIN, TTL);

		assertThat(outcome).isEqualTo(new IssueOutcome.Refused(IssueOutcome.Reason.EMAIL_TAKEN));
		User user = users.findById(id).orElseThrow();
		assertThat(user.getRole()).isEqualTo(Role.USER);
		assertThat(passwordEncoder.matches(PASSWORD, user.getPasswordHash())).isTrue();
		assertThat(tokens.findById(id)).isEmpty();
	}

	@Test
	void anEmailTakenWhileCreatingIsRefused() throws Exception {
		String email = newEmail();
		ExecutorService executor = Executors.newSingleThreadExecutor();
		Future<IssueOutcome> outcome;
		try (Connection other = dataSource.getConnection()) {
			// Another command has inserted the e-mail but not committed: this check passes, and the insert
			// waits on the unique index until the other account is there.
			other.setAutoCommit(false);
			try (PreparedStatement insert = other.prepareStatement("INSERT INTO users (email) VALUES (?)")) {
				insert.setString(1, email);
				insert.executeUpdate();
			}
			outcome = executor.submit(() -> links.createUser(email, Role.ADMIN, TTL));
			awaitLockWaiters("users", 1);
			other.commit();
		}
		finally {
			executor.shutdown();
		}

		assertThat(outcome.get(30, TimeUnit.SECONDS))
			.isEqualTo(new IssueOutcome.Refused(IssueOutcome.Reason.EMAIL_TAKEN));
		User user = users.findByEmail(email).orElseThrow();
		assertThat(user.getRole()).isEqualTo(Role.USER);
		assertThat(tokens.findById(user.getId())).isEmpty();
	}

	@Test
	void resetOfAnUnknownEmailIsRefused() {
		String email = newEmail();

		assertThat(links.resetPassword(email, TTL))
			.isEqualTo(new IssueOutcome.Refused(IssueOutcome.Reason.NO_ACCOUNT));
		assertThat(users.findByEmail(email)).isEmpty();
	}

	@Test
	void aLinkMustBeValidForSomeTime() {
		String email = newEmail();

		assertThatIllegalArgumentException().isThrownBy(() -> links.createUser(email, Role.USER, Duration.ZERO));
		assertThatIllegalArgumentException().isThrownBy(() -> links.resetPassword(email, Duration.ofMinutes(-1)));
		assertThat(users.findByEmail(email)).isEmpty();
	}

	// --- lookup ---------------------------------------------------------------------------------

	@Test
	void lookupFindsTheEmailOfAValidLinkOnlyAndChangesNothing() {
		String email = newEmail();
		IssuedLink link = issued(links.createUser(email, Role.USER, TTL));
		UUID id = users.findByEmail(email).orElseThrow().getId();

		assertThat(links.lookup(link.token())).contains(email);
		assertThat(links.lookup(link.token())).contains(email);
		ActivationToken stored = tokens.findById(id).orElseThrow();
		assertThat(stored.getTokenHash()).isEqualTo(Sha256.hex(link.token()));
		assertThat(stored.getExpiresAt()).isEqualTo(link.expiresAt());

		assertThat(links.lookup("unknown-" + UUID.randomUUID())).isEmpty();
		assertThat(links.lookup("")).isEmpty();

		expire(link.token());
		assertThat(links.lookup(link.token())).isEmpty();
	}

	// --- activation -----------------------------------------------------------------------------

	@Test
	void activationSetsAnArgon2HashAndUsesUpTheLink() {
		String email = newEmail();
		IssuedLink link = issued(links.createUser(email, Role.USER, TTL));

		assertThat(links.activate(link.token(), PASSWORD)).isEqualTo(ActivationResult.ACTIVATED);

		User user = users.findByEmail(email).orElseThrow();
		assertThat(user.getPasswordHash()).startsWith("$argon2id$");
		assertThat(passwordEncoder.matches(PASSWORD, user.getPasswordHash())).isTrue();
		assertThat(tokens.findById(user.getId())).isEmpty();
		assertThat(links.lookup(link.token())).isEmpty();

		assertThat(links.activate(link.token(), "another password!")).isEqualTo(ActivationResult.LINK_INVALID);
		assertThat(passwordEncoder.matches(PASSWORD, users.findById(user.getId()).orElseThrow().getPasswordHash()))
			.isTrue();
	}

	@Test
	void anExpiredOrUnknownLinkCannotActivate() {
		String email = newEmail();
		IssuedLink link = issued(links.createUser(email, Role.USER, TTL));
		expire(link.token());

		assertThat(links.activate(link.token(), PASSWORD)).isEqualTo(ActivationResult.LINK_INVALID);
		assertThat(links.activate("unknown-" + UUID.randomUUID(), PASSWORD)).isEqualTo(ActivationResult.LINK_INVALID);
		assertThat(users.findByEmail(email).orElseThrow().getPasswordHash()).isNull();
	}

	@Test
	void aPasswordAgainstThePolicyKeepsTheLink() {
		String email = newEmail();
		IssuedLink link = issued(links.createUser(email, Role.USER, TTL));

		assertThat(links.activate(link.token(), "x".repeat(Passwords.MIN_LENGTH - 1)))
			.isEqualTo(ActivationResult.PASSWORD_POLICY);
		assertThat(links.activate(link.token(), "x".repeat(Passwords.MAX_LENGTH + 1)))
			.isEqualTo(ActivationResult.PASSWORD_POLICY);
		// The policy is checked first, before the link is looked at.
		assertThat(links.activate("unknown-" + UUID.randomUUID(), "short")).isEqualTo(ActivationResult.PASSWORD_POLICY);

		assertThat(users.findByEmail(email).orElseThrow().getPasswordHash()).isNull();
		assertThat(links.lookup(link.token())).contains(email);
		assertThat(links.activate(link.token(), PASSWORD)).isEqualTo(ActivationResult.ACTIVATED);
	}

	@Test
	void aNewLinkReplacesThePreviousOne() {
		String email = newEmail();
		IssuedLink first = issued(links.createUser(email, Role.USER, TTL));

		IssuedLink second = issued(links.resetPassword(email, TTL));

		assertThat(second.token()).isNotEqualTo(first.token());
		assertThat(links.lookup(first.token())).isEmpty();
		assertThat(links.activate(first.token(), PASSWORD)).isEqualTo(ActivationResult.LINK_INVALID);
		assertThat(links.lookup(second.token())).contains(email);
		assertThat(links.activate(second.token(), PASSWORD)).isEqualTo(ActivationResult.ACTIVATED);
	}

	// --- reset ----------------------------------------------------------------------------------

	@Test
	void resetRemovesThePasswordAndTheSessionsOfThatAccountOnly() {
		String email = newEmail();
		UUID id = activatedAccount(email);
		UUID otherId = activatedAccount(newEmail());
		createSession(sessions, id);
		createSession(sessions, id);
		String otherSession = createSession(sessions, otherId);
		List<String> primaryIds = jdbc.sql("SELECT primary_id FROM spring_session WHERE principal_name = ?")
			.param(id.toString())
			.query(String.class)
			.list();
		assertThat(primaryIds).hasSize(2);
		assertThat(attributeRows(primaryIds)).isPositive();

		IssuedLink link = issued(links.resetPassword(email, TTL));

		assertThat(link.email()).isEqualTo(email);
		assertThat(link.role()).isEqualTo(Role.USER);
		assertThat(users.findById(id).orElseThrow().getPasswordHash()).isNull();
		assertThat(sessions.findByPrincipalName(id.toString())).isEmpty();
		assertThat(attributeRows(primaryIds)).isZero();
		// The other account keeps its password and its session.
		assertThat(sessions.findByPrincipalName(otherId.toString())).containsOnlyKeys(otherSession);
		assertThat(passwordEncoder.matches(PASSWORD, users.findById(otherId).orElseThrow().getPasswordHash())).isTrue();
		// The reset account is activated again through the new link.
		assertThat(links.activate(link.token(), "a new password!")).isEqualTo(ActivationResult.ACTIVATED);
		assertThat(passwordEncoder.matches("a new password!", users.findById(id).orElseThrow().getPasswordHash()))
			.isTrue();
	}

	@Test
	void activationLogsTheAccountOutEverywhere() {
		String email = newEmail();
		UUID id = activatedAccount(email);
		UUID otherId = activatedAccount(newEmail());
		IssuedLink link = issued(links.resetPassword(email, TTL));
		// A login past its password check when the reset committed stores its session after the reset.
		createSession(sessions, id);
		String otherSession = createSession(sessions, otherId);

		assertThat(links.activate(link.token(), PASSWORD)).isEqualTo(ActivationResult.ACTIVATED);

		assertThat(sessions.findByPrincipalName(id.toString())).isEmpty();
		assertThat(sessions.findByPrincipalName(otherId.toString())).containsOnlyKeys(otherSession);
	}

	// --- concurrency ----------------------------------------------------------------------------

	@Test
	void twoConcurrentActivationsWithOneLinkSucceedOnce() throws Exception {
		String email = newEmail();
		IssuedLink link = issued(links.createUser(email, Role.USER, TTL));
		List<String> passwords = List.of(PASSWORD + " one", PASSWORD + " two");
		ExecutorService executor = Executors.newFixedThreadPool(2);
		List<Future<ActivationResult>> results = new ArrayList<>();
		try (Connection lockHolder = dataSource.getConnection()) {
			// Hold the token's row lock so that both activations queue up behind it.
			lockHolder.setAutoCommit(false);
			try (PreparedStatement lock = lockHolder
				.prepareStatement("SELECT 1 FROM activation_tokens WHERE token_hash = ? FOR UPDATE")) {
				lock.setString(1, Sha256.hex(link.token()));
				try (ResultSet row = lock.executeQuery()) {
					assertThat(row.next()).isTrue();
				}
			}
			for (String password : passwords) {
				results.add(executor.submit(() -> links.activate(link.token(), password)));
			}
			awaitLockWaiters("activation_tokens", 2);
			lockHolder.commit(); // releases the lock
		}
		finally {
			executor.shutdown();
		}
		List<ActivationResult> outcomes = new ArrayList<>();
		for (Future<ActivationResult> result : results) {
			outcomes.add(result.get(30, TimeUnit.SECONDS));
		}

		assertThat(outcomes).containsExactlyInAnyOrder(ActivationResult.ACTIVATED, ActivationResult.LINK_INVALID);
		int winner = outcomes.indexOf(ActivationResult.ACTIVATED);
		User user = users.findByEmail(email).orElseThrow();
		assertThat(passwordEncoder.matches(passwords.get(winner), user.getPasswordHash())).isTrue();
		assertThat(passwordEncoder.matches(passwords.get(1 - winner), user.getPasswordHash())).isFalse();
		assertThat(tokens.findById(user.getId())).isEmpty();
	}

	// --- helpers --------------------------------------------------------------------------------

	private static String newEmail() {
		return "user" + SEQUENCE.incrementAndGet() + "-" + UUID.randomUUID().toString().substring(0, 8)
				+ "@example.com";
	}

	private static IssuedLink issued(IssueOutcome outcome) {
		assertThat(outcome).isInstanceOf(IssuedLink.class);
		return (IssuedLink) outcome;
	}

	/** An account with {@link #PASSWORD}, set through its first link. */
	private UUID activatedAccount(String email) {
		IssuedLink link = issued(links.createUser(email, Role.USER, TTL));
		assertThat(links.activate(link.token(), PASSWORD)).isEqualTo(ActivationResult.ACTIVATED);
		return users.findByEmail(email).orElseThrow().getId();
	}

	/** Moves the link's expiry into the past. */
	private void expire(String token) {
		int updated = jdbc
			.sql("UPDATE activation_tokens SET expires_at = now() - interval '1 second' WHERE token_hash = ?")
			.param(Sha256.hex(token))
			.update();
		assertThat(updated).isOne();
	}

	/** A session of {@code userId} as login stores it: the user's id is the principal name. */
	private static <S extends Session> String createSession(FindByIndexNameSessionRepository<S> repository,
			UUID userId) {
		S session = repository.createSession();
		session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, userId.toString());
		repository.save(session);
		return session.getId();
	}

	private long attributeRows(List<String> sessionPrimaryIds) {
		return jdbc.sql("SELECT count(*) FROM spring_session_attributes WHERE session_primary_id IN (:ids)")
			.param("ids", sessionPrimaryIds)
			.query(Long.class)
			.single();
	}

	/** Waits until {@code count} database sessions wait for a lock in a statement on {@code table}. */
	private void awaitLockWaiters(String table, int count) throws InterruptedException {
		long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
		long waiting;
		do {
			waiting = jdbc
				.sql("SELECT count(*) FROM pg_stat_activity WHERE datname = current_database()"
						+ " AND wait_event_type = 'Lock' AND query LIKE :table")
				.param("table", "%" + table + "%")
				.query(Long.class)
				.single();
			if (waiting >= count) {
				return;
			}
			Thread.sleep(20);
		}
		while (System.nanoTime() < deadline);
		assertThat(waiting).as("statements on " + table + " waiting for a lock").isEqualTo(count);
	}

}
