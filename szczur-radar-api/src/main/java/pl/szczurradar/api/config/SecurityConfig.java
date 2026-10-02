package pl.szczurradar.api.config;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.session.web.http.HttpSessionIdResolver;

import pl.szczurradar.api.auth.JsonAccessDeniedHandler;
import pl.szczurradar.api.auth.JsonAuthenticationEntryPoint;
import pl.szczurradar.api.auth.OriginGuardFilter;
import pl.szczurradar.api.auth.SessionCookieRenewalFilter;

/**
 * Deny by default: every route needs a session except the public ones listed here — keep them in
 * sync with {@code PUBLIC_ROUTES} in {@code AuthorizationCompletenessIT}, which calls every mapped
 * route without a session. Routes under {@code /api/admin/} additionally need the admin role.
 *
 * <p>Answers are JSON ({@link ApiError}), never redirects or login pages. CSRF tokens are off: the
 * session cookie is {@code SameSite=Strict} and {@link OriginGuardFilter} rejects cross-origin
 * writes (S-01 contract, no changes in the SPA).
 *
 * <p>The filter chain exists only with the web server: operator commands ({@code migrate},
 * {@code create-admin}) run the same context without one.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

	@Bean
	@ConditionalOnWebApplication(type = Type.SERVLET)
	SecurityFilterChain apiSecurity(HttpSecurity http, SecurityContextRepository securityContextRepository,
			HttpSessionIdResolver sessionIdResolver) throws Exception {
		http
			.authorizeHttpRequests(auth -> auth
				// Error pages rendered for an already authorized request (e.g. 404, 415) stay visible.
				.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
				.requestMatchers(HttpMethod.GET, "/api/health", "/api/ready").permitAll()
				.requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/logout").permitAll()
				.requestMatchers("/api/admin/**").hasRole("ADMIN")
				.anyRequest().authenticated())
			.csrf(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			// Never store the anonymous request for a post-login redirect: that would create a session.
			.requestCache(cache -> cache.requestCache(new NullRequestCache()))
			.securityContext(context -> context.securityContextRepository(securityContextRepository))
			.exceptionHandling(errors -> errors
				.authenticationEntryPoint(new JsonAuthenticationEntryPoint())
				.accessDeniedHandler(new JsonAccessDeniedHandler()))
			.addFilterBefore(new OriginGuardFilter(), SecurityContextHolderFilter.class)
			.addFilterAfter(new SessionCookieRenewalFilter(sessionIdResolver), AuthorizationFilter.class);
		return http.build();
	}

	/** Where the login controller saves the authenticated context: the (JDBC-backed) HTTP session. */
	@Bean
	SecurityContextRepository securityContextRepository() {
		return new DelegatingSecurityContextRepository(new RequestAttributeSecurityContextRepository(),
				new HttpSessionSecurityContextRepository());
	}

	/** E-mail + password; also equalizes timing for unknown e-mails. Erases credentials after use. */
	@Bean
	AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
		provider.setPasswordEncoder(passwordEncoder);
		return new ProviderManager(provider);
	}

}
