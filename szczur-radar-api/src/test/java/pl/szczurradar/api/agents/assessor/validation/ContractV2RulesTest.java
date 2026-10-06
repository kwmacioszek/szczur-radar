package pl.szczurradar.api.agents.assessor.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static pl.szczurradar.api.agents.assessor.ContractFixtures.BODY;
import static pl.szczurradar.api.agents.assessor.ContractFixtures.claim;
import static pl.szczurradar.api.agents.assessor.ContractFixtures.investment;
import static pl.szczurradar.api.agents.assessor.ContractFixtures.post;
import static pl.szczurradar.api.agents.assessor.ContractFixtures.target;
import static pl.szczurradar.api.agents.assessor.ContractFixtures.validInput;
import static pl.szczurradar.api.agents.assessor.ContractFixtures.validOutput;

import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import pl.szczurradar.api.agents.assessor.contract.Anchor;
import pl.szczurradar.api.agents.assessor.contract.Assertiveness;
import pl.szczurradar.api.agents.assessor.contract.AssessmentInputV1;
import pl.szczurradar.api.agents.assessor.contract.AssessmentOutputV2;
import pl.szczurradar.api.agents.assessor.contract.Attribution;
import pl.szczurradar.api.agents.assessor.contract.Context;
import pl.szczurradar.api.agents.assessor.contract.Direction;
import pl.szczurradar.api.agents.assessor.contract.Document;
import pl.szczurradar.api.agents.assessor.contract.Evidence;
import pl.szczurradar.api.agents.assessor.contract.Field;
import pl.szczurradar.api.agents.assessor.contract.Flag;
import pl.szczurradar.api.agents.assessor.contract.Modifier;
import pl.szczurradar.api.agents.assessor.contract.Parent;
import pl.szczurradar.api.agents.assessor.contract.Reference;
import pl.szczurradar.api.agents.assessor.contract.Relevance;

/**
 * Each domain rule D1–D15 of contract-v2.md §12 through the V2 validator: a negative case asserting
 * the rule and its class, and a positive case. Quote cases follow agent-implementation.md §5.
 */
class ContractV2RulesTest {

	private final RuleBasedValidator validator = RuleBasedValidator.contractV2();

	@Test
	void theBaseOutputIsValid() {
		assertThat(validator.validate(validInput(), validOutput()).violations()).isEmpty();
	}

	@Nested
	class D1UniqueIds {

		@Test
		void duplicateTargetIdIsInvalidReference() {
			AssessmentOutputV2 output = investment(
					List.of(target("t1", "XTB", Direction.NOT_EXPRESSED, null),
							target("t1", "CDR", Direction.NOT_EXPRESSED, null)),
					List.of());
			assertOnly(validInput(), output, RuleId.D1, "$.targets[1].id", FailureClass.INVALID_REFERENCE);
		}

		@Test
		void duplicateClaimIdIsInvalidReference() {
			AssessmentOutputV2 output = investment(List.of(),
					List.of(claim("c1", Attribution.AUTHOR, "XTB"), claim("c1", Attribution.AUTHOR, "CDR")));
			assertOnly(validInput(), output, RuleId.D1, "$.claims[1].id", FailureClass.INVALID_REFERENCE);
		}

		@Test
		void aTargetAndAClaimMayShareAnId() {
			AssessmentOutputV2 output = investment(List.of(target("x", "XTB", Direction.NOT_EXPRESSED, null)),
					List.of(claim("x", Attribution.AUTHOR, "XTB", "x")));
			assertValid(validInput(), output);
		}

	}

	@Nested
	class D2ClaimTargetsExist {

		@Test
		void missingTargetIsInvalidReference() {
			AssessmentOutputV2 output = investment(List.of(), List.of(claim("c1", Attribution.AUTHOR, "XTB", "t9")));
			assertOnly(validInput(), output, RuleId.D2, "$.claims[0].target_ids[0]", FailureClass.INVALID_REFERENCE);
		}

		@Test
		void generalClaimWithoutTargetsIsValid() {
			assertValid(validInput(), investment(List.of(), List.of(claim("c1", Attribution.AUTHOR, "XTB"))));
		}

	}

