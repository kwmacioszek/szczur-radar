package pl.szczurradar.api.agents.assessor.validation;

import java.util.Objects;

/**
 * One contract violation in a model response.
 *
 * @param failureClass the class of §12
 * @param rule the broken rule: a domain rule ({@code D1}–{@code D15}), {@code json} for parsing or
 *     {@code schema:<keyword>} for the shape
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
