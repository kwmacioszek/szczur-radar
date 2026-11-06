package pl.szczurradar.api.pipeline.state;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

/**
 * The pure part of the guard that keeps a run's ending and its notification together (R-23): which public
 * methods a class has against how they are classified, and whether the tests named for each method that ends
 * a run exist. {@code RunEndingTest} reads {@code RunRecorder} through it, {@code RunEndingGuardTest} feeds it
 * what is wrong on purpose. Every answer is a list of problems, empty when all is well.
 *
 * <p>A test is named {@code package.Class#method}. The guard proves the class, the method and its
 * {@code @Test} / {@code @ParameterizedTest} exist and run in the unit suite (not {@code @Disabled}, not tagged
 * {@code measurement}), not what the test checks.
 *
 * <p>Limit: only the methods {@code type} declares itself are seen ({@code getDeclaredMethods}): public methods
 * inherited from a superclass and non-public ending methods are outside the guard ({@code RunRecorder} has no
 * superclass today).
 */
final class RunEndingGuard {

	/** The JUnit tag of live runs that the unit run excludes ({@code surefire.excludedGroups}). */
	private static final String MEASUREMENT = "measurement";

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
		new TreeMap<>(testsByMethod).forEach((method, references) -> {
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
		List<Method> tests = named.stream()
			.filter(method -> method.isAnnotationPresent(Test.class) || method.isAnnotationPresent(ParameterizedTest.class))
			.toList();
		if (tests.isEmpty()) {
			return reference + " exists but is not a @Test or @ParameterizedTest";
		}
		String classReason = notRun(type);
		if (classReason != null) {
			return reference + " never runs in the unit suite: its class is " + classReason;
		}
		String methodReason = null;
		for (Method test : tests) {
			methodReason = notRun(test);
			if (methodReason == null) {
				return null;
			}
		}
		return reference + " never runs in the unit suite: it is " + methodReason;
	}

	/** Why {@code element} (a test method or a class, with the classes around it) does not run in CI; null when it does. */
	private static String notRun(AnnotatedElement element) {
		Class<?> type = element instanceof Class<?> declared ? declared : null;
		AnnotatedElement current = element;
		while (current != null) {
			if (current.isAnnotationPresent(Disabled.class)) {
				return "@Disabled";
			}
			if (Arrays.stream(current.getAnnotationsByType(Tag.class)).anyMatch(tag -> tag.value().equals(MEASUREMENT))) {
				return "tagged \"" + MEASUREMENT + "\" (excluded from the unit run, pom.xml)";
			}
			if (type == null && current instanceof Method method) {
				type = method.getDeclaringClass();
				current = type;
			}
			else if (type != null) {
				type = type.getEnclosingClass();
				current = type;
			}
			else {
				current = null;
			}
		}
		return null;
	}

	private static Set<String> union(Set<String> first, Set<String> second) {
		Set<String> all = new TreeSet<>(first);
		all.addAll(second);
		return all;
	}

}