	@Nested
	class D3StanceClaimsExist {

		@Test
		void missingClaimIsInvalidReference() {
			AssessmentOutputV2 output = investment(
					List.of(target("t1", "XTB", Direction.UNCLEAR, null, "c9")), List.of());
			assertOnly(validInput(), output, RuleId.D3, "$.targets[0].stance.claim_ids[0]",
					FailureClass.INVALID_REFERENCE);
		}

		@Test
		void existingClaimIsValid() {
			assertValid(validInput(), investment(List.of(target("t1", "XTB", Direction.UNCLEAR, null, "c1")),
					List.of(claim("c1", Attribution.REPORTED, "XTB", "t1"))));
		}

	}

	@Nested
	class D4NoRepeatedReferences {

		@Test
		void repeatedTargetIdInClaimIsInvalidReference() {
			AssessmentOutputV2 output = investment(List.of(target("t1", "XTB", Direction.NOT_EXPRESSED, null)),
					List.of(claim("c1", Attribution.AUTHOR, "XTB", "t1", "t1")));
			assertOnly(validInput(), output, RuleId.D4, "$.claims[0].target_ids[1]", FailureClass.INVALID_REFERENCE);
		}

		@Test
		void repeatedClaimIdInStanceIsInvalidReference() {
			AssessmentOutputV2 output = investment(
					List.of(target("t1", "XTB", Direction.BULLISH, Assertiveness.HEDGED, "c1", "c1")),
					List.of(claim("c1", Attribution.AUTHOR, "XTB", "t1")));
			assertOnly(validInput(), output, RuleId.D4, "$.targets[0].stance.claim_ids[1]",
					FailureClass.INVALID_REFERENCE);
		}

		@Test
		void distinctReferencesAreValid() {
			assertValid(validInput(), investment(
					List.of(target("t1", "XTB", Direction.BULLISH, Assertiveness.HEDGED, "c1", "c2"),
							target("t2", "CDR", Direction.NOT_EXPRESSED, null)),
					List.of(claim("c1", Attribution.AUTHOR, "XTB", "t1", "t2"), claim("c2", Attribution.AUTHOR, "CDR", "t1"))));
		}

	}

	@Nested
	class D5QuoteReference {

		@Test
		void quoteFromNullFieldIsInvalidReference() {
			AssessmentOutputV2 output = investment(List.of(), List.of(claim("c1", Attribution.AUTHOR,
					new Evidence(Document.ITEM, Field.TITLE, "XTB"))));
			assertOnly(validInput(), output, RuleId.D5, "$.claims[0].evidence", FailureClass.INVALID_REFERENCE);
		}

		@Test
		void anchorFromMissingParentIsInvalidReference() {
			AssessmentOutputV2 output = investment(List.of(target("t1",
					new Anchor(Document.PARENT_POST, Field.BODY, "XTB"), Reference.CONTEXTUAL, Direction.NOT_EXPRESSED,
					null)), List.of());
			assertOnly(validInput(), output, RuleId.D5, "$.targets[0].anchor", FailureClass.INVALID_REFERENCE);
		}

		@Test
		void quoteFromTitleIsValidWhenTheTitleExists() {
			AssessmentOutputV2 output = investment(List.of(), List.of(claim("c1", Attribution.AUTHOR,
					new Evidence(Document.ITEM, Field.TITLE, "Wyniki XTB"))));
			assertValid(post("Wyniki XTB", BODY), output);
		}

	}

	@Nested
	class D6QuoteIsFragment {

		@Test
		void fabricatedQuoteIsFabricatedEvidence() {
			assertQuoteViolation("XTB po wynikach wystrzeli", RuleId.D6, FailureClass.FABRICATED_EVIDENCE);
		}

		@Test
		void changedInflectionIsFabricatedEvidence() {
			assertQuoteViolation("XTB po wyniku poleci w górę", RuleId.D6, FailureClass.FABRICATED_EVIDENCE);
		}

