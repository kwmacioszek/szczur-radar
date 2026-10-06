package pl.szczurradar.api.agents.assessor.validation.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import pl.szczurradar.api.agents.assessor.contract.AssessmentInputV1;
import pl.szczurradar.api.agents.assessor.contract.AssessmentOutputV2;
import pl.szczurradar.api.agents.assessor.contract.Stance;
import pl.szczurradar.api.agents.assessor.validation.RuleId;
import pl.szczurradar.api.agents.assessor.validation.StanceTable;
import pl.szczurradar.api.agents.assessor.validation.ValidationRule;
import pl.szczurradar.api.agents.assessor.validation.Violation;

/** {@link RuleId#D13}, reading the row from {@link StanceTable}. */
public final class StanceClaimCountRule implements ValidationRule {

	@Override
	public RuleId id() {
		return RuleId.D13;
	}

	@Override
	public List<Violation> check(AssessmentInputV1 input, AssessmentOutputV2 output) {
		List<Violation> violations = new ArrayList<>();
		for (int i = 0; i < output.targets().size(); i++) {
			Stance stance = output.targets().get(i).stance();
			StanceTable row = StanceTable.of(stance.direction());
			if (!row.allowsClaimIdCount(stance.claimIds().size())) {
				violations.add(id().violation("$.targets[" + i + "].stance.claim_ids",
						"direction " + stance.direction().value() + " has claim_ids "
								+ row.claimIds().name().toLowerCase(Locale.ROOT) + ", got "
								+ stance.claimIds().size()));
			}
		}
		return violations;
	}

}
