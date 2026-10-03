package pl.szczurradar.api.agents.assessor.validation.rules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import pl.szczurradar.api.agents.assessor.contract.AssessmentInputV1;
import pl.szczurradar.api.agents.assessor.contract.AssessmentOutputV2;
import pl.szczurradar.api.agents.assessor.contract.Attribution;
import pl.szczurradar.api.agents.assessor.contract.Claim;
import pl.szczurradar.api.agents.assessor.contract.Stance;
import pl.szczurradar.api.agents.assessor.validation.RuleId;
import pl.szczurradar.api.agents.assessor.validation.StanceTable;
import pl.szczurradar.api.agents.assessor.validation.ValidationRule;
import pl.szczurradar.api.agents.assessor.validation.Violation;

/**
 * {@link RuleId#D14}: someone else's opinion ({@code reported}) is not the author's stance.
 * Unknown claim IDs count as no claim (D3 reports them).
 */
public final class DirectionalStanceAuthorClaimRule implements ValidationRule {

	@Override
	public RuleId id() {
		return RuleId.D14;
	}

	@Override
	public List<Violation> check(AssessmentInputV1 input, AssessmentOutputV2 output) {
		Map<String, Claim> claims = new HashMap<>();
		output.claims().forEach(claim -> claims.putIfAbsent(claim.id(), claim));
		List<Violation> violations = new ArrayList<>();
		for (int i = 0; i < output.targets().size(); i++) {
			Stance stance = output.targets().get(i).stance();
			if (!StanceTable.of(stance.direction()).authorClaimRequired()) {
				continue;
			}
			boolean authorClaim = stance.claimIds()
				.stream()
				.map(claims::get)
				.anyMatch(claim -> claim != null && claim.attribution() == Attribution.AUTHOR);
			if (!authorClaim) {
				violations.add(id().violation("$.targets[" + i + "].stance.claim_ids",
						"direction " + stance.direction().value() + " needs a claim with attribution author"));
			}
		}
		return violations;
	}

}
