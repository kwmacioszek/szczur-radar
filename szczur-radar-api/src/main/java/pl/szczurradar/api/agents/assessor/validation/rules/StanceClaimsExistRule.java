package pl.szczurradar.api.agents.assessor.validation.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import pl.szczurradar.api.agents.assessor.contract.AssessmentInputV1;
import pl.szczurradar.api.agents.assessor.contract.AssessmentOutputV2;
import pl.szczurradar.api.agents.assessor.contract.Claim;
import pl.szczurradar.api.agents.assessor.contract.Stance;
import pl.szczurradar.api.agents.assessor.validation.RuleId;
import pl.szczurradar.api.agents.assessor.validation.ValidationRule;
import pl.szczurradar.api.agents.assessor.validation.Violation;

/** {@link RuleId#D3}. */
public final class StanceClaimsExistRule implements ValidationRule {

	@Override
	public RuleId id() {
		return RuleId.D3;
	}

	@Override
	public List<Violation> check(AssessmentInputV1 input, AssessmentOutputV2 output) {
		Set<String> claimIds = output.claims().stream().map(Claim::id).collect(Collectors.toSet());
		List<Violation> violations = new ArrayList<>();
		for (int i = 0; i < output.targets().size(); i++) {
			Stance stance = output.targets().get(i).stance();
			for (int j = 0; j < stance.claimIds().size(); j++) {
				String claimId = stance.claimIds().get(j);
				if (!claimIds.contains(claimId)) {
					violations.add(id().violation("$.targets[" + i + "].stance.claim_ids[" + j + "]",
							"no claim with id " + claimId));
				}
			}
		}
		return violations;
	}

}
