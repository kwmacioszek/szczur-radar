package pl.szczurradar.api.agents.assessor.validation;

import java.util.List;

import pl.szczurradar.api.agents.assessor.contract.AssessmentInputV1;
import pl.szczurradar.api.agents.assessor.contract.AssessmentOutputV2;

/**
 * One domain rule of the contract (a strategy). A rule reports every violation it finds and nothing
 * else; it never repairs the output. Rules run on output that already passed the schema.
 */
public interface ValidationRule {

	/** The contract rule this strategy implements; unique within a rule set. */
	RuleId id();

	/** All violations of this rule, in document order; empty when the output satisfies it. */
	List<Violation> check(AssessmentInputV1 input, AssessmentOutputV2 output);

}
