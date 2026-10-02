package pl.szczurradar.api.config;

import java.time.Duration;
import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.CookieHttpSessionIdResolver;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.session.web.http.HttpSessionIdResolver;

/**
 * The session cookie (sessions themselves live in Postgres via Spring Session JDBC, so they survive
 * API sleep and restarts): {@code sr_session; HttpOnly; Secure; SameSite=Strict; Path=/api;
 * Max-Age=<spring.session.timeout>}.
 *
 * <p>Declared explicitly instead of through {@code server.servlet.session.cookie.*} so that
 * {@code Secure} follows the S-01 rule (off only for {@code COOKIE_SECURE} in {0, false, no}) and
 * so the cookie renewal reuses the very same resolver and serializer as Spring Session.
 */
@Configuration(proxyBeanMethods = false)
public class SessionConfig {

	public static final String COOKIE_NAME = "sr_session";

	public static final String COOKIE_PATH = "/api";

	private static final Set<String> INSECURE_VALUES = Set.of("0", "false", "no");

	@Bean
	CookieSerializer cookieSerializer(@Value("${spring.session.timeout}") Duration timeout,
			@Value("${COOKIE_SECURE:true}") String cookieSecure) {
		DefaultCookieSerializer serializer = new DefaultCookieSerializer();
		serializer.setCookieName(COOKIE_NAME);
		serializer.setCookiePath(COOKIE_PATH);
		serializer.setUseHttpOnlyCookie(true);
		serializer.setUseSecureCookie(secureCookie(cookieSecure));
		serializer.setSameSite("Strict");
		serializer.setCookieMaxAge(Math.toIntExact(timeout.toSeconds()));
		return serializer;
	}

	@Bean
	HttpSessionIdResolver httpSessionIdResolver(CookieSerializer cookieSerializer) {
		CookieHttpSessionIdResolver resolver = new CookieHttpSessionIdResolver();
		resolver.setCookieSerializer(cookieSerializer);
		return resolver;
	}

	/** {@code Secure} unless {@code COOKIE_SECURE} is 0/false/no (any case) — plain-http local setups only. */
	static boolean secureCookie(String cookieSecure) {
		return !INSECURE_VALUES.contains(cookieSecure.strip().toLowerCase(Locale.ROOT));
	}

}
