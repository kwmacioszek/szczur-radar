package pl.szczurradar.api.agents.assessor.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static pl.szczurradar.api.agents.assessor.ContractFixtures.claim;
import static pl.szczurradar.api.agents.assessor.ContractFixtures.investment;
import static pl.szczurradar.api.agents.assessor.ContractFixtures.target;
import static pl.szczurradar.api.agents.assessor.ContractFixtures.validInput;
import static pl.szczurradar.api.agents.assessor.ContractFixtures.validOutput;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import pl.szczurradar.api.agents.assessor.contract.AssessmentInputV1;
import pl.szczurradar.api.agents.assessor.contract.AssessmentOutputV2;
import pl.szczurradar.api.agents.assessor.contract.Attribution;
import pl.szczurradar.api.agents.assessor.contract.Direction;
import pl.szczurradar.api.agents.assessor.validation.rules.UniqueIdsRule;

/** The validator runs a rule set; it collects all violations and classifies the result (§12). */
class RuleBasedValidatorTest {

	@Test
	void contractV2RunsEveryRuleOnceWithQuoteRulesInOrderD5D7D6() {
		assertThat(RuleBasedValidator.contractV2().ruleIds()).containsExactly(RuleId.D1, RuleId.D2, RuleId.D3,
				RuleId.D4, RuleId.D5, RuleId.D7, RuleId.D6, RuleId.D8, RuleId.D9, RuleId.D10, RuleId.D11, RuleId.D12,
				RuleId.D13, RuleId.D14, RuleId.D15);
	}

	@Test
	void ruleClassesAreTheContractTable() {
		assertThat(List.of(RuleId.D1, RuleId.D2, RuleId.D3, RuleId.D4, RuleId.D5))
			.allSatisfy(id -> assertThat(id.failureClass()).isEqualTo(FailureClass.INVALID_REFERENCE));
		assertThat(RuleId.D6.failureClass()).isEqualTo(FailureClass.FABRICATED_EVIDENCE);
		assertThat(List.of(RuleId.D7, RuleId.D8, RuleId.D9, RuleId.D10, RuleId.D11, RuleId.D12, RuleId.D13, RuleId.D14,
				RuleId.D15))
			.allSatisfy(id -> assertThat(id.failureClass()).isEqualTo(FailureClass.DOMAIN_INVARIANT_VIOLATION));
	}

	@Test
	void aRuleSetWithARepeatedIdIsRejected() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new RuleBasedValidator(List.of(new UniqueIdsRule(), new UniqueIdsRule())))
			.withMessageContaining("D1");
	}

	@Test
	void aNewRuleIsANewStrategyInTheSet() {
		ValidationRule alwaysFails = new ValidationRule() {

			@Override
			public RuleId id() {
				return RuleId.D15;
			}

			@Override
			public List<Violation> check(AssessmentInputV1 input, AssessmentOutputV2 output) {
				return List.of(id().violation("$", "test rule"));
			}

		};
		List<ValidationRule> rules = new ArrayList<>(ContractV2Rules.rules());
		rules.removeIf(rule -> rule.id() == RuleId.D15);
		rules.add(alwaysFails);
		ValidationResult result = new RuleBasedValidator(rules).validate(validInput(),
				validOutput());
		assertThat(result.violations()).extracting(Violation::message).containsExactly("test rule");
	}

	@Test
	void collectsAllViolationsAndClassifiesByPrecedence() {
		// D13 (domain), D6 (fabricated) and D2 (reference) in one output.
		AssessmentOutputV2 output = investment(
				List.of(target("t1", "XTB", Direction.NOT_EXPRESSED, null, "c1")),
				List.of(claim("c1", Attribution.AUTHOR, "zmyślony cytat", "t1", "t9")));
		ValidationResult result = new RuleBasedValidator(ContractV2Rules.rules()).validate(validInput(), output);
		assertThat(result.violations()).extracting(Violation::rule).containsExactly("D2", "D6", "D13");
		assertThat(result.failureClass()).contains(FailureClass.INVALID_REFERENCE);
	}

	@Test
	void fabricatedEvidenceOutranksDomainViolations() {
		AssessmentOutputV2 output = investment(List.of(target("t1", "XTB", Direction.BULLISH, null, "c1")),
				List.of(claim("c1", Attribution.AUTHOR, "zmyślony cytat", "t1")));
		ValidationResult result = RuleBasedValidator.contractV2().validate(validInput(), output);
		assertThat(result.violations()).extracting(Violation::rule).containsExactly("D6", "D12");
		assertThat(result.failureClass()).contains(FailureClass.FABRICATED_EVIDENCE);
	}

}
