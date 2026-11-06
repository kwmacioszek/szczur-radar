package pl.szczurradar.api.pipeline.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

/** {@link RunEndingGuard} reports what is wrong on purpose: its red paths stay in the tests. */
class RunEndingGuardTest {

	@SuppressWarnings("unused")
	public static class Probe {

		public void ends() {
		}

		public void reads() {
		}

		public void newlyAdded() {
		}

		void notPublic() {
		}

	}

	/**
	 * Tests that never run in the unit suite. Nested static classes are not discovered by surefire (it skips
	 * {@code $} classes) and every method here is skipped or excluded anyway, with an empty body.
	 */
	@SuppressWarnings("unused")
	static class Fixture {

		@Test
		@Disabled("a fixture for RunEndingGuardTest")
		void disabledMethod() {
		}

		@Test
		@Tag("measurement")
		void measurementMethod() {
		}

		@Test
		@Tags({ @Tag("slow"), @Tag("measurement") })
		void measurementThroughTheContainer() {
		}

		@Test
		@Tag("slow")
		void taggedButRuns() {
		}

	}

	@SuppressWarnings("unused")
	@Disabled("a fixture for RunEndingGuardTest")
	static class DisabledClass {

		@Test
		void test() {
		}

	}

	@SuppressWarnings("unused")
	@Tag("measurement")
	static class MeasurementClass {

		@Test
		void test() {
		}

	}

	private static final String FIXTURE = "pl.szczurradar.api.pipeline.state.RunEndingGuardTest$";

	private static final String HERE = "pl.szczurradar.api.pipeline.state.RunEndingGuardTest#";

	@Test
	void aMethodOutsideTheClassificationIsReported() {
		List<String> problems = RunEndingGuard.unclassified(Probe.class, Set.of("ends"), Map.of("reads", "a read"));

		assertThat(problems).singleElement().asString().contains("newlyAdded").contains("not classified");
	}

	@Test
	void aFullyClassifiedClassHasNoProblem() {
		assertThat(RunEndingGuard.unclassified(Probe.class, Set.of("ends"),
				Map.of("reads", "a read", "newlyAdded", "adds nothing"))).isEmpty();
	}

	@Test
	void anEntryForAMethodTheClassNoLongerHasIsReported() {
		List<String> problems = RunEndingGuard.unclassified(Probe.class, Set.of("ends", "gone"),
				Map.of("reads", "a read", "newlyAdded", "x", "notPublic", "x"));

		assertThat(problems).hasSize(2).anyMatch(problem -> problem.contains("gone") && problem.contains("stale"))
			.anyMatch(problem -> problem.contains("notPublic") && problem.contains("stale"));
	}

	@Test
	void aMethodInBothSetsAndAReasonlessEntryAreReported() {
		List<String> problems = RunEndingGuard.unclassified(Probe.class, Set.of("ends"),
				Map.of("ends", "a read", "reads", " ", "newlyAdded", "x"));

		assertThat(problems).anyMatch(problem -> problem.contains("both"))
			.anyMatch(problem -> problem.contains("reads") && problem.contains("without a reason"));
	}

	@Test
	void aTestThatDoesNotExistIsReported() {
		Map<String, List<String>> tests = Map.of("ends", List.of(HERE + "noSuchTest"));

		assertThat(RunEndingGuard.missingCoverage(tests)).singleElement()
			.asString()
			.contains("does not exist")
			.contains("noSuchTest");
	}

	@Test
	void aMissingClassAnAMalformedReferenceAndANonTestAreReported() {
		Map<String, List<String>> tests = Map.of(
				"a", List.of("pl.szczurradar.NoSuchClass#test"),
				"b", List.of("withoutHash"),
				"c", List.of(HERE + "aMethodOutsideTheClassificationIsReported", "pl.szczurradar.api.pipeline.state"
						+ ".RunEndingGuardTest$Probe#ends"));

		assertThat(RunEndingGuard.missingCoverage(tests)).hasSize(3)
			.anyMatch(problem -> problem.startsWith("a:") && problem.contains("class"))
			.anyMatch(problem -> problem.startsWith("b:") && problem.contains("form"))
			.anyMatch(problem -> problem.startsWith("c:") && problem.contains("not a @Test"));
	}

	@Test
	void anEndingMethodWithoutTestsIsReportedAndRealTestsPass() {
		assertThat(RunEndingGuard.missingCoverage(Map.of("ends", List.of()))).singleElement()
			.asString()
			.contains("no test");
		assertThat(RunEndingGuard.missingCoverage(Map.of("ends", List.of(HERE + "aFullyClassifiedClassHasNoProblem"))))
			.isEmpty();
	}

	@Test
	void aDisabledTestAndATestOfAMeasurementTagAreReported() {
		Map<String, List<String>> tests = Map.of(
				"a", List.of(FIXTURE + "Fixture#disabledMethod"),
				"b", List.of(FIXTURE + "Fixture#measurementMethod"),
				"c", List.of(FIXTURE + "Fixture#measurementThroughTheContainer"),
				"d", List.of(FIXTURE + "DisabledClass#test"),
				"e", List.of(FIXTURE + "MeasurementClass#test"));

		assertThat(RunEndingGuard.missingCoverage(tests)).hasSize(5)
			.anyMatch(problem -> problem.startsWith("a:") && problem.contains("@Disabled"))
			.anyMatch(problem -> problem.startsWith("b:") && problem.contains("measurement"))
			.anyMatch(problem -> problem.startsWith("c:") && problem.contains("measurement"))
			.anyMatch(problem -> problem.startsWith("d:") && problem.contains("class") && problem.contains("@Disabled"))
			.anyMatch(problem -> problem.startsWith("e:") && problem.contains("class") && problem.contains("measurement"));
	}

	@Test
	void aTestWithAnotherTagRunsAndOneRunnableTestAmongTheReferencesIsEnough() {
		assertThat(RunEndingGuard.missingCoverage(Map.of("ends", List.of(FIXTURE + "Fixture#taggedButRuns")))).isEmpty();
		assertThat(RunEndingGuard.missingCoverage(
				Map.of("ends", List.of(FIXTURE + "Fixture#disabledMethod", HERE + "aFullyClassifiedClassHasNoProblem"))))
			.hasSize(1);
	}

	@Test
	void theProblemsFollowTheSortedMethodNames() {
		Map<String, List<String>> tests = Map.of("zeta", List.of(), "alpha", List.of(), "mid", List.of());

		assertThat(RunEndingGuard.missingCoverage(tests)).extracting(problem -> problem.split(" ")[0])
			.containsExactly("alpha", "mid", "zeta");
	}

}
