package pl.szczurradar.api.config;

import java.io.IOException;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Body of every application error: {@code {"detail": "<code>"}}. The SPA reads {@code detail} when
 * it is a string ({@code szczur-radar-web/src/api/client.ts}).
 *
 * @param detail a stable machine-readable code such as {@code invalid_credentials}
 */
public record ApiError(String detail) {

	private static final Pattern CODE = Pattern.compile("[a-z_]+");

	public ApiError {
		if (!CODE.matcher(detail).matches()) {
			throw new IllegalArgumentException("error codes are lowercase identifiers: " + detail);
		}
	}

	public static ResponseEntity<ApiError> response(HttpStatus status, String detail) {
		return ResponseEntity.status(status).body(new ApiError(detail));
	}

	/** For filters and Spring Security handlers, which answer outside Spring MVC. */
	public static void write(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
		ApiError error = new ApiError(detail); // validated code: safe to embed without JSON escaping
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.getWriter().write("{\"detail\":\"" + error.detail() + "\"}");
	}

}
