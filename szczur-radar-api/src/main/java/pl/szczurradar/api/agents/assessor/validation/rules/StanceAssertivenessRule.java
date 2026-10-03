package pl.szczurradar.api.agents.assessor.validation.rules;

import java.util.ArrayList;
import java.util.List;

import pl.szczurradar.api.agents.assessor.contract.AssessmentInputV1;
import pl.szczurradar.api.agents.assessor.contract.AssessmentOutputV2;
import pl.szczurradar.api.agents.assessor.contract.Stance;
import pl.szczurradar.api.agents.assessor.validation.RuleId;
import pl.szczurradar.api.agents.assessor.validation.StanceTable;
import pl.szczurradar.api.agents.assessor.validation.ValidationRule;
import pl.szczurradar.api.agents.assessor.validation.Violation;

/** {@link RuleId#D12}, reading the row from {@link StanceTable}. */
public final class StanceAssertivenessRule implements ValidationRule {

	@Override
	public RuleId id() {
		return RuleId.D12;
	}

	@Override
	public List<Violation> check(AssessmentInputV1 input, AssessmentOutputV2 output) {
		List<Violation> violations = new ArrayList<>();
		for (int i = 0; i < output.targets().size(); i++) {
			Stance stance = output.targets().get(i).stance();
			StanceTable row = StanceTable.of(stance.direction());
			if (!row.allowsAssertiveness(stance.assertiveness())) {
				violations.add(id().violation("$.targets[" + i + "].stance.assertiveness",
						"direction " + stance.direction().value() + " needs assertiveness "
								+ (row.assertiveness() == StanceTable.AssertivenessPresence.NULL ? "null" : "hedged or unhedged")));
			}
		}
		return violations;
	}

}