		@Test
		void ellipsisIsFabricatedEvidence() {
			assertQuoteViolation("XTB po wynikach [...] dokupuję", RuleId.D6, FailureClass.FABRICATED_EVIDENCE);
			assertQuoteViolation("XTB po wynikach ... dokupuję", RuleId.D6, FailureClass.FABRICATED_EVIDENCE);
		}

		@Test
		void changedCaseIsFabricatedEvidence() {
			assertQuoteViolation("xtb po wynikach poleci w górę", RuleId.D6, FailureClass.FABRICATED_EVIDENCE);
		}

		@Test
		void joinedFragmentsAreFabricatedEvidence() {
			assertQuoteViolation("XTB poleci w górę", RuleId.D6, FailureClass.FABRICATED_EVIDENCE);
		}

		@Test
		void quoteFromAnotherFieldIsFabricatedEvidence() {
			AssessmentOutputV2 output = investment(List.of(), List.of(claim("c1", Attribution.AUTHOR,
					new Evidence(Document.ITEM, Field.TITLE, "dokupuję"))));
			assertOnly(post("Wyniki XTB", BODY), output, RuleId.D6, "$.claims[0].evidence.quote",
					FailureClass.FABRICATED_EVIDENCE);
		}

		@Test
		void anchorOutsideTheFieldIsFabricatedEvidence() {
			AssessmentOutputV2 output = investment(List.of(target("t1", "PKO", Direction.NOT_EXPRESSED, null)),
					List.of());
			assertOnly(validInput(), output, RuleId.D6, "$.targets[0].anchor.quote", FailureClass.FABRICATED_EVIDENCE);
		}

		@Test
		void quoteDifferingOnlyInWhitespaceIsValid() {
			// The field has a line break and a hard space where the quote has plain spaces.
			AssessmentInputV1 input = post("szarpniecia małe, myślałem że realizacja\nzyskow");
			AssessmentOutputV2 output = investment(List.of(), List.of(claim("c1", Attribution.AUTHOR,
					"szarpniecia  małe, myślałem że realizacja zyskow ")));
			assertValid(input, output);
		}

		@Test
		void quoteWithHardSpaceAgainstPlainSpaceIsValid() {
			AssessmentOutputV2 output = investment(List.of(), List.of(claim("c1", Attribution.AUTHOR,
					"dokupuję.  Analityk")));
			assertValid(validInput(), output);
		}

	}

	@Nested
	class D7QuoteNotEmpty {

		@ParameterizedTest
		@ValueSource(strings = { " ", " \n", " " })
		void whitespaceOnlyQuoteIsDomainInvariantViolation(String quote) {
			// The field contains these characters, yet the quote is rejected (§10 pt 2) and not passed to D6.
			assertQuoteViolation(quote, RuleId.D7, FailureClass.DOMAIN_INVARIANT_VIOLATION);
		}

		@Test
		void oneCharacterQuoteIsValid() {
			assertValid(validInput(), investment(List.of(), List.of(claim("c1", Attribution.AUTHOR, "X"))));
		}

		@Test
		void whitespaceOnlyQuoteOfMissingFieldIsOnlyInvalidReference() {
			AssessmentOutputV2 output = investment(List.of(), List.of(claim("c1", Attribution.AUTHOR,
					new Evidence(Document.ITEM, Field.TITLE, " "))));
			assertOnly(validInput(), output, RuleId.D5, "$.claims[0].evidence", FailureClass.INVALID_REFERENCE);
		}

	}

	@Nested
	class D8EvidenceFromItem {

		@Test
		void evidenceFromParentIsDomainInvariantViolation() {
			AssessmentOutputV2 output = investment(List.of(), List.of(claim("c1", Attribution.AUTHOR,
					new Evidence(Document.PARENT_POST, Field.BODY, "rodzic"))));
			assertOnly(withParentPost(), output, RuleId.D8, "$.claims[0].evidence.document",
					FailureClass.DOMAIN_INVARIANT_VIOLATION);
		}

		@Test
		void evidenceFromItemIsValid() {
			assertValid(withParentPost(), validOutput());
		}

	}

	@Nested
	class D9AnchorFromItem {

