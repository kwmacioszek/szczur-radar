package pl.szczurradar.api.pipeline.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import pl.szczurradar.api.config.ApiError;
import pl.szczurradar.api.config.ErrorCode;
import pl.szczurradar.api.pipeline.PipelineBusyException;
import pl.szczurradar.api.pipeline.PostDeadException;
import pl.szczurradar.api.pipeline.SourceNotConfiguredException;

/**
 * The refusals of {@code PipelineRunService} as API errors, the same for the admin's and the cron's
 * routes: 409 {@code pipeline_busy}, 400 {@code post_dead}, 503 {@code source_not_configured}.
 */
@RestControllerAdvice(assignableTypes = { PipelineRunController.class, InternalPipelineRunController.class })
class PipelineRunExceptionHandler {

	@ExceptionHandler(PipelineBusyException.class)
	ResponseEntity<ApiError> busy() {
		return ApiError.response(ErrorCode.PIPELINE_BUSY);
	}

	@ExceptionHandler(PostDeadException.class)
	ResponseEntity<ApiError> dead() {
		return ApiError.response(ErrorCode.POST_DEAD);
	}

	@ExceptionHandler(SourceNotConfiguredException.class)
	ResponseEntity<ApiError> sourceNotConfigured() {
		return ApiError.response(ErrorCode.SOURCE_NOT_CONFIGURED);
	}

}
