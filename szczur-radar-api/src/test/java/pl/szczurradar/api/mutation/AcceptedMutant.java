package pl.szczurradar.api.mutation;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * An entry of {@link AcceptedMutants}: a surviving mutant accepted with its reason.
 *
 * @param key the mutant's identity
 * @param reason why no test kills it, starting with its {@link ReasonCategory}
 */
record AcceptedMutant(MutantKey key, String reason) {

	/** An entry as {@link AcceptedMutants} writes it and the guard prints it. */
	static AcceptedMutant of(String mutatedClass, String mutatedMethod, String methodDescription, String mutator,
			String description, String reason) {
		return new AcceptedMutant(new MutantKey(mutatedClass, mutatedMethod, methodDescription, mutator, description),
				reason);
	}

	/** What is wrong with the reason, if anything: none, no category, or nothing after the category. */
	Optional<String> reasonProblem() {
		if (reason.isBlank()) {
			return Optional.of("no reason");
		}
		Optional<ReasonCategory> category = ReasonCategory.of(reason);
		if (category.isEmpty()) {
			return Optional.of("the reason starts with none of the categories " + prefixes() + ": " + reason);
		}
		if (reason.substring(category.get().prefix().length()).isBlank()) {
			return Optional.of("the reason names only its category: " + reason);
		}
		return Optional.empty();
	}

	private static String prefixes() {
		return Arrays.stream(ReasonCategory.values()).map(ReasonCategory::prefix).collect(Collectors.joining(", "));
	}

}
