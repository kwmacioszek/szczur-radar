package pl.szczurradar.api.config;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Request-shape errors in the {@link ApiError} format instead of Spring's default error body.
 * Request bodies are closed models: unknown fields fail deserialization
 * ({@code spring.jackson.deserialization.fail-on-unknown-properties=true}).
 */
@RestControllerAdvice
class ApiExceptionHandler {

	/** Malformed JSON, unknown fields or wrong types. */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ApiError> unreadable() {
		return ApiError.response(ErrorCode.INVALID_REQUEST);
	}

	/** Bean Validation failures ({@code @NotNull}, {@code @Size}, ...). */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ApiError> invalid() {
		return ApiError.response(ErrorCode.INVALID_REQUEST);
	}

	/** E.g. a form-encoded login: the API accepts JSON only. */
	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	ResponseEntity<ApiError> unsupportedMediaType() {
		return ApiError.response(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
	}

}