		@ParameterizedTest
		@ValueSource(strings = { "EXPLICIT", "AMBIGUOUS", "UNRESOLVED" })
		void explicitAnchorOutsideItemIsDomainInvariantViolation(Reference reference) {
			AssessmentOutputV2 output = investment(List.of(target("t1",
					new Anchor(Document.PARENT_POST, Field.BODY, "rodzic"), reference, Direction.NOT_EXPRESSED, null)),
					List.of());
			assertOnly(withParentPost(), output, RuleId.D9, "$.targets[0].anchor.document",
					FailureClass.DOMAIN_INVARIANT_VIOLATION);
		}

		@ParameterizedTest
		@ValueSource(strings = { "EXPLICIT", "AMBIGUOUS", "UNRESOLVED" })
		void anchorFromItemIsValid(Reference reference) {
			assertValid(validInput(), investment(List.of(target("t1", new Anchor(Document.ITEM, Field.BODY, "XTB"),
					reference, Direction.NOT_EXPRESSED, null)), List.of()));
		}

	}

	@Nested
	class D10ContextualAnchorFromParent {

		@Test
		void contextualAnchorFromItemIsDomainInvariantViolation() {
			AssessmentOutputV2 output = investment(List.of(target("t1", new Anchor(Document.ITEM, Field.BODY, "XTB"),
					Reference.CONTEXTUAL, Direction.NOT_EXPRESSED, null)), List.of());
			assertOnly(validInput(), output, RuleId.D10, "$.targets[0].anchor.document",
					FailureClass.DOMAIN_INVARIANT_VIOLATION);
		}

		@Test
		void contextualAnchorFromParentIsValid() {
			assertValid(withParentPost(), investment(List.of(target("t1",
					new Anchor(Document.PARENT_POST, Field.BODY, "rodzic"), Reference.CONTEXTUAL, Direction.NOT_EXPRESSED,
					null)), List.of()));
		}

	}

	@Nested
	class D11StanceClaimsCoverTarget {

		@Test
		void claimWithoutTheTargetIsDomainInvariantViolation() {
			AssessmentOutputV2 output = investment(
					List.of(target("t1", "XTB", Direction.UNCLEAR, null, "c1"),
							target("t2", "CDR", Direction.NOT_EXPRESSED, null)),
					List.of(claim("c1", Attribution.AUTHOR, "CDR", "t2")));
			assertOnly(validInput(), output, RuleId.D11, "$.targets[0].stance.claim_ids[0]",
					FailureClass.DOMAIN_INVARIANT_VIOLATION);
		}

		@Test
		void claimWithTheTargetIsValid() {
			assertValid(validInput(), investment(List.of(target("t1", "XTB", Direction.UNCLEAR, null, "c1")),
					List.of(claim("c1", Attribution.AUTHOR, "XTB", "t1"))));
		}

	}

	@Nested
	class D12ToD14StanceTable {

		@Test
		void hedgedMixedIsDomainInvariantViolation() {
			AssessmentOutputV2 output = investment(List.of(target("t1", "XTB", Direction.MIXED, Assertiveness.HEDGED, "c1")),
					List.of(claim("c1", Attribution.AUTHOR, "XTB", "t1")));
			assertOnly(validInput(), output, RuleId.D12, "$.targets[0].stance.assertiveness",
					FailureClass.DOMAIN_INVARIANT_VIOLATION);
		}

		@Test
		void bullishWithoutAssertivenessIsDomainInvariantViolation() {
			AssessmentOutputV2 output = investment(List.of(target("t1", "XTB", Direction.BULLISH, null, "c1")),
					List.of(claim("c1", Attribution.AUTHOR, "XTB", "t1")));
			assertOnly(validInput(), output, RuleId.D12, "$.targets[0].stance.assertiveness",
					FailureClass.DOMAIN_INVARIANT_VIOLATION);
		}

		@Test
		void notExpressedWithClaimsIsDomainInvariantViolation() {
			AssessmentOutputV2 output = investment(List.of(target("t1", "XTB", Direction.NOT_EXPRESSED, null, "c1")),
					List.of(claim("c1", Attribution.AUTHOR, "XTB", "t1")));
			assertOnly(validInput(), output, RuleId.D13, "$.targets[0].stance.claim_ids",
					FailureClass.DOMAIN_INVARIANT_VIOLATION);
		}

