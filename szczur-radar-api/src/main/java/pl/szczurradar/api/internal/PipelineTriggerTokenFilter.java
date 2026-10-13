package pl.szczurradar.api.internal;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.web.filter.OncePerRequestFilter;

import pl.szczurradar.api.user.SystemAccount;

/**
 * Authenticates the daily cron on {@code /internal/**}: {@code Authorization: Bearer <token>} equal
 * to {@link PipelineTriggerToken} makes the request {@link SystemAccount#SCHEDULER} (the principal
 * name is its id, like a session's user id) with the single authority {@link #AUTHORITY}.
 *
 * <p>Anything else — no header, another scheme, a wrong token, no token configured — leaves the
 * request unauthenticated, and the chain's entry point answers 401. The authentication lives only
 * for this request: the chain is stateless, nothing is stored in a session.
 */
public final class PipelineTriggerTokenFilter extends OncePerRequestFilter {

	/** The only authority of the token, required by every {@code /internal/**} route. */
	public static final String AUTHORITY = "PIPELINE_TRIGGER";

	private static final String BEARER = "Bearer ";

	private final PipelineTriggerToken token;

	private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

	public PipelineTriggerTokenFilter(PipelineTriggerToken token) {
		this.token = token;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header != null && header.regionMatches(true, 0, BEARER, 0, BEARER.length())
				&& token.matches(header.substring(BEARER.length()))) {
			SecurityContext context = contextHolder.createEmptyContext();
			context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
					SystemAccount.SCHEDULER.id().toString(), null, List.of(new SimpleGrantedAuthority(AUTHORITY))));
			contextHolder.setContext(context);
		}
		chain.doFilter(request, response);
	}

}
