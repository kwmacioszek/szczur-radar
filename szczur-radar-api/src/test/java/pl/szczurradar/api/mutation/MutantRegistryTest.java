package pl.szczurradar.api.mutation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import pl.szczurradar.api.mutation.MutantRegistry.Comparison;

/**
 * The comparison of the guard {@link SurvivingMutantsTest} on fixtures cut from a real PIT report (some with an
 * edited status or line, said where), so the plain build proves it catches every kind of mismatch.
 */
class MutantRegistryTest {

	/** Relative to the module directory, where Maven runs the tests. */
	private static final Path FIXTURES = Path.of("src/test/resources/fixtures/pit");

	private static final String MUTATORS = "org.pitest.mutationtest.engine.gregor.mutators.";

	private static final String REASON = "równoważny: the fixture's mutant";

	/** {@code statuses.xml}: SURVIVED on line 72. */
	private static final MutantKey SURVIVED = new MutantKey("pl.szczurradar.api.agents.assessor.validation.StanceTable",
			"allowsClaimIdCount", "(I)Z", MUTATORS + "ConditionalsBoundaryMutator", "changed conditional boundary");

	/** {@code statuses.xml}: NO_COVERAGE on line 392. */
	private static final MutantKey NO_COVERAGE = new MutantKey("pl.szczurradar.api.pipeline.PipelineRunService",
			"execute", "(Ljava/util/UUID;)V", MUTATORS + "RemoveConditionalMutator_EQUAL_ELSE",
			"removed conditional - replaced equality check with false");

	/** {@code statuses.xml}: KILLED. */
	private static final MutantKey KILLED = new MutantKey("pl.szczurradar.api.agents.assessor.validation.StanceTable",
			"allowsAssertiveness", "(Lpl/szczurradar/api/agents/assessor/contract/Assertiveness;)Z",
			MUTATORS + "RemoveConditionalMutator_EQUAL_ELSE",
			"removed conditional - replaced equality check with false");

	/** {@code statuses.xml}: TIMED_OUT. */
	private static final MutantKey TIMED_OUT = new MutantKey("pl.szczurradar.api.pipeline.PipelineRunService",
			"guarded", "(Ljava/util/UUID;)V", MUTATORS + "VoidMethodCallMutator",
			"removed call to pl/szczurradar/api/pipeline/PipelineRunService::execute");

	/** {@code same-key.xml}: SURVIVED twice, on lines 258 and 261. */
	private static final MutantKey TWICE = new MutantKey("pl.szczurradar.api.pipeline.PipelineRunService",
			"cleanUpOrphans", "()V", MUTATORS + "RemoveConditionalMutator_EQUAL_ELSE",
			"removed conditional - replaced equality check with false");

	@Test
	void aSurvivedMutantWithoutAnEntryIsListedWithItsLine() {
		Comparison comparison = MutantRegistry.compare(read("statuses.xml"), List.of());

		assertThat(comparison.clean()).isFalse();
		assertThat(comparison.withoutEntry()).extracting(Mutant::key, Mutant::lineNumber, Mutant::status)
			.contains(tuple(SURVIVED, 72, MutantStatus.SURVIVED));
	}

	@Test
	void aMutantWithoutCoverageIsSurvivingTooAndDetectedOnesAreNot() {
		Comparison comparison = MutantRegistry.compare(read("statuses.xml"), List.of());

		assertThat(comparison.withoutEntry()).extracting(Mutant::key, Mutant::lineNumber, Mutant::status)
			.containsExactly(tuple(SURVIVED, 72, MutantStatus.SURVIVED),
					tuple(NO_COVERAGE, 392, MutantStatus.NO_COVERAGE));
		assertThat(comparison.superfluous()).isEmpty();
	}

	@Test
	void survivingMutantsWithTheirEntriesPass() {
		Comparison comparison = MutantRegistry.compare(read("statuses.xml"),
				List.of(entry(SURVIVED), entry(NO_COVERAGE)));

		assertThat(comparison.clean()).isTrue();
		assertThat(comparison.message()).isEmpty();
	}

	@Test
	void anEntryWhoseMutantIsKilledOrTimedOutIsSuperfluous() {
		List<AcceptedMutant> entries = List.of(entry(SURVIVED), entry(NO_COVERAGE), entry(KILLED), entry(TIMED_OUT));

		Comparison comparison = MutantRegistry.compare(read("statuses.xml"), entries);

		assertThat(comparison.withoutEntry()).isEmpty();
		assertThat(comparison.superfluous()).containsExactly(entry(KILLED), entry(TIMED_OUT));
		assertThat(comparison.clean()).isFalse();
	}

