package pl.szczurradar.api.agents.assessor.validation;

import java.util.List;
import java.util.Optional;

/** Every domain violation of one output, in rule order; valid when there are none. */
public record ValidationResult(List<Violation> violations) {

	/** Precedence of the result's class (contract-v2.md §12). */
	private static final List<FailureClass> PRECEDENCE = List.of(FailureClass.INVALID_REFERENCE,
			FailureClass.FABRICATED_EVIDENCE, FailureClass.DOMAIN_INVARIANT_VIOLATION);

	public ValidationResult {
		violations = List.copyOf(violations);
	}

	public boolean valid() {
		return violations.isEmpty();
	}

	/**
	 * The result's class: the first class present in the order {@code invalid_reference →
	 * fabricated_evidence → domain_invariant_violation}, else the first violation's class (a rule
	 * outside the contract table never makes an invalid output pass); empty when valid.
	 */
	public Optional<FailureClass> failureClass() {
		return PRECEDENCE.stream()
			.filter(failureClass -> violations.stream().anyMatch(v -> v.failureClass() == failureClass))
			.findFirst()
			.or(() -> violations.stream().map(Violation::failureClass).findFirst());
	}

}
