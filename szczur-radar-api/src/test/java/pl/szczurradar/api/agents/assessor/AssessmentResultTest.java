package pl.szczurradar.api.agents.assessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import pl.szczurradar.api.agents.assessor.AssessmentResult.Status;
import pl.szczurradar.api.agents.assessor.contract.AssessmentOutputV2;
import pl.szczurradar.api.agents.assessor.validation.FailureClass;
import pl.szczurradar.api.agents.assessor.validation.Violation;

/**
 * The invariants of {@link AssessmentResult}: an {@code ok} result has an output and no failure, a {@code failed}
 * one a failure class, violations and no output. Each part of either rule is refused on its own.
 */
class AssessmentResultTest {

	private static final Provenance PROVENANCE = Provenance.prefilter("V2", Instant.parse("2026-10-04T10:00:00Z"));

	private static final AssessmentOutputV2 OUTPUT = ContractFixtures.validOutput();

	private static final List<Violation> VIOLATIONS = List
		.of(new Violation(FailureClass.SCHEMA_VIOLATION, "schema:enum", "$.relevance", "not in the enum"));

	@Test
	void anOkResultWithAnOutputAndNoFailureIsAccepted() {
		AssessmentResult result = new AssessmentResult(Status.OK, OUTPUT, null, List.of(), PROVENANCE, null);

		assertThat(result.ok()).isTrue();
		assertThat(result.callFailed()).isFalse();
	}

	@Test
	void anOkResultWithoutAnOutputIsRefused() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new AssessmentResult(Status.OK, null, null, List.of(), PROVENANCE, null))
			.withMessage("an ok result has an output and no failure");
	}

	@Test
	void anOkResultWithAFailureClassIsRefused() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new AssessmentResult(Status.OK, OUTPUT, FailureClass.SCHEMA_VIOLATION, List.of(),
					PROVENANCE, null))
			.withMessage("an ok result has an output and no failure");
	}

	@Test
	void anOkResultWithAViolationIsRefused() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new AssessmentResult(Status.OK, OUTPUT, null, VIOLATIONS, PROVENANCE, null))
			.withMessage("an ok result has an output and no failure");
	}

	@Test
	void aFailedResultWithAClassAndViolationsAndNoOutputIsAccepted() {
		AssessmentResult result = new AssessmentResult(Status.FAILED, null, FailureClass.SCHEMA_VIOLATION, VIOLATIONS,
				PROVENANCE, "{}");

		assertThat(result.ok()).isFalse();
		assertThat(result.callFailed()).isFalse();
	}

	@Test
	void aFailedResultWithAnOutputIsRefused() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new AssessmentResult(Status.FAILED, OUTPUT, FailureClass.SCHEMA_VIOLATION, VIOLATIONS,
					PROVENANCE, null))
			.withMessage("a failed result has a failure class, violations and no output");
	}

	@Test
	void aFailedResultWithoutAFailureClassIsRefused() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new AssessmentResult(Status.FAILED, null, null, VIOLATIONS, PROVENANCE, null))
			.withMessage("a failed result has a failure class, violations and no output");
	}

	@Test
	void aFailedResultWithoutViolationsIsRefused() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new AssessmentResult(Status.FAILED, null, FailureClass.SCHEMA_VIOLATION, List.of(),
					PROVENANCE, null))
			.withMessage("a failed result has a failure class, violations and no output");
	}

}
