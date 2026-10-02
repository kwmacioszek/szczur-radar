package pl.szczurradar.api.auth;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

import pl.szczurradar.api.config.ApiError;
import pl.szczurradar.api.config.ErrorCode;

/**
 * Cross-origin write protection (CSRF defence on top of {@code SameSite=Strict}; Spring's CSRF
 * tokens are disabled).
 *
 * <p>State-changing requests under {@code /api/} whose {@code Origin} header names another
 * {@code host[:port]} than the request's {@code Host} are rejected with 403
 * {@code {"detail":"cross_origin"}}; so is {@code Origin: null} or any origin without a host.
 * Requests without {@code Origin} (curl, server-to-server) pass. Caddy's {@code reverse_proxy}
 * forwards the original {@code Host}, so the comparison holds behind the web proxy. Runs before
 * authentication.
 */
public class OriginGuardFilter extends OncePerRequestFilter {

	private static final Set<String> UNSAFE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !UNSAFE_METHODS.contains(request.getMethod()) || !request.getRequestURI().startsWith("/api/");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String origin = request.getHeader(HttpHeaders.ORIGIN);
		if (origin != null && isCrossOrigin(origin, request.getHeader(HttpHeaders.HOST))) {
			ApiError.write(response, ErrorCode.CROSS_ORIGIN);
			return;
		}
		chain.doFilter(request, response);
	}

	static boolean isCrossOrigin(String origin, String host) {
		String originAuthority = authority(origin);
		if (originAuthority.isEmpty() || host == null) {
			return true;
		}
		return !originAuthority.equals(host.strip().toLowerCase(Locale.ROOT));
	}

	/** {@code host[:port]} of an origin as written (no default-port folding), or "" if none. */
	private static String authority(String origin) {
		try {
			String authority = new URI(origin).getRawAuthority();
			return authority == null ? "" : authority.toLowerCase(Locale.ROOT);
		}
		catch (URISyntaxException ex) {
			return "";
		}
	}

}
