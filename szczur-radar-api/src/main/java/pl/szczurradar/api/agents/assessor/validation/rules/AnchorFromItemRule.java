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

/** {@link RuleId#D9}. */
public final class AnchorFromItemRule implements ValidationRule {

	@Override
	public RuleId id() {
		return RuleId.D9;
	}

	@Override
	public List<Violation> check(AssessmentInputV1 input, AssessmentOutputV2 output) {
		List<Violation> violations = new ArrayList<>();
		for (int i = 0; i < output.targets().size(); i++) {
			Target target = output.targets().get(i);
			if (target.reference() != Reference.CONTEXTUAL && target.anchor().document() != Document.ITEM) {
				violations.add(id().violation("$.targets[" + i + "].anchor.document", "reference "
						+ target.reference().value() + " needs an anchor from item, not " + target.anchor().document().value()));
			}
		}
		return violations;
	}

}