	@Test
	void twoSurvivingMutantsOfOneKeyNeedTwoEntries() {
		List<Mutant> mutants = read("same-key.xml");

		Comparison one = MutantRegistry.compare(mutants, List.of(entry(TWICE)));
		Comparison two = MutantRegistry.compare(mutants, List.of(entry(TWICE), entry(TWICE)));
		Comparison three = MutantRegistry.compare(mutants, List.of(entry(TWICE), entry(TWICE), entry(TWICE)));

		assertThat(one.withoutEntry()).extracting(Mutant::key).containsExactly(TWICE);
		assertThat(one.superfluous()).isEmpty();
		assertThat(two.clean()).isTrue();
		assertThat(three.withoutEntry()).isEmpty();
		assertThat(three.superfluous()).containsExactly(entry(TWICE));
	}

	/** {@code moved-line.xml} is the SURVIVED mutant of {@code statuses.xml} with its line edited from 72 to 84. */
	@Test
	void theSameKeyOnAnotherLineStillMatches() {
		List<Mutant> moved = read("moved-line.xml");

		assertThat(moved).extracting(Mutant::lineNumber).containsExactly(84);
		assertThat(MutantRegistry.compare(moved, List.of(entry(SURVIVED))).clean()).isTrue();
	}

	/** {@code lambda.xml} is a real mutant of {@code lambda$cancel$0}, renumbered {@code lambda$cancel$3}. */
	@Test
	void aLambdaWithAnotherNumberMatchesItsEntryWithoutTheNumber() {
		MutantKey lambda = new MutantKey("pl.szczurradar.api.pipeline.assess.ConcurrentAssessmentDispatcher",
				"lambda$cancel", "(Ljava/util/concurrent/Future;)Z", MUTATORS + "returns.BooleanTrueReturnValsMutator",
				"replaced boolean return with true for"
						+ " pl/szczurradar/api/pipeline/assess/ConcurrentAssessmentDispatcher::lambda$cancel");
		List<Mutant> mutants = read("lambda.xml");

		assertThat(mutants).extracting(mutant -> mutant.key().mutatedMethod()).containsExactly("lambda$cancel");
		assertThat(MutantRegistry.compare(mutants, List.of(entry(lambda))).clean()).isTrue();
		assertThat(new MutantKey(lambda.mutatedClass(), "lambda$cancel$0", lambda.methodDescription(),
				lambda.mutator(), lambda.description().replace("lambda$cancel", "lambda$cancel$0")))
			.isEqualTo(lambda);
	}

	/**
	 * {@code overload.xml} is a real mutant of {@code AssessmentQuality.status(JJI)}, its status edited from KILLED to
	 * SURVIVED; {@code status(JJII)} is the overload next to it.
	 */
	@Test
	void theSameMethodNameWithAnotherDescriptorDoesNotMatch() {
		String description = "replaced return value with null for pl/szczurradar/api/posts/AssessmentQuality::status";
		MutantKey threeArguments = new MutantKey("pl.szczurradar.api.posts.AssessmentQuality", "status",
				"(JJI)Lpl/szczurradar/api/posts/QualityStatus;", MUTATORS + "returns.NullReturnValsMutator",
				description);
		MutantKey fourArguments = new MutantKey("pl.szczurradar.api.posts.AssessmentQuality", "status",
				"(JJII)Lpl/szczurradar/api/posts/QualityStatus;", MUTATORS + "returns.NullReturnValsMutator",
				description);
		List<Mutant> mutants = read("overload.xml");

		Comparison other = MutantRegistry.compare(mutants, List.of(entry(fourArguments)));

		assertThat(other.withoutEntry()).extracting(Mutant::key).containsExactly(threeArguments);
		assertThat(other.superfluous()).containsExactly(entry(fourArguments));
		assertThat(MutantRegistry.compare(mutants, List.of(entry(threeArguments))).clean()).isTrue();
	}

