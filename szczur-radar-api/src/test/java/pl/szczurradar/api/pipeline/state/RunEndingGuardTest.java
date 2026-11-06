package pl.szczurradar.api.pipeline.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;

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

}
