package pl.szczurradar.api.auth;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Client address for the login limiter: {@code X-Real-IP} (set by Railway's edge, which overwrites
 * a client-supplied value), else the peer address. Behind Caddy {@code X-Forwarded-For} names
 * Railway's edge, not the client.
 */
final class ClientIp {

	private ClientIp() {
	}

	static String of(HttpServletRequest request) {
		String realIp = request.getHeader("X-Real-IP");
		if (realIp != null && !realIp.isBlank()) {
			return realIp.strip();
		}
		return request.getRemoteAddr();
	}

}
