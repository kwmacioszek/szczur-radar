package pl.szczurradar.api.agents.assessor.validation.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import pl.szczurradar.api.agents.assessor.contract.AssessmentInputV1;
import pl.szczurradar.api.agents.assessor.contract.AssessmentOutputV2;
import pl.szczurradar.api.agents.assessor.contract.Claim;
import pl.szczurradar.api.agents.assessor.contract.Target;
import pl.szczurradar.api.agents.assessor.validation.RuleId;
import pl.szczurradar.api.agents.assessor.validation.ValidationRule;
import pl.szczurradar.api.agents.assessor.validation.Violation;

/** {@link RuleId#D2}. */
public final class ClaimTargetsExistRule implements ValidationRule {

	@Override
	public RuleId id() {
		return RuleId.D2;
	}

	@Override
	public List<Violation> check(AssessmentInputV1 input, AssessmentOutputV2 output) {
		Set<String> targetIds = output.targets().stream().map(Target::id).collect(Collectors.toSet());
		List<Violation> violations = new ArrayList<>();
		for (int i = 0; i < output.claims().size(); i++) {
			Claim claim = output.claims().get(i);
			for (int j = 0; j < claim.targetIds().size(); j++) {
				String targetId = claim.targetIds().get(j);
				if (!targetIds.contains(targetId)) {
					violations.add(id().violation("$.claims[" + i + "].target_ids[" + j + "]",
							"no target with id " + targetId));
				}
			}
		}
		return violations;
	}

}
