package pl.szczurradar.api.agents.assessor.validation.rules;

import java.util.ArrayList;
import java.util.List;

import pl.szczurradar.api.agents.assessor.contract.AssessmentInputV1;
import pl.szczurradar.api.agents.assessor.contract.AssessmentOutputV2;
import pl.szczurradar.api.agents.assessor.contract.Document;
import pl.szczurradar.api.agents.assessor.contract.Reference;
import pl.szczurradar.api.agents.assessor.contract.Target;
import pl.szczurradar.api.agents.assessor.validation.RuleId;
import pl.szczurradar.api.agents.assessor.validation.ValidationRule;
import pl.szczurradar.api.agents.assessor.validation.Violation;

/** {@link RuleId#D10}. */
public final class ContextualAnchorFromParentRule implements ValidationRule {

	@Override
	public RuleId id() {
		return RuleId.D10;
	}

	@Override
	public List<Violation> check(AssessmentInputV1 input, AssessmentOutputV2 output) {
		List<Violation> violations = new ArrayList<>();
		for (int i = 0; i < output.targets().size(); i++) {
			Target target = output.targets().get(i);
			if (target.reference() == Reference.CONTEXTUAL && target.anchor().document() == Document.ITEM) {
				violations.add(id().violation("$.targets[" + i + "].anchor.document",
						"reference contextual needs an anchor from a parent, not item"));
			}
		}
		return violations;
	}

}
