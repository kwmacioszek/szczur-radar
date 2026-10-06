package pl.szczurradar.api.llm;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Why a model call produced no text. The values match the call classes of the assessment contract
 * ({@code provider_error}, {@code timeout}) without this infrastructure depending on it.
 */
public enum CallFailure {

	/** Refusal, a non-retryable error, or a retryable error on the last attempt. */
	PROVIDER_ERROR,

	/** The last attempt ran out of time. */
	TIMEOUT;

	@JsonValue
	public String value() {
		return name().toLowerCase(Locale.ROOT);
	}

}
