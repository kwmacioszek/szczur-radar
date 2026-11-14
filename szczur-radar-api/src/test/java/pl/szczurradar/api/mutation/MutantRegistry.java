package pl.szczurradar.api.mutation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compares the surviving mutants of a PIT report with the entries of the registry, both ways and as multisets:
 * each entry answers for one surviving mutant of its key.
 */
final class MutantRegistry {

	private static final Comparator<Mutant> BY_PLACE = Comparator
		.comparing((Mutant mutant) -> mutant.key().mutatedClass())
		.thenComparingInt(Mutant::lineNumber)
		.thenComparing(mutant -> mutant.key().mutatedMethod())
		.thenComparing(mutant -> mutant.key().mutator());

	private MutantRegistry() {
	}

	/**
	 * The surviving mutants of {@code mutants} no entry answers for, and the entries of {@code entries} without a
	 * surviving mutant of their key (killed, timed out, gone).
	 */
	static Comparison compare(List<Mutant> mutants, List<AcceptedMutant> entries) {
		Map<MutantKey, Deque<Mutant>> surviving = new LinkedHashMap<>();
		for (Mutant mutant : mutants) {
			if (mutant.surviving()) {
				surviving.computeIfAbsent(mutant.key(), key -> new ArrayDeque<>()).add(mutant);
			}
		}
		List<AcceptedMutant> superfluous = new ArrayList<>();
		for (AcceptedMutant entry : entries) {
			Deque<Mutant> left = surviving.get(entry.key());
			if (left == null || left.isEmpty()) {
				superfluous.add(entry);
			}
			else {
				left.removeFirst();
			}
		}
		List<Mutant> withoutEntry = surviving.values().stream().flatMap(Collection::stream).sorted(BY_PLACE).toList();
		return new Comparison(withoutEntry, List.copyOf(superfluous));
	}

	/** Each entry whose reason is empty, has no {@link ReasonCategory} or nothing after it, with what is wrong. */
	static List<String> invalidReasons(List<AcceptedMutant> entries) {
		return entries.stream()
			.flatMap(entry -> entry.reasonProblem()
				.map(problem -> problem + "\n" + javaEntry(entry.key(), entry.reason()))
				.stream())
			.toList();
	}

	/** The entry as Java source, ready to paste into {@link AcceptedMutants}. */
	static String javaEntry(MutantKey key, String reason) {
		String next = ",\n\t\t\t\t";
		return "\t\tAcceptedMutant.of(" + literal(key.mutatedClass()) + ", " + literal(key.mutatedMethod()) + next
				+ literal(key.methodDescription()) + next + literal(key.mutator()) + next + literal(key.description())
				+ next + literal(reason) + "),";
	}

	private static String literal(String text) {
		return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}

	/**
	 * The result of {@link #compare}.
	 *
	 * @param withoutEntry the surviving mutants no entry answers for, by class and line
	 * @param superfluous the entries without a surviving mutant of their key, in the registry's order
	 */
	record Comparison(List<Mutant> withoutEntry, List<AcceptedMutant> superfluous) {

		/** The reason a pasted entry starts with: it names no category, so it fails until it is written. */
		static final String REASON_TO_WRITE = "<równoważny:|obronny:|tekst:> why no test kills it";

		boolean clean() {
			return withoutEntry.isEmpty() && superfluous.isEmpty();
		}

		/** Every surviving mutant without an entry as an entry to paste, then every superfluous entry. */
		String message() {
			StringBuilder message = new StringBuilder();
			if (!withoutEntry.isEmpty()) {
				message.append(withoutEntry.size())
					.append(" surviving mutants without an entry in AcceptedMutants. Kill each with a test of behavior,"
							+ " or paste its entry into AcceptedMutants.ENTRIES with a reason starting with")
					.append(" równoważny:, obronny: or tekst:\n\n");
				for (Mutant mutant : withoutEntry) {
					message.append("\t\t// line ")
						.append(mutant.lineNumber())
						.append(", ")
						.append(mutant.status())
						.append('\n')
						.append(javaEntry(mutant.key(), REASON_TO_WRITE))
						.append('\n');
				}
			}
			if (!superfluous.isEmpty()) {
				message.append(message.isEmpty() ? "" : "\n")
					.append(superfluous.size())
					.append(" entries in AcceptedMutants without a surviving mutant (killed, timed out or gone)."
							+ " Remove them:\n\n");
				for (AcceptedMutant entry : superfluous) {
					message.append(javaEntry(entry.key(), entry.reason())).append('\n');
				}
			}
			return message.toString();
		}

	}

}
