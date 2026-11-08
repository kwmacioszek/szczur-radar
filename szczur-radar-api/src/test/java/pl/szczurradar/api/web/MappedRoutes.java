package pl.szczurradar.api.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Every route the application maps, for the tests that walk them all ({@code auth/AuthorizationCompletenessIT},
 * {@code auth/AdminRoleIT}, {@code contract/ContractRoutesIT}). It returns everything, {@code /error} and
 * {@code /internal/**} included; a test that needs fewer filters them itself.
 */
public final class MappedRoutes {

	private static final Pattern PATH_VARIABLE = Pattern.compile("\\{(\\w+)(?::[^}]*)?}");

	private MappedRoutes() {
	}

	/**
	 * Every route of {@code handlerMapping}, one per method and pattern; a mapping without a method stands
	 * for every write and read method.
	 */
	public static List<Route> routes(RequestMappingHandlerMapping handlerMapping) {
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

	/** A route: its method, its path pattern and the handler that serves it. */
	public record Route(String method, String pattern, HandlerMethod handler) {

		/** {@code "<METHOD> <pattern>"}, as the tests' route lists name it. */
		public String key() {
			return method + " " + pattern;
		}

		/** The pattern with every path variable filled with a dummy of its parameter's type. */
		public String concretePath() {
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
