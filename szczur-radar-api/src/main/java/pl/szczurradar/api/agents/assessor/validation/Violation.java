package pl.szczurradar.api.agents.assessor.validation;

import java.util.Objects;

/**
 * One contract violation in a model response, or the failed call that produced none.
 *
 * @param failureClass the class of §12
 * @param rule the broken rule: a domain rule ({@code D1}–{@code D15}), {@code json} for parsing,
 *     {@code schema:<keyword>} for the shape or {@code call} for a call without a response
 *     ({@code provider_error}, {@code timeout})
 * @param path JSON path of the offending value, e.g. {@code $.claims[0].id}; {@code $} for the whole
 *     response
 * @param message what is wrong, for logs and reports
 */
public record Violation(FailureClass failureClass, String rule, String path, String message) {

	public Violation {
		Objects.requireNonNull(failureClass, "failureClass");
		Objects.requireNonNull(rule, "rule");
		Objects.requireNonNull(path, "path");
		Objects.requireNonNull(message, "message");
	}

}
