package pl.szczurradar.api.agents.assessor.validation.rules;

import java.util.ArrayList;
import java.util.List;

import pl.szczurradar.api.agents.assessor.contract.AssessmentInputV1;
import pl.szczurradar.api.agents.assessor.contract.AssessmentOutputV2;
import pl.szczurradar.api.agents.assessor.contract.Document;
import pl.szczurradar.api.agents.assessor.validation.RuleId;
import pl.szczurradar.api.agents.assessor.validation.ValidationRule;
import pl.szczurradar.api.agents.assessor.validation.Violation;

/** {@link RuleId#D8}. */
public final class EvidenceFromItemRule implements ValidationRule {

	@Override
	public RuleId id() {
		return RuleId.D8;
	}

	@Override
	public List<Violation> check(AssessmentInputV1 input, AssessmentOutputV2 output) {
		List<Violation> violations = new ArrayList<>();
		for (int i = 0; i < output.claims().size(); i++) {
			Document document = output.claims().get(i).evidence().document();
			if (document != Document.ITEM) {
				violations.add(id().violation("$.claims[" + i + "].evidence.document",
						"evidence must quote item, quotes " + document.value()));
			}
		}
		return violations;
	}

}
