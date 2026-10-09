package pl.szczurradar.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import pl.szczurradar.api.TestcontainersConfiguration;
import pl.szczurradar.api.cli.MigrateCommand;

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
			"POST /api/auth/login", "obtaining a session cannot require one",
			"POST /api/auth/logout", "idempotent session end and cookie removal");

	/** Routes that a plain user must get 403 on (see {@code AdminRoleIT}). */
	static final Set<String> ADMIN_ROUTES = Set.of(
			"POST /api/admin/pipeline-runs",
			"GET /api/admin/pipeline-runs",
			"GET /api/admin/pipeline-runs/sources",
			"GET /api/admin/posts/failed",
			"GET /api/admin/llm/check");

	private static final Pattern PATH_VARIABLE = Pattern.compile("\\{(\\w+)(?::[^}]*)?}");

	@Autowired
	MockMvcTester mvc;

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	RequestMappingHandlerMapping handlerMapping;

	@Test
	void allowlistsReferenceExistingRoutes() {
		Set<String> declared = new TreeSet<>();
		routes().forEach(route -> declared.add(route.key()));

		assertThat(declared).as("dead PUBLIC_ROUTES").containsAll(PUBLIC_ROUTES.keySet());
		assertThat(declared).as("dead ADMIN_ROUTES").containsAll(ADMIN_ROUTES);
		assertThat(ADMIN_ROUTES).doesNotContainAnyElementsOf(PUBLIC_ROUTES.keySet());
	}

	@Test
	void everyNonPublicRouteRequiresASession() {
		List<String> checked = new ArrayList<>();
		List<String> unprotected = new ArrayList<>();
		for (Route route : routes()) {
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

	private List<Route> routes() {
		List<Route> routes = new ArrayList<>();
		for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
			RequestMappingInfo info = entry.getKey();
			Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
			Set<RequestMethod> effective = methods.isEmpty()
					? Set.of(RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH,
							RequestMethod.DELETE)
					: methods;
			for (String pattern : info.getPatternValues()) {
				for (RequestMethod method : effective) {
					routes.add(new Route(method.name(), pattern, entry.getValue()));
				}
			}
		}
		return routes;
	}

	private record Route(String method, String pattern, HandlerMethod handler) {

		String key() {
			return method + " " + pattern;
		}

		String concretePath() {
			Matcher matcher = PATH_VARIABLE.matcher(pattern);
			StringBuilder path = new StringBuilder();
			while (matcher.find()) {
				matcher.appendReplacement(path, dummyFor(matcher.group(1)));
			}
			matcher.appendTail(path);
			return path.toString();
		}

		private String dummyFor(String variable) {
			for (MethodParameter parameter : handler.getMethodParameters()) {
				PathVariable annotation = parameter.getParameterAnnotation(PathVariable.class);
				if (annotation == null) {
					continue;
				}
				String name = annotation.value().isEmpty() ? parameter.getParameterName() : annotation.value();
				if (variable.equals(name)) {
					Class<?> type = parameter.getParameterType();
					if (type == UUID.class) {
						return new UUID(0, 0).toString();
					}
					if (Number.class.isAssignableFrom(type) || type.isPrimitive()) {
						return "1";
					}
				}
			}
			return "x";
		}

	}

}
