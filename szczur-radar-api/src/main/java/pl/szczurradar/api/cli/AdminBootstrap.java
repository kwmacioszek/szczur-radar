package pl.szczurradar.api.cli;

import java.io.PrintStream;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import pl.szczurradar.api.auth.Passwords;
import pl.szczurradar.api.user.Role;
import pl.szczurradar.api.user.User;
import pl.szczurradar.api.user.UserRepository;

/**
 * Creates the first administrator (FR-026), ported from the Python {@code app/cli.py}. Refuses when
 * any admin exists — further admins and recovery belong to S-07.
 *
 * <p>Everything runs in one transaction holding a PostgreSQL advisory lock, so separate CLI
 * processes are serialized through the check and the insert: two concurrent bootstraps create
 * exactly one admin. The password is asked only after the checks pass, is read twice and is never
 * printed.
 */
@Service
public class AdminBootstrap {

	/** Exit code: the admin was created. */
	public static final int CREATED = 0;

	/** Exit code: refused (admin exists, e-mail taken, passwords differ, policy); nothing written. */
	public static final int REFUSED = 1;

	/** Stable advisory lock key for the one-time admin bootstrap ("SR_ADMIN"). */
	static final long LOCK_ID = 0x53525F41444D494EL;

	private final UserRepository users;

	private final PasswordEncoder passwordEncoder;

	private final JdbcClient jdbc;

	private final TransactionTemplate transaction;

	AdminBootstrap(UserRepository users, PasswordEncoder passwordEncoder, JdbcClient jdbc,
			TransactionTemplate transaction) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.jdbc = jdbc;
		this.transaction = transaction;
	}

	/**
	 * @param email an already normalized and validated address
	 * @return {@link #CREATED} or {@link #REFUSED}
	 */
	public int createAdmin(String email, PasswordPrompt prompt, PrintStream out, PrintStream err) {
		Integer code = transaction.execute(status -> {
			jdbc.sql("SELECT pg_advisory_xact_lock(?)").param(LOCK_ID).query((rs, row) -> true).single();
			if (users.existsByRole(Role.ADMIN)) {
				err.println("Admin already exists — use the recovery command (S-07).");
				return REFUSED;
			}
			if (users.existsByEmail(email)) {
				err.println("An account with e-mail " + email + " already exists.");
				return REFUSED;
			}

			String password = prompt.read("Password: ");
			if (!password.equals(prompt.read("Repeat password: "))) {
				err.println("Passwords do not match. Nothing was created.");
				return REFUSED;
			}
			try {
				Passwords.validatePolicy(password);
			}
			catch (IllegalArgumentException ex) {
				err.println(ex.getMessage() + " Nothing was created.");
				return REFUSED;
			}

			users.save(new User(email, passwordEncoder.encode(password), Role.ADMIN));
			return CREATED;
		});
		if (code == CREATED) {
			out.println("Created admin " + email);
		}
		return code;
	}

}
