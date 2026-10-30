package pl.szczurradar.api.config;

import java.io.IOException;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Where the servlet container sends everything that ends in an error status without an
 * {@link ApiError} body: an uncaught exception (500), an unknown route (404), a method a route does not
 * allow (405). Replaces Spring Boot's {@code BasicErrorController}, whose body
 * ({@code timestamp}, {@code status}, {@code error}, {@code path}) the SPA cannot read: every answer is
 * {@code {"detail": "<code>"}} ({@link ErrorCode#forStatus}), whatever the {@code Accept} header says.
 *
 * <p>Nothing about the failure reaches the body — no message, no exception class. The container already
 * logs an uncaught exception, so it is not logged again here. {@code SecurityConfig} permits the error
 * dispatch; a direct request to {@code /error} still needs a session like any other route.
 */
@RestController
class ApiErrorController implements ErrorController {

	@RequestMapping("/error")
	void error(HttpServletRequest request, HttpServletResponse response) throws IOException {
		int status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) instanceof Integer code ? code
				: HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
		ApiError.write(response, status, ErrorCode.forStatus(status));
	}

}
