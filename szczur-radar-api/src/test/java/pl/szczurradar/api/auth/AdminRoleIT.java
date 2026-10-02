package pl.szczurradar.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import pl.szczurradar.api.TestcontainersConfiguration;
import pl.szczurradar.api.user.Role;
import pl.szczurradar.probe.AdminProbeController;

/**
 * Routes under {@code /api/admin/} need the admin role: a plain user gets 403
 * {@code {"detail":"forbidden"}} (the Python {@code require_admin} probe tests). Every route listed
 * in {@code AuthorizationCompletenessIT.ADMIN_ROUTES} is checked the same way.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, AdminProbeController.class })
class AdminRoleIT {

	@Autowired
	MockMvcTester mvc;

	@Test
	void plainUserIsForbidden() {
		assertThat(mvc.get().uri("/api/admin/probe").with(user(UUID.randomUUID().toString()).roles(Role.USER.name())))
			.hasStatus(HttpStatus.FORBIDDEN)
			.bodyJson()
			.isStrictlyEqualTo("{\"detail\":\"forbidden\"}");
	}

	@Test
	void adminIsAllowed() {
		assertThat(mvc.get().uri("/api/admin/probe").with(user(UUID.randomUUID().toString()).roles(Role.ADMIN.name())))
			.hasStatusOk();
	}

	@Test
	void anonymousIsUnauthenticated() {
		assertThat(mvc.get().uri("/api/admin/probe")).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void listedAdminRoutesRejectPlainUsers() {
		for (String route : AuthorizationCompletenessIT.ADMIN_ROUTES) {
			String[] methodAndPath = route.split(" ", 2);
			assertThat(mvc.method(HttpMethod.valueOf(methodAndPath[0]))
				.uri(methodAndPath[1])
				.with(user(UUID.randomUUID().toString()).roles(Role.USER.name()))).as(route).hasStatus(HttpStatus.FORBIDDEN);
		}
	}

}
