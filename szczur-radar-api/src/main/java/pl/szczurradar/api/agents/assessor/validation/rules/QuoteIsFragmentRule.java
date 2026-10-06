package pl.szczurradar.api.agents.assessor.validation.rules;

import java.util.List;

import pl.szczurradar.api.agents.assessor.contract.AssessmentInputV1;
import pl.szczurradar.api.agents.assessor.contract.AssessmentOutputV2;
import pl.szczurradar.api.agents.assessor.validation.RuleId;
import pl.szczurradar.api.agents.assessor.validation.ValidationRule;
import pl.szczurradar.api.agents.assessor.validation.Violation;
import pl.szczurradar.api.text.Whitespace;

/**
 * {@link RuleId#D6} — nothing more lenient: case, inflection, "[...]" and markdown must match.
 * Quotes failing D5 or D7 are left to those rules.
 */
public final class QuoteIsFragmentRule implements ValidationRule {

	@Override
	public RuleId id() {
		return RuleId.D6;
	}

	@Override
	public List<Violation> check(AssessmentInputV1 input, AssessmentOutputV2 output) {
		return QuotedText.all(output)
			.stream()
			.filter(quoted -> quoted.resolvesToText(input))
			.filter(quoted -> !Whitespace.normalize(quoted.fieldText(input))
				.contains(Whitespace.normalize(quoted.quote().quote())))
			.map(quoted -> id().violation(quoted.path() + ".quote",
					"quote is not a fragment of " + quoted.location()))
			.toList();
	}

}
