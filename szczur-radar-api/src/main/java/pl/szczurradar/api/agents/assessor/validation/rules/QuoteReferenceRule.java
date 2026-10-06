package pl.szczurradar.api.agents.assessor.validation.rules;

import java.util.List;

import pl.szczurradar.api.agents.assessor.contract.AssessmentInputV1;
import pl.szczurradar.api.agents.assessor.contract.AssessmentOutputV2;
import pl.szczurradar.api.agents.assessor.validation.RuleId;
import pl.szczurradar.api.agents.assessor.validation.ValidationRule;
import pl.szczurradar.api.agents.assessor.validation.Violation;

/** {@link RuleId#D5}; the first step of the quote invariant, see {@link QuotedText}. */
public final class QuoteReferenceRule implements ValidationRule {

	@Override
	public RuleId id() {
		return RuleId.D5;
	}

	@Override
	public List<Violation> check(AssessmentInputV1 input, AssessmentOutputV2 output) {
		return QuotedText.all(output)
			.stream()
			.filter(quoted -> !quoted.resolves(input))
			.map(quoted -> id().violation(quoted.path(), "quoted field " + quoted.location() + " does not exist"))
			.toList();
	}

}
