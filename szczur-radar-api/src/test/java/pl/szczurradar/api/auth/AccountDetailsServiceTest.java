package pl.szczurradar.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import pl.szczurradar.api.user.Role;
import pl.szczurradar.api.user.User;
import pl.szczurradar.api.user.UserRepository;

/**
 * Accounts that cannot log in get the unusable hash instead of being reported as disabled. The
 * database never lets a system account hold a password ({@code ck_users_login_password}); this checks
 * that the login would refuse one anyway.
 */
class AccountDetailsServiceTest {

	private static final String EMAIL = "someone@example.com";

	private static final String HASH = "$argon2id$v=19$real-hash";

	private final UserRepository users = mock(UserRepository.class);

	private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

	@Test
	void anAccountThatCanLogInGetsItsOwnHash() {
		UserDetails details = detailsOf(account(true, HASH));

		assertThat(details.getPassword()).isEqualTo(HASH);
	}

	@Test
	void anAccountWithoutAPasswordGetsTheUnusableHash() {
		UserDetails details = detailsOf(account(true, null));

		assertThat(details.getPassword()).isEqualTo("unusable");
		assertThat(details.isEnabled()).isTrue();
	}

	@Test
	void anAccountThatCannotLogInGetsTheUnusableHashEvenWithAPassword() {
		UserDetails details = detailsOf(account(false, HASH));

		assertThat(details.getPassword()).isEqualTo("unusable");
		// Not reported as disabled: that would answer before the password check and reveal the account.
		assertThat(details.isEnabled()).isTrue();
	}

	private UserDetails detailsOf(User account) {
		when(passwordEncoder.encode(any())).thenReturn("unusable");
		when(users.findByEmail(EMAIL)).thenReturn(Optional.of(account));
		return new AccountDetailsService(users, passwordEncoder).loadUserByUsername(EMAIL);
	}

	private static User account(boolean loginEnabled, @Nullable String passwordHash) {
		User user = mock(User.class);
		when(user.getId()).thenReturn(UUID.randomUUID());
		when(user.getRole()).thenReturn(Role.USER);
		when(user.isLoginEnabled()).thenReturn(loginEnabled);
		when(user.getPasswordHash()).thenReturn(passwordHash);
		return user;
	}

}
