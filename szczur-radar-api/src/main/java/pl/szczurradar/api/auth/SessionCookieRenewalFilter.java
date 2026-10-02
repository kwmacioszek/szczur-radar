package pl.szczurradar.api.auth;

import java.io.IOException;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.util.OnCommittedResponseWrapper;
import org.springframework.session.web.http.HttpSessionIdResolver;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Renews the session cookie's {@code Max-Age} on every successful authenticated API response, so
 * the browser keeps the cookie as long as the session lives (30 days of inactivity). Spring Session
 * only refreshes {@code lastAccessedTime} in the database; without a new {@code Set-Cookie} the
 * browser would drop the cookie 30 days after login.
 *
 * <p>Renews only when the response is 2xx, the request is authenticated (not anonymous) and its
 * session is still valid ({@code getSession(false)}); never creates a session or rotates its id.
 * Skips the probes, login (which issues the cookie itself) and logout (which clears it). The cookie
 * is written through Spring Session's own {@link HttpSessionIdResolver}, before the response is
 * committed; the resolver skips the write if the same session id was already written in this
 * request, so there is never a second {@code sr_session} cookie.
 */
public class SessionCookieRenewalFilter extends OncePerRequestFilter {

	private static final Set<String> EXCLUDED_PATHS = Set.of("/api/health", "/api/ready", "/api/auth/login",
			"/api/auth/logout");

	private final HttpSessionIdResolver sessionIdResolver;

	private final AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();

	private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

	public SessionCookieRenewalFilter(HttpSessionIdResolver sessionIdResolver) {
		this.sessionIdResolver = sessionIdResolver;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getRequestURI();
		return !path.startsWith("/api/") || EXCLUDED_PATHS.contains(path);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		RenewingResponse renewing = new RenewingResponse(request, response);
		chain.doFilter(request, renewing);
		// Nothing committed the response yet (e.g. an empty body): renew now, the headers are still open.
		renewing.renewOnce();
	}

	private final class RenewingResponse extends OnCommittedResponseWrapper {

		private final HttpServletRequest request;

		private boolean done;

		RenewingResponse(HttpServletRequest request, HttpServletResponse response) {
			super(response);
			this.request = request;
		}

		@Override
		protected void onResponseCommitted() {
			renewOnce();
		}

		void renewOnce() {
			if (done) {
				return;
			}
			done = true;
			HttpServletResponse target = (HttpServletResponse) getResponse();
			int status = target.getStatus();
			if (target.isCommitted() || status < 200 || status >= 300) {
				return;
			}
			if (!trustResolver.isAuthenticated(contextHolder.getContext().getAuthentication())) {
				return;
			}
			HttpSession session = request.getSession(false);
			if (session == null) {
				return;
			}
			sessionIdResolver.setSessionId(request, target, session.getId());
		}

	}

}
