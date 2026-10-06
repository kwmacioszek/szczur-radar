package pl.szczurradar.api.agents.assessor;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonValue;
import org.jspecify.annotations.Nullable;

import pl.szczurradar.api.agents.assessor.contract.AssessmentOutputV2;
import pl.szczurradar.api.agents.assessor.validation.FailureClass;
import pl.szczurradar.api.agents.assessor.validation.Violation;

/**
 * The outcome of assessing one entry, ready to be stored without interpretation: a valid output, or
 * the failure class with every violation found. A result with any violation is never {@code ok}.
 *
 * @param status {@code ok} or {@code failed}
 * @param output the model's output (or the prefilter's), only when {@code ok}
 * @param failureClass why it failed (contract-v2.md §12), only when {@code failed}
 * @param violations every violation, in the order found; empty when {@code ok}
 * @param provenance where the result came from
 * @param rawText the model's text exactly as returned; {@code null} for the prefilter and when the
 *     call produced no text
 */
public record AssessmentResult(Status status, @Nullable AssessmentOutputV2 output, @Nullable FailureClass failureClass,
		List<Violation> violations, Provenance provenance, @Nullable String rawText) {

	/** Whether the result is a valid assessment. */
	public enum Status {

		OK, FAILED;

		@JsonValue
		public String value() {
			return name().toLowerCase(Locale.ROOT);
		}

	}

	public AssessmentResult {
		Objects.requireNonNull(status, "status");
		Objects.requireNonNull(provenance, "provenance");
		violations = List.copyOf(violations);
		if (status == Status.OK && (output == null || failureClass != null || !violations.isEmpty())) {
			throw new IllegalArgumentException("an ok result has an output and no failure");
		}
		if (status == Status.FAILED && (output != null || failureClass == null || violations.isEmpty())) {
			throw new IllegalArgumentException("a failed result has a failure class, violations and no output");
		}
	}

	static AssessmentResult ok(AssessmentOutputV2 output, Provenance provenance, @Nullable String rawText) {
		return new AssessmentResult(Status.OK, output, null, List.of(), provenance, rawText);
	}

	static AssessmentResult failed(FailureClass failureClass, List<Violation> violations, Provenance provenance,
			@Nullable String rawText) {
		return new AssessmentResult(Status.FAILED, null, failureClass, violations, provenance, rawText);
	}

	public boolean ok() {
		return status == Status.OK;
	}

}