	@Test
	void theMessageListsEachMutantAsAnEntryToPasteAndEachSuperfluousEntry() {
		Comparison comparison = MutantRegistry.compare(read("statuses.xml"), List.of(entry(KILLED)));

		assertThat(comparison.message()).contains("2 surviving mutants without an entry in AcceptedMutants")
			.contains("// line 72, SURVIVED\n\t\tAcceptedMutant.of(\"pl.szczurradar.api.agents.assessor.validation"
					+ ".StanceTable\", \"allowsClaimIdCount\",\n\t\t\t\t\"(I)Z\",")
			.contains("// line 392, NO_COVERAGE\n")
			.contains("\"" + Comparison.REASON_TO_WRITE + "\"),")
			.contains("1 entries in AcceptedMutants without a surviving mutant")
			.contains("\"allowsAssertiveness\"");
		assertThat(MutantRegistry.invalidReasons(List.of(new AcceptedMutant(SURVIVED, Comparison.REASON_TO_WRITE))))
			.as("a pasted entry fails until its reason is written")
			.hasSize(1);
	}

	@Test
	void aMessageEntryEscapesQuotesAsJava() {
		MutantKey quoted = new MutantKey("pl.szczurradar.api.agents.assessor.contract.Relevance", "value",
				"()Ljava/lang/String;", MUTATORS + "returns.EmptyObjectReturnValsMutator",
				"replaced return value with \"\" for pl/szczurradar/api/agents/assessor/contract/Relevance::value");

		assertThat(MutantRegistry.javaEntry(quoted, REASON))
			.contains("\"replaced return value with \\\"\\\" for pl/szczurradar/api/agents/assessor/contract/Relevance"
					+ "::value\"");
	}

	@Test
	void anEmptyReasonIsAnError() {
		assertThat(MutantRegistry.invalidReasons(List.of(new AcceptedMutant(SURVIVED, ""))))
			.singleElement(STRING)
			.contains("no reason");
		assertThat(MutantRegistry.invalidReasons(List.of(new AcceptedMutant(SURVIVED, "  ")))).hasSize(1);
	}

	@Test
	void aReasonWithoutItsCategoryIsAnError() {
		assertThat(MutantRegistry.invalidReasons(List.of(new AcceptedMutant(SURVIVED, "the boundary is never hit"))))
			.singleElement(STRING)
			.contains("starts with none of the categories równoważny:, obronny:, tekst:");
		assertThat(MutantRegistry.invalidReasons(List.of(new AcceptedMutant(SURVIVED, " równoważny: leading space"),
				new AcceptedMutant(SURVIVED, "Równoważny: capital"), new AcceptedMutant(SURVIVED, "równoważny:"),
				new AcceptedMutant(SURVIVED, "obronny:  "))))
			.hasSize(4);
	}

	@Test
	void aReasonWithItsCategoryIsValid() {
		assertThat(MutantRegistry.invalidReasons(List.of(new AcceptedMutant(SURVIVED, "równoważny: x >= 1 and x > 0"
				+ " differ only at 0, which the caller never passes"),
				new AcceptedMutant(SURVIVED, "obronny: Prefilter rules it out"),
				new AcceptedMutant(SURVIVED, "tekst: the exception's message"))))
			.isEmpty();
	}

	@Test
	void everyEntryOfTheRegistryHasAReasonWithItsCategory() {
		assertThat(MutantRegistry.invalidReasons(AcceptedMutants.entries())).isEmpty();
	}

	@Test
	void aReportWithADocumentTypeIsRefused(@TempDir Path directory) throws IOException {
		Path report = directory.resolve("mutations.xml");
		Files.writeString(report, """
				<?xml version="1.0" encoding="UTF-8"?>
				<!DOCTYPE mutations [<!ENTITY secret SYSTEM "file:///etc/hosts">]>
				<mutations><mutation status='SURVIVED'><description>&secret;</description></mutation></mutations>
				""");

		assertThatThrownBy(() -> MutationReport.read(report)).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Cannot read the PIT report");
	}

	@Test
	void anUnknownStatusIsAnError(@TempDir Path directory) throws IOException {
		Path report = directory.resolve("mutations.xml");
		Files.writeString(report, Files.readString(FIXTURES.resolve("moved-line.xml"))
			.replace("status='SURVIVED'", "status='STARTED'"));

		assertThatThrownBy(() -> MutationReport.read(report)).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("unknown status 'STARTED'");
	}

	private static List<Mutant> read(String fixture) {
		return MutationReport.read(FIXTURES.resolve(fixture));
	}

	private static AcceptedMutant entry(MutantKey key) {
		return new AcceptedMutant(key, REASON);
	}

}
