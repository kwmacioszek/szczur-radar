package pl.szczurradar.api.agents.assessor.validation;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import pl.szczurradar.api.agents.assessor.contract.AssessmentInputV1;
import pl.szczurradar.api.agents.assessor.contract.AssessmentOutputV2;

/**
 * The domain validator (contract-v2.md §12 step 3): runs an ordered rule set and collects all
 * violations. It knows no rule itself — a new rule is a new {@link ValidationRule} added to the set
 * (for V2: {@link ContractV2Rules}).
 */
public final class RuleBasedValidator {

	private final List<ValidationRule> rules;

	public RuleBasedValidator(List<ValidationRule> rules) {
		Set<RuleId> seen = EnumSet.noneOf(RuleId.class);
		for (ValidationRule rule : rules) {
			if (!seen.add(rule.id())) {
				throw new IllegalArgumentException("rule " + rule.id() + " appears more than once in the rule set");
			}
		}
		this.rules = List.copyOf(rules);
	}

	/** The validator of contract V2. */
	public static RuleBasedValidator contractV2() {
		return new RuleBasedValidator(ContractV2Rules.rules());
	}

	/** The rule IDs in run order. */
	public List<RuleId> ruleIds() {
		return rules.stream().map(ValidationRule::id).toList();
	}

	public ValidationResult validate(AssessmentInputV1 input, AssessmentOutputV2 output) {
		List<Violation> violations = new ArrayList<>();
		for (ValidationRule rule : rules) {
			violations.addAll(rule.check(input, output));
		}
		return new ValidationResult(violations);
	}

}
