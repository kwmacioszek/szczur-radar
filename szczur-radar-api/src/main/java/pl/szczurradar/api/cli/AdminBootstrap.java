package pl.szczurradar.api.cli;

import java.io.PrintStream;
import java.util.Objects;

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
 * any admin exists: further admins come from {@code create-user --role admin}, and a lost admin
 * password is recovered with {@code reset-password} (S-07).
 *
 * <p>Everything runs in one transaction holding a PostgreSQL advisory lock, so separate CLI
 * processes are serialized through the check and the insert: two concurrent bootstraps create
 * exactly one admin. The password is asked only after the checks pass, is read twice and is never
 * printed.
 */
@Service
public class AdminBootstrap {

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
	 * @return {@link ExitCode#OK} when the admin was created, {@link ExitCode#REFUSED} when an admin or the
	 *     e-mail exists, the passwords differ or break the policy; nothing is written then
	 */
	public ExitCode createAdmin(String email, PasswordPrompt prompt, PrintStream out, PrintStream err) {
		ExitCode code = Objects.requireNonNull(transaction.execute(status -> {
			jdbc.sql("SELECT pg_advisory_xact_lock(?)").param(LOCK_ID).query((rs, row) -> true).single();
			if (users.existsByRole(Role.ADMIN)) {
				err.println("Admin already exists — to recover access use " + CommandType.RESET_PASSWORD.cliName() + " "
						+ Option.EMAIL.flag() + " <e-mail>; for another admin use " + CommandType.CREATE_USER.cliName()
						+ " " + Option.EMAIL.flag() + " <e-mail> " + Option.ROLE.flag() + " " + Role.ADMIN.value()
						+ ".");
				return ExitCode.REFUSED;
			}
			if (users.existsByEmail(email)) {
				err.println("An account with e-mail " + email + " already exists.");
				return ExitCode.REFUSED;
			}

			String password = prompt.read("Password: ");
			if (!password.equals(prompt.read("Repeat password: "))) {
				err.println("Passwords do not match. Nothing was created.");
				return ExitCode.REFUSED;
			}
			try {
				Passwords.validatePolicy(password);
			}
			catch (IllegalArgumentException ex) {
				err.println(ex.getMessage() + " Nothing was created.");
				return ExitCode.REFUSED;
			}

			users.save(new User(email, passwordEncoder.encode(password), Role.ADMIN));
			return ExitCode.OK;
		}), "transaction result");
		if (code == ExitCode.OK) {
			out.println("Created admin " + email);
		}
		return code;
	}

}
