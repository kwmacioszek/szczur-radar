package pl.szczurradar.api.sources.wykop;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertyResolver;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * The application's {@link WykopRepository}, from {@code WYKOP_KEY} and {@code WYKOP_SECRET} (secrets:
 * never logged or echoed). Both are optional: without them the API, CI and tests start and there is
 * simply no {@link WykopRepository} bean — consumers inject {@code Optional<WykopRepository>}, and a
 * range run fails with {@code wykop_not_configured}. Only one of the two set is a configuration error
 * that fails the start.
 */
@Configuration(proxyBeanMethods = false)
public class WykopConfiguration {

	public static final String KEY = "WYKOP_KEY";

	public static final String SECRET = "WYKOP_SECRET";

	@Bean
	@Conditional(KeyOrSecretSet.class)
	WykopRepository wykopRepository(Environment environment) {
		String key = text(environment, KEY);
		String secret = text(environment, SECRET);
		if (key.isEmpty() || secret.isEmpty()) {
			throw new IllegalStateException(
					"Invalid Wykop configuration: " + KEY + " and " + SECRET + " must be set together (only "
							+ (key.isEmpty() ? SECRET : KEY) + " is set). See szczur-radar-api/README.md (Wykop).");
		}
		return new WykopRepository(new WykopApiClient(key, secret));
	}

	private static String text(PropertyResolver environment, String name) {
		@Nullable String value = environment.getProperty(name);
		return value == null ? "" : value.strip();
	}

	/** Matches when {@code WYKOP_KEY} or {@code WYKOP_SECRET} is set (non-blank). */
	static class KeyOrSecretSet implements Condition {

		@Override
		public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
			return !text(context.getEnvironment(), KEY).isEmpty() || !text(context.getEnvironment(), SECRET).isEmpty();
		}

	}

}
