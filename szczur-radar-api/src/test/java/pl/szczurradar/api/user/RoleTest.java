package pl.szczurradar.api.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

/** Role values are stored in the database (ck_users_role) and sent to the SPA: pinned here. */
class RoleTest {

	@Test
	void valuesMatchTheDatabaseAndApiContract() {
		assertThat(Role.USER.value()).isEqualTo("user");
		assertThat(Role.ADMIN.value()).isEqualTo("admin");
		assertThat(Role.values()).hasSize(2);
	}

	@Test
	void parsesStoredValues() {
		assertThat(Role.fromValue("admin")).isEqualTo(Role.ADMIN);
		assertThatIllegalArgumentException().isThrownBy(() -> Role.fromValue("ADMIN"));
	}

}
