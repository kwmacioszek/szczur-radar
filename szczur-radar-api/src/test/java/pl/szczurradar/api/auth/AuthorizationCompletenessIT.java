package pl.szczurradar.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import pl.szczurradar.api.TestcontainersConfiguration;
import pl.szczurradar.api.cli.MigrateCommand;
import pl.szczurradar.api.web.MappedRoutes;
import pl.szczurradar.api.web.MappedRoutes.Route;

/**
 * Authorization completeness: EVERY mapped route requires a session (ported from the Python
 * tests/test_authz_completeness.py).
 *
 * <p>The rule for every new route: it needs no change at all (deny by default in
 * {@code SecurityConfig}), unless it must be public — then it is permitted in {@code SecurityConfig}
 * AND listed in {@link #PUBLIC_ROUTES} with a justification. Admin-only routes go under
 * {@code /api/admin/} and are listed in {@link #ADMIN_ROUTES}.
 *
 * <p>The test walks every handler mapping of the real application and calls each method without a
 * session, so a route that slips past the security rules fails CI. Path variables are filled with
 * a dummy of the right type so that conversion does not answer 400 before security answers 401.
 */
@SpringBootTest(properties = MigrateCommand.ENABLE_FLYWAY)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthorizationCompletenessIT {

	static final Map<String, String> PUBLIC_ROUTES = Map.of(
			"GET /api/health", "liveness probe (Railway healthcheck)",
			"GET /api/ready", "readiness probe, reveals only up/down",
			"GET /api/deploy-safety", "CI deploy gate; reveals only whether a pipeline run is queued or running",
			"POST /api/auth/login", "obtaining a session cannot require one",
			"POST /api/auth/logout", "idempotent session end and cookie removal",
			"POST /api/auth/activation",
			"setting the first password cannot require a session; the one-time token is the credential",
			"POST /api/auth/activation/lookup",
			"checks a one-time link before the password form; reveals only the e-mail to the link holder");

	/** Routes that a plain user must get 403 on (see {@code AdminRoleIT}). */
	static final Set<String> ADMIN_ROUTES = Set.of(
			"POST /api/admin/pipeline-runs",
			"GET /api/admin/pipeline-runs",
			"GET /api/admin/pipeline-runs/sources",
			"GET /api/admin/pipeline-runs/missing-days",
			"GET /api/admin/pipeline-runs/{id}",
			"GET /api/admin/posts/failed",
			"POST /api/admin/posts/restore",
			"GET /api/admin/assessment-quality",
			"GET /api/admin/llm/check",
			"GET /api/admin/notifications",
			"POST /api/admin/notifications/{id}/read",
			"POST /api/admin/notifications/read");

	@Autowired
	MockMvcTester mvc;

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	RequestMappingHandlerMapping handlerMapping;

	@Test
	void allowlistsReferenceExistingRoutes() {
		Set<String> declared = new TreeSet<>();
		MappedRoutes.routes(handlerMapping).forEach(route -> declared.add(route.key()));

		assertThat(declared).as("dead PUBLIC_ROUTES").containsAll(PUBLIC_ROUTES.keySet());
		assertThat(declared).as("dead ADMIN_ROUTES").containsAll(ADMIN_ROUTES);
		assertThat(ADMIN_ROUTES).doesNotContainAnyElementsOf(PUBLIC_ROUTES.keySet());
	}

	@Test
	void everyNonPublicRouteRequiresASession() {
		List<String> checked = new ArrayList<>();
		List<String> unprotected = new ArrayList<>();
		for (Route route : MappedRoutes.routes(handlerMapping)) {
			if (PUBLIC_ROUTES.containsKey(route.key())) {
				continue;
			}
			checked.add(route.key());
			MvcTestResult result = mvc.method(HttpMethod.valueOf(route.method())).uri(route.concretePath()).exchange();
			if (result.getUnresolvedException() != null) {
				// The handler ran without a session and blew up: unprotected just the same.
				unprotected.add(route.key() + " -> " + result.getUnresolvedException());
			}
			else if (result.getResponse().getStatus() != 401) {
				unprotected.add(route.key() + " -> " + result.getResponse().getStatus());
			}
		}

		assertThat(checked).as("protected routes found").contains("GET /api/auth/me");
		assertThat(unprotected)
			.as("routes answering without a session; guard them or list them in PUBLIC_ROUTES with a reason")
			.isEmpty();
	}

}