		@Test
		void directionalStanceWithOnlyReportedClaimIsDomainInvariantViolation() {
			AssessmentOutputV2 output = investment(
					List.of(target("t1", "XTB", Direction.BEARISH, Assertiveness.UNHEDGED, "c1")),
					List.of(claim("c1", Attribution.REPORTED, "XTB", "t1")));
			assertOnly(validInput(), output, RuleId.D14, "$.targets[0].stance.claim_ids",
					FailureClass.DOMAIN_INVARIANT_VIOLATION);
		}

		@Test
		void directionalStanceWithAnAuthorClaimAmongOthersIsValid() {
			assertValid(validInput(), investment(
					List.of(target("t1", "XTB", Direction.BEARISH, Assertiveness.UNHEDGED, "c1", "c2")),
					List.of(claim("c1", Attribution.REPORTED, "XTB", "t1"), claim("c2", Attribution.AUTHOR, "CDR", "t1"))));
		}

		@Test
		void unclearMayRestOnAReportedClaimOrNone() {
			assertValid(validInput(), investment(List.of(target("t1", "XTB", Direction.UNCLEAR, null, "c1"),
					target("t2", "CDR", Direction.UNCLEAR, null)), List.of(claim("c1", Attribution.REPORTED, "XTB", "t1"))));
		}

	}

	@Nested
	class D15IrrelevantHasNoContent {

		@Test
		void irrelevantWithTargetsAndClaimsIsDomainInvariantViolation() {
			AssessmentOutputV2 output = new AssessmentOutputV2(Relevance.NOT_INVESTMENT_RELATED, List.of(),
					validOutput().targets(), validOutput().claims(), List.of());
			ValidationResult result = validator.validate(validInput(), output);
			assertThat(result.violations()).extracting(Violation::rule, Violation::path)
				.containsExactly(tuple("D15", "$.targets"),
						tuple("D15", "$.claims"));
			assertThat(result.failureClass()).contains(FailureClass.DOMAIN_INVARIANT_VIOLATION);
		}

		@Test
		void irrelevantWithModifiersAndFlagsIsValid() {
			assertValid(validInput(), new AssessmentOutputV2(Relevance.NOT_INVESTMENT_RELATED, List.of(Modifier.HUMOR),
					List.of(), List.of(), List.of(Flag.POSSIBLE_SARCASM)));
		}

		@Test
		void unclearWithTargetsIsValid() {
			assertValid(validInput(), new AssessmentOutputV2(Relevance.UNCLEAR, List.of(), validOutput().targets(),
					validOutput().claims(), List.of(Flag.MISSING_CONTEXT)));
		}

	}

	private static AssessmentInputV1 withParentPost() {
		return AssessmentInputV1.of(validInput().item(), new Context(new Parent(null, "rodzic pisze o XTB"), null));
	}

	private void assertQuoteViolation(String quote, RuleId rule, FailureClass failureClass) {
		AssessmentOutputV2 output = investment(List.of(), List.of(claim("c1", Attribution.AUTHOR, quote)));
		assertOnly(validInput(), output, rule, "$.claims[0].evidence.quote", failureClass);
	}

	private void assertOnly(AssessmentInputV1 input, AssessmentOutputV2 output, RuleId rule, String path,
			FailureClass failureClass) {
		ValidationResult result = validator.validate(input, output);
		assertThat(result.violations()).singleElement().satisfies(violation -> {
			assertThat(violation.rule()).isEqualTo(rule.name());
			assertThat(violation.path()).isEqualTo(path);
			assertThat(violation.failureClass()).isEqualTo(failureClass);
		});
		assertThat(result.failureClass()).contains(failureClass);
		assertThat(result.valid()).isFalse();
	}

	private void assertValid(AssessmentInputV1 input, AssessmentOutputV2 output) {
		ValidationResult result = validator.validate(input, output);
		assertThat(result.violations()).isEmpty();
		assertThat(result.failureClass()).isEmpty();
		assertThat(result.valid()).isTrue();
	}

}
