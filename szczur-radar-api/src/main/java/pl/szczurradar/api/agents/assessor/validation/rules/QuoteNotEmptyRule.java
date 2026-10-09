package pl.szczurradar.api.agents.assessor.validation.rules;

import java.util.List;

import pl.szczurradar.api.agents.assessor.contract.AssessmentInputV1;
import pl.szczurradar.api.agents.assessor.contract.AssessmentOutputV2;
import pl.szczurradar.api.agents.assessor.validation.RuleId;
import pl.szczurradar.api.agents.assessor.validation.ValidationRule;
import pl.szczurradar.api.agents.assessor.validation.Violation;
import pl.szczurradar.api.text.Whitespace;

/**
 * {@link RuleId#D7}: rejected even if the field holds the same white space. Quotes failing D5 are
 * left to D5.
 */
public final class QuoteNotEmptyRule implements ValidationRule {

	@Override
	public RuleId id() {
		return RuleId.D7;
	}

	@Override
	public List<Violation> check(AssessmentInputV1 input, AssessmentOutputV2 output) {
		return QuotedText.all(output)
			.stream()
			.filter(quoted -> quoted.resolves(input) && Whitespace.normalize(quoted.quote().quote()).isEmpty())
			.map(quoted -> id().violation(quoted.path() + ".quote", "quote is white space only"))
			.toList();
	}

}
