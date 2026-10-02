package pl.szczurradar.api.auth;

import java.util.Locale;
import java.util.UUID;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import pl.szczurradar.api.user.Emails;
import pl.szczurradar.api.user.User;
import pl.szczurradar.api.user.UserRepository;

/**
 * Looks accounts up by e-mail for {@code DaoAuthenticationProvider}.
 *
 * <p>The principal name is the user's UUID, not the e-mail: it is what the session stores and
 * indexes ({@code SPRING_SESSION.PRINCIPAL_NAME} is {@code VARCHAR(100)}, an e-mail may be 320).
 *
 * <p>An account without a password is NOT reported as disabled: the provider checks that flag
 * before the password, which would answer faster and reveal that the account exists. It gets an
 * unusable hash instead, so it costs a full argon2 verification and always fails like a wrong
 * password. Unknown e-mails are timing-equalized by the provider itself.
 */
@Service
class AccountDetailsService implements UserDetailsService {

	private final UserRepository users;

	/** Hash of a random secret generated at startup and never kept: it can never match. */
	private final String unusableHash;

	AccountDetailsService(UserRepository users, PasswordEncoder passwordEncoder) {
		this.users = users;
		this.unusableHash = passwordEncoder.encode(UUID.randomUUID() + "" + UUID.randomUUID());
	}

	@Override
	public UserDetails loadUserByUsername(String email) {
		User user = users.findByEmail(Emails.normalize(email))
				.orElseThrow(() -> new UsernameNotFoundException("unknown account"));
		String hash = user.passwordHash() != null ? user.passwordHash() : unusableHash;
		return org.springframework.security.core.userdetails.User.withUsername(user.id().toString())
				.password(hash)
				.roles(user.role().toUpperCase(Locale.ROOT))
				.build();
	}

}
