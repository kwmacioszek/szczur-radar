package pl.szczurradar.api.llm;

import pl.szczurradar.api.config.LlmProviderProperties.ServiceTier;

/**
 * The provider refused the configuration, not this request: the key (HTTP 401), the account's credit
 * (402) or the model (404). Every later call would fail the same way, so callers stop instead of
 * recording it as the result of a request — an assessment is not attempted, a pipeline run stops
 * ({@code pipeline/assess/ConcurrentAssessmentDispatcher}). Still an {@link LlmCallException} (with
 * {@link CallFailure#PROVIDER_ERROR}) for callers that only report failed calls.
 */
public class LlmConfigurationException extends LlmCallException {

	public LlmConfigurationException(String message, int attempts, ServiceTier serviceTier, boolean flexFallback,
			Throwable cause) {
		super(CallFailure.PROVIDER_ERROR, message, attempts, serviceTier, flexFallback, cause);
	}

}
