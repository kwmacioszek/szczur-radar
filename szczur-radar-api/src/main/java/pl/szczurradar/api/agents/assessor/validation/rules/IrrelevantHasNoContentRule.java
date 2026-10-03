package pl.szczurradar.api.agents.assessor.validation.rules;

import java.util.ArrayList;
import java.util.List;

import pl.szczurradar.api.agents.assessor.contract.AssessmentInputV1;
import pl.szczurradar.api.agents.assessor.contract.AssessmentOutputV2;
import pl.szczurradar.api.agents.assessor.contract.Relevance;
import pl.szczurradar.api.agents.assessor.validation.RuleId;
import pl.szczurradar.api.agents.assessor.validation.ValidationRule;
import pl.szczurradar.api.agents.assessor.validation.Violation;

/** {@link RuleId#D15}; modifiers and flags are allowed. */
public final class IrrelevantHasNoContentRule implements ValidationRule {

	@Override
	public RuleId id() {
		return RuleId.D15;
	}

	@Override
	public List<Violation> check(AssessmentInputV1 input, AssessmentOutputV2 output) {
		List<Violation> violations = new ArrayList<>();
		if (output.relevance() != Relevance.NOT_INVESTMENT_RELATED) {
			return violations;
		}
		if (!output.targets().isEmpty()) {
			violations.add(id().violation("$.targets", "not_investment_related must have no targets"));
		}
		if (!output.claims().isEmpty()) {
			violations.add(id().violation("$.claims", "not_investment_related must have no claims"));
		}
		return violations;
	}

}
