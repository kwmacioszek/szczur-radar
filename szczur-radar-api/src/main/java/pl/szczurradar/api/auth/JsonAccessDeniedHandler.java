package pl.szczurradar.api.auth;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import pl.szczurradar.api.config.ApiError;
import pl.szczurradar.api.config.ErrorCode;

/** Logged in but lacking the role: 403 {@code {"detail":"forbidden"}}. */
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException accessDeniedException) throws IOException {
		ApiError.write(response, ErrorCode.FORBIDDEN);
	}

}
