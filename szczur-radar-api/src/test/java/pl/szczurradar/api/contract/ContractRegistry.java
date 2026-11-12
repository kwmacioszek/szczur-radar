package pl.szczurradar.api.contract;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import pl.szczurradar.api.pipeline.step.SourceIngest;

/**
 * Which route the SPA reads gives which body: its contract files in {@code szczur-radar-web/src/contract/}
 * and the Java type the files are a body of. The type is stated here, not derived, because most handlers
 * return {@code ResponseEntity<?>}.
 *
 * <p>The response records are package-private in their feature packages, so they are named by their binary
 * name and loaded with {@link Class#forName} when this class initializes; a name that no longer resolves
 * fails every test using the registry with that name. Reading a record's components and their annotations
 * needs no access to the class.
 *
 * <p>The rule for a new route under {@code /api/}: one the SPA reads gets its contract file, the file's typed
 * sample in {@code samples.ts} and an entry in {@link #SHAPES}; any other one an entry in
 * {@link #NOT_READ_BY_SPA} with the reason. {@code ContractRoutesIT} checks both lists against the mapped
 * routes, {@code ContractVariantsTest} the files and the variants of the types.
 */
final class ContractRegistry {

	/** One contract file and the type its body is. */
	record Shape(String file, Type type) {
	}

	private static final Class<?> USER = type("pl.szczurradar.api.auth.UserResponse");

	private static final Class<?> LOOKUP = type("pl.szczurradar.api.auth.activation.ActivationController$LookupResponse");

	private static final Class<?> NOTIFICATION_LIST = type(
			"pl.szczurradar.api.notifications.api.NotificationController$NotificationList");

	private static final Class<?> RUN_VIEW = type("pl.szczurradar.api.pipeline.api.PipelineRunController$RunView");

	private static final Class<?> RUN_PAGE = type("pl.szczurradar.api.pipeline.api.PipelineRunController$RunPage");

	private static final Class<?> MISSING_DAYS = type(
			"pl.szczurradar.api.pipeline.api.PipelineRunController$MissingDaysView");

	private static final Class<?> FRESHNESS = type("pl.szczurradar.api.pipeline.api.FreshnessController$Freshness");

	private static final Class<?> POST_PAGE = type("pl.szczurradar.api.posts.api.PostController$PostPage");

	private static final Class<?> POST_ITEM = type("pl.szczurradar.api.posts.api.PostController$PostItem");

	private static final Class<?> FAILED_POST_PAGE = type(
			"pl.szczurradar.api.posts.api.FailedPostController$FailedPostPage");

	private static final Class<?> RESTORE_RESULT = type("pl.szczurradar.api.posts.api.DeadPostController$RestoreResult");

	private static final Class<?> QUALITY = type(
			"pl.szczurradar.api.posts.api.AssessmentQualityController$QualityView");

	/** Route ({@code "<METHOD> <pattern>"}, as {@code web/MappedRoutes.Route.key()}) → its files and type. */
	static final Map<String, List<Shape>> SHAPES = shapes();

	/** Route → why it has no contract file. */
	static final Map<String, String> NOT_READ_BY_SPA = Map.of(
			"GET /api/health", "liveness probe: Railway's healthcheck and the deploy monitor read it, not the SPA",
			"GET /api/ready", "readiness probe: CI and the deploy monitor read it, not the SPA",
			"GET /api/deploy-safety", "the CI deploy gate reads it (tools/deploy-gate), not the SPA",
			"GET /api/admin/llm/check", "an operator's tool for the LLM settings; the SPA never calls it",
			"GET /api/posts/{id}/raw", "the source's raw payload as text (apiFetchText), not a shape of the API",
			"POST /api/auth/logout", "204 without a body",
			"POST /api/auth/activation", "204 without a body",
			"POST /api/admin/notifications/{id}/read", "204 without a body",
			"POST /api/admin/notifications/read", "204 without a body");

	private ContractRegistry() {
	}

	private static Map<String, List<Shape>> shapes() {
		Map<String, List<Shape>> shapes = new LinkedHashMap<>();
		shapes.put("POST /api/auth/login", List.of(new Shape("user.json", USER)));
		shapes.put("GET /api/auth/me", List.of(new Shape("user.json", USER)));
		shapes.put("POST /api/auth/activation/lookup", List.of(new Shape("activation-lookup.json", LOOKUP)));
		shapes.put("GET /api/admin/notifications", List.of(new Shape("notifications.json", NOTIFICATION_LIST),
				new Shape("notifications-without-run.json", NOTIFICATION_LIST)));
		shapes.put("POST /api/admin/pipeline-runs", List.of(new Shape("started-runs.json", listOf(RUN_VIEW))));
		shapes.put("GET /api/admin/pipeline-runs", List.of(new Shape("runs-page.json", RUN_PAGE)));
		shapes.put("GET /api/admin/pipeline-runs/{id}", List.of(new Shape("run.json", RUN_VIEW)));
		shapes.put("GET /api/admin/pipeline-runs/sources",
				List.of(new Shape("sources.json", listOf(SourceIngest.SourceState.class))));
		shapes.put("GET /api/admin/pipeline-runs/missing-days", List.of(new Shape("missing-days.json", MISSING_DAYS)));
		shapes.put("GET /api/freshness", List.of(new Shape("freshness.json", FRESHNESS)));
		shapes.put("GET /api/posts", List.of(new Shape("posts-page.json", POST_PAGE)));
		shapes.put("GET /api/posts/{id}", List.of(new Shape("post.json", POST_ITEM)));
		shapes.put("GET /api/admin/posts/failed", List.of(new Shape("failed-posts-page.json", FAILED_POST_PAGE)));
		shapes.put("POST /api/admin/posts/restore", List.of(new Shape("restore-result.json", RESTORE_RESULT)));
		shapes.put("GET /api/admin/assessment-quality", List.of(new Shape("assessment-quality.json", QUALITY),
				new Shape("assessment-quality-few-attempts.json", QUALITY)));
		return Map.copyOf(shapes);
	}

	/** The class of {@code binaryName}; fails at once naming it when it does not resolve. */
	private static Class<?> type(String binaryName) {
		try {
			return Class.forName(binaryName, false, ContractRegistry.class.getClassLoader());
		}
		catch (ClassNotFoundException ex) {
			throw new IllegalStateException("ContractRegistry names a class that does not exist: " + binaryName
					+ " — a response record was renamed or moved; correct its binary name here", ex);
		}
	}

	/** {@code List<element>}. */
	static ParameterizedType listOf(Type element) {
		return new ListType(element);
	}

	private record ListType(Type element) implements ParameterizedType {

		@Override
		public Type[] getActualTypeArguments() {
			return new Type[] { element };
		}

		@Override
		public Type getRawType() {
			return List.class;
		}

		@Override
		public @Nullable Type getOwnerType() {
			return null;
		}

		@Override
		public String toString() {
			return "List<" + element.getTypeName() + ">";
		}

	}

}
