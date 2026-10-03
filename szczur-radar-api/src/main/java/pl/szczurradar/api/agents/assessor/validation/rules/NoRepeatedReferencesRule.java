package pl.szczurradar.api.agents.assessor.validation.rules;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import pl.szczurradar.api.agents.assessor.contract.AssessmentInputV1;
import pl.szczurradar.api.agents.assessor.contract.AssessmentOutputV2;
import pl.szczurradar.api.agents.assessor.validation.RuleId;
import pl.szczurradar.api.agents.assessor.validation.ValidationRule;
import pl.szczurradar.api.agents.assessor.validation.Violation;

/** {@link RuleId#D4}. */
public final class NoRepeatedReferencesRule implements ValidationRule {

	@Override
	public RuleId id() {
		return RuleId.D4;
	}

	@Override
	public List<Violation> check(AssessmentInputV1 input, AssessmentOutputV2 output) {
		List<Violation> violations = new ArrayList<>();
		for (int i = 0; i < output.targets().size(); i++) {
			repeats(output.targets().get(i).stance().claimIds(), "$.targets[" + i + "].stance.claim_ids", violations);
		}
		for (int i = 0; i < output.claims().size(); i++) {
			repeats(output.claims().get(i).targetIds(), "$.claims[" + i + "].target_ids", violations);
		}
		return violations;
	}

	private void repeats(List<String> ids, String path, List<Violation> violations) {
		Set<String> seen = new HashSet<>();
		for (int j = 0; j < ids.size(); j++) {
			if (!seen.add(ids.get(j))) {
				violations.add(id().violation(path + "[" + j + "]", "repeated reference " + ids.get(j)));
			}
		}
	}

}
