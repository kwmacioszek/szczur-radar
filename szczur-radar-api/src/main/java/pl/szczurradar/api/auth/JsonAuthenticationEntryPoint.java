package pl.szczurradar.api.auth;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import pl.szczurradar.api.config.ApiError;
import pl.szczurradar.api.config.ErrorCode;

/** No (valid) session: 401 {@code {"detail":"not_authenticated"}}, never a redirect. */
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException {
		ApiError.write(response, ErrorCode.NOT_AUTHENTICATED);
	}

}
