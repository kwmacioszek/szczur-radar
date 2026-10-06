package pl.szczurradar.api.agents.assessor.validation.rules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import pl.szczurradar.api.agents.assessor.contract.AssessmentInputV1;
import pl.szczurradar.api.agents.assessor.contract.AssessmentOutputV2;
import pl.szczurradar.api.agents.assessor.contract.Claim;
import pl.szczurradar.api.agents.assessor.contract.Target;
import pl.szczurradar.api.agents.assessor.validation.RuleId;
import pl.szczurradar.api.agents.assessor.validation.ValidationRule;
import pl.szczurradar.api.agents.assessor.validation.Violation;

/** {@link RuleId#D11}. Unknown claim IDs are left to D3. */
public final class StanceClaimsCoverTargetRule implements ValidationRule {

	@Override
	public RuleId id() {
		return RuleId.D11;
	}

	@Override
	public List<Violation> check(AssessmentInputV1 input, AssessmentOutputV2 output) {
		Map<String, Claim> claims = new HashMap<>();
		output.claims().forEach(claim -> claims.putIfAbsent(claim.id(), claim));
		List<Violation> violations = new ArrayList<>();
		for (int i = 0; i < output.targets().size(); i++) {
			Target target = output.targets().get(i);
			List<String> claimIds = target.stance().claimIds();
			for (int j = 0; j < claimIds.size(); j++) {
				Claim claim = claims.get(claimIds.get(j));
				if (claim != null && !claim.targetIds().contains(target.id())) {
					violations.add(id().violation("$.targets[" + i + "].stance.claim_ids[" + j + "]",
							"claim " + claim.id() + " does not have target " + target.id() + " in target_ids"));
				}
			}
		}
		return violations;
	}

}
