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

/** {@link RuleId#D1}. */
public final class UniqueIdsRule implements ValidationRule {

	@Override
	public RuleId id() {
		return RuleId.D1;
	}

	@Override
	public List<Violation> check(AssessmentInputV1 input, AssessmentOutputV2 output) {
		List<Violation> violations = new ArrayList<>();
		Set<String> targetIds = new HashSet<>();
		for (int i = 0; i < output.targets().size(); i++) {
			String id = output.targets().get(i).id();
			if (!targetIds.add(id)) {
				violations.add(id().violation("$.targets[" + i + "].id", "duplicate target id " + id));
			}
		}
		Set<String> claimIds = new HashSet<>();
		for (int i = 0; i < output.claims().size(); i++) {
			String id = output.claims().get(i).id();
			if (!claimIds.add(id)) {
				violations.add(id().violation("$.claims[" + i + "].id", "duplicate claim id " + id));
			}
		}
		return violations;
	}

}
