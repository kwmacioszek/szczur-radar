package pl.szczurradar.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.io.UnsupportedEncodingException;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import pl.szczurradar.api.TestcontainersConfiguration;
import pl.szczurradar.api.llm.LlmClient;
import pl.szczurradar.api.user.Role;
import pl.szczurradar.api.web.MappedRoutes;
import pl.szczurradar.api.web.MappedRoutes.Route;
import pl.szczurradar.probe.AdminProbeController;

/**
 * Routes under {@code /api/admin/} need the admin role: a plain user gets 403
 * {@code {"detail":"forbidden"}} (the Python {@code require_admin} probe tests). Every mapped route under
 * {@code /api/admin/**} is checked the same way, with no list: the set comes from the mappings
 * ({@link MappedRoutes#routesUnder}), its path variables filled as in {@link Route#concretePath()}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, AdminProbeController.class })
class AdminRoleIT {

	@Autowired
	MockMvcTester mvc;

	/** No real provider call if the admin rule ever regresses and the walk reaches {@code /api/admin/llm/check}. */
	@MockitoBean
	LlmClient llm;

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
	void everyAdminRouteRejectsPlainUsers() {
		List<String> checked = new ArrayList<>();
		List<String> notForbidden = new ArrayList<>();
		for (Route route : MappedRoutes.routesUnder(handlerMapping, MappedRoutes.ADMIN_PATHS)) {
			checked.add(route.key());
			MvcTestResult result = mvc.method(HttpMethod.valueOf(route.method()))
				.uri(route.concretePath())
				.with(user(UUID.randomUUID().toString()).roles(Role.USER.name()))
				.exchange();
			if (result.getUnresolvedException() != null) {
				notForbidden.add(route.key() + " -> " + result.getUnresolvedException());
			}
			else if (result.getResponse().getStatus() != 403) {
				notForbidden.add(route.key() + " -> " + result.getResponse().getStatus());
			}
			else if (!"{\"detail\":\"forbidden\"}".equals(contentOf(result))) {
				notForbidden.add(route.key() + " -> 403 with body " + contentOf(result));
			}
		}

		assertThat(checked).as("admin routes found").contains("GET /api/admin/probe", "POST /api/admin/pipeline-runs");
		assertThat(notForbidden).as("admin routes a plain user can pass; they must answer 403 forbidden").isEmpty();
	}

	private static String contentOf(MvcTestResult result) {
		try {
			return result.getResponse().getContentAsString();
		}
		catch (UnsupportedEncodingException e) {
			throw new IllegalStateException(e);
		}
	}

}
