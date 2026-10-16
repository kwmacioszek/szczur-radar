package pl.szczurradar.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import pl.szczurradar.api.TestcontainersConfiguration;
import pl.szczurradar.api.auth.AuthorizationCompletenessIT.Route;
import pl.szczurradar.api.user.Role;
import pl.szczurradar.probe.AdminProbeController;

/**
 * Routes under {@code /api/admin/} need the admin role: a plain user gets 403
 * {@code {"detail":"forbidden"}} (the Python {@code require_admin} probe tests). Every route listed
 * in {@code AuthorizationCompletenessIT.ADMIN_ROUTES} is checked the same way, its path variables filled
 * as there ({@link Route#concretePath()}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, AdminProbeController.class })
class AdminRoleIT {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	RequestMappingHandlerMapping handlerMapping;

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
		List<String> checked = new ArrayList<>();
		for (Route route : AuthorizationCompletenessIT.routes(handlerMapping)) {
			if (!AuthorizationCompletenessIT.ADMIN_ROUTES.contains(route.key())) {
				continue;
			}
			checked.add(route.key());
			assertThat(mvc.method(HttpMethod.valueOf(route.method()))
				.uri(route.concretePath())
				.with(user(UUID.randomUUID().toString()).roles(Role.USER.name()))).as(route.key())
				.hasStatus(HttpStatus.FORBIDDEN);
		}

		assertThat(checked).containsExactlyInAnyOrderElementsOf(AuthorizationCompletenessIT.ADMIN_ROUTES);
	}

}
