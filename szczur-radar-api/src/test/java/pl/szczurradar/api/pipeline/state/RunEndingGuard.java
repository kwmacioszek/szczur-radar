package pl.szczurradar.api.pipeline.state;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

/**
 * The pure part of the guard that keeps a run's ending and its notification together (R-23): which public
 * methods a class has against how they are classified, and whether the tests named for each method that ends
 * a run exist. {@code RunEndingTest} reads {@code RunRecorder} through it, {@code RunEndingGuardTest} feeds it
 * what is wrong on purpose. Every answer is a list of problems, empty when all is well.
 *
 * <p>A test is named {@code package.Class#method}. The guard proves the class, the method and its
 * {@code @Test} / {@code @ParameterizedTest} exist, not what the test checks.
 */
final class RunEndingGuard {

	private RunEndingGuard() {
	}

	/** The names of {@code type}'s public methods (an overload counts once), without synthetic ones. */
	static Set<String> publicMethods(Class<?> type) {
		return Arrays.stream(type.getDeclaredMethods())
			.filter(method -> Modifier.isPublic(method.getModifiers()) && !method.isSynthetic())
			.map(Method::getName)
			.collect(Collectors.toCollection(TreeSet::new));
	}

	/**
	 * What is wrong with the classification of {@code type}'s public methods: a method in neither
	 * {@code ends} nor {@code notEnding}, in both, a classified name {@code type} does not have (a stale
	 * entry), and a method that does not end a run without a reason.
	 */
	static List<String> unclassified(Class<?> type, Set<String> ends, Map<String, String> notEnding) {
		Set<String> methods = publicMethods(type);
		List<String> problems = new ArrayList<>();
		for (String method : methods) {
			if (!ends.contains(method) && !notEnding.containsKey(method)) {
				problems.add("public method " + type.getSimpleName() + "." + method + " is not classified: it either"
						+ " ends a run (then it needs tests that check the run's notification) or it does not");
			}
		}
		for (String method : ends) {
			if (notEnding.containsKey(method)) {
				problems.add(method + " is classified both as ending a run and as not ending one");
			}
		}
		for (String method : union(ends, notEnding.keySet())) {
			if (!methods.contains(method)) {
				problems.add("the classification names " + method + ", which " + type.getSimpleName()
						+ " has no public method of (stale entry)");
			}
		}
		notEnding.forEach((method, reason) -> {
			if (reason == null || reason.isBlank()) {
				problems.add(method + " is classified as not ending a run without a reason");
			}
		});
		return problems;
	}

	/**
	 * What is wrong with the tests named for each method that ends a run ({@code testsByMethod}: method → the
	 * references {@code package.Class#method}): a method without a test, a reference that is no reference, a
	 * class or a test method that does not exist, a method that is no test.
	 */
	static List<String> missingCoverage(Map<String, List<String>> testsByMethod) {
		List<String> problems = new ArrayList<>();
		testsByMethod.forEach((method, references) -> {
			if (references.isEmpty()) {
				problems.add(method + " ends a run and has no test that checks its notification");
			}
			for (String reference : references) {
				String problem = problemWith(reference);
				if (problem != null) {
					problems.add(method + ": " + problem);
				}
			}
		});
		return problems;
	}

	private static String problemWith(String reference) {
		int hash = reference.indexOf('#');
		if (hash <= 0 || hash == reference.length() - 1) {
			return reference + " is not of the form package.Class#method";
		}
		String className = reference.substring(0, hash);
		String methodName = reference.substring(hash + 1);
		Class<?> type;
		try {
			type = Class.forName(className, false, RunEndingGuard.class.getClassLoader());
		}
		catch (ClassNotFoundException ex) {
			return "the test class " + className + " does not exist";
		}
		List<Method> named = Arrays.stream(type.getDeclaredMethods())
			.filter(method -> method.getName().equals(methodName))
			.toList();
		if (named.isEmpty()) {
			return "the test " + reference + " does not exist";
		}
		if (named.stream().noneMatch(method -> method.isAnnotationPresent(Test.class)
				|| method.isAnnotationPresent(ParameterizedTest.class))) {
			return reference + " exists but is not a @Test or @ParameterizedTest";
		}
		return null;
	}

	private static Set<String> union(Set<String> first, Set<String> second) {
		Set<String> all = new TreeSet<>(first);
		all.addAll(second);
		return all;
	}

}
