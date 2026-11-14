package pl.szczurradar.api.mutation;

import java.util.regex.Pattern;

/**
 * The identity of a mutant across runs: the fields of {@code mutations.xml} without {@code lineNumber}, so an edit
 * elsewhere in the file keeps the registry valid. One key can occur several times in a report (the same mutation on
 * several lines of a method), so keys are compared as multisets.
 * <p>
 * The number javac gives a lambda's method ({@code lambda$name$N}) changes when a lambda is added before it, so the
 * key drops it from the method name and from the description (PIT names the method in some descriptions):
 * {@code lambda$cancel$0} is {@code lambda$cancel}.
 *
 * @param mutatedClass the binary name of the mutated class ({@code $} for a nested class)
 * @param mutatedMethod the mutated method ({@code <init>} for a constructor)
 * @param methodDescription the JVM descriptor of the method, which tells overloads apart
 * @param mutator PIT's mutator, by its full class name
 * @param description PIT's description of the mutation
 */
record MutantKey(String mutatedClass, String mutatedMethod, String methodDescription, String mutator,
		String description) {

	private static final Pattern LAMBDA_NUMBER = Pattern.compile("(lambda\\$[^$\\s:]+)\\$\\d+");

	MutantKey {
		mutatedMethod = withoutLambdaNumber(mutatedMethod);
		description = withoutLambdaNumber(description);
	}

	private static String withoutLambdaNumber(String text) {
		return LAMBDA_NUMBER.matcher(text).replaceAll("$1");
	}

}
