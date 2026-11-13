package pl.szczurradar.api.mutation;

import java.util.List;

/**
 * The surviving mutants of the core accepted with a reason (S-45). PIT mutates the core's classes (the list is in the
 * profile {@code mutation} of {@code pom.xml}) and {@code SurvivingMutantsTest} compares its report with this
 * registry after every {@code ./mvnw -Pmutation test}.
 * <p>
 * The rule: a mutant that survives ({@code SURVIVED} or {@code NO_COVERAGE}) is killed by a test of behavior, or it
 * gets an entry here with a concrete reason starting with its category:
 * <ul>
 * <li>{@code równoważny:} — why the behavior does not change;</li>
 * <li>{@code obronny:} — a branch the input contract makes unreachable, naming the place that rules it out;</li>
 * <li>{@code tekst:} — the mutant changes only the text of a message that is no contract.</li>
 * </ul>
 * An entry whose mutant no longer survives (killed, timed out, gone) fails the guard, so the registry does not rot.
 * <p>
 * An entry is the mutant's identity without its line ({@link MutantKey}); the guard prints a surviving mutant without
 * an entry as an entry ready to paste, its line in a comment. One entry answers for one mutant: the same key on two
 * lines needs two entries. A lambda's method is written without its number ({@code lambda$name}, not
 * {@code lambda$name$0}), also in the description.
 */
final class AcceptedMutants {

	/** The entries; an array, so each pasted entry may end with a comma. */
	private static final AcceptedMutant[] ENTRIES = {
	};

	private AcceptedMutants() {
	}

	static List<AcceptedMutant> entries() {
		return List.of(ENTRIES);
	}

}
