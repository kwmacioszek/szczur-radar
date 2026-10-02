package pl.szczurradar.api.config;

import java.io.IOException;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Body of every application error: {@code {"detail": "<code>"}}. The SPA reads {@code detail} when
 * it is a string ({@code szczur-radar-web/src/api/client.ts}).
 *
 * @param detail the error; serialized as its {@link ErrorCode#code()}
 */
public record ApiError(ErrorCode detail) {

	/** The error with its status, for controllers. */
	public static ResponseEntity<ApiError> response(ErrorCode code) {
		return ResponseEntity.status(code.status()).body(new ApiError(code));
	}

	/** For filters and Spring Security handlers, which answer outside Spring MVC. */
	public static void write(HttpServletResponse response, ErrorCode code) throws IOException {
		response.setStatus(code.status().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		// Codes are lower-case enum names: safe to embed without JSON escaping.
		response.getWriter().write("{\"detail\":\"" + code.code() + "\"}");
	}

}
