package pl.szczurradar.api.persistence;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.ResolvableType;
import org.springframework.core.type.filter.AnnotationTypeFilter;

/**
 * What the converters of the application really write for each enum constant, and how that compares with the
 * values a column's {@code CHECK} allows. Pure logic: {@link ClosedSetsIT} reads the world (the classpath and the
 * migrated schema) and calls it; {@code ClosedSetsGuardTest} calls it with deliberately wrong converters.
 */
final class ClosedSetsGuard {

	/** A string literal of a {@code CHECK} with the column it is compared with. */
	record LiteralPair(String column, String literal) {
	}

	/** Any string literal as {@code pg_get_constraintdef} prints it: {@code 'x'::text}. */
	static final Pattern ANY_LITERAL = Pattern.compile("'(?:[^']|'')*'::");

	private static final String LITERAL = "'(?:[^']|'')*'::text";

	private static final String LITERALS = LITERAL + "(?:, " + LITERAL + ")*";

	/** {@code status = 'x'::text}, {@code (status)::text <> 'x'::text}. */
	private static final Pattern COMPARED = Pattern
		.compile("(\\w+)\\)?(?:::text)? (?:=|<>) ('(?:[^']|'')*'::text)");

	/** {@code x = ANY (ARRAY[...])}, {@code x <> ALL (ARRAY[...])}, {@code x <@ ARRAY[...]}. */
	private static final Pattern IN_ARRAY = Pattern
		.compile("(\\w+)\\)?(?:::text)? (?:= ANY \\(|<> ALL \\(|<@ )ARRAY\\[(" + LITERALS + ")\\]");

	private static final Pattern VALUE = Pattern.compile("'((?:[^']|'')*)'::text");

	private ClosedSetsGuard() {
	}

	/**
	 * Every {@code column <op> 'literal'} of a {@code CHECK} definition, in the forms Postgres prints (a
	 * comparison, {@code <> ALL (ARRAY[...])}, {@code = ANY (ARRAY[...])}, {@code <@ ARRAY[...]}); a pattern
	 * ({@code ~}) or a column-to-column comparison has no pair.
	 */
	static List<LiteralPair> literalPairs(String definition) {
		List<LiteralPair> pairs = new ArrayList<>();
		Matcher compared = COMPARED.matcher(definition);
		while (compared.find()) {
			pairs.add(new LiteralPair(compared.group(1), unquote(compared.group(2))));
		}
		Matcher inArray = IN_ARRAY.matcher(definition);
		while (inArray.find()) {
			Matcher value = VALUE.matcher(inArray.group(2));
			while (value.find()) {
				pairs.add(new LiteralPair(inArray.group(1), value.group(1).replace("''", "'")));
			}
		}
		return pairs;
	}

	/** How many string literals {@code definition} holds, to compare with the pairs read from it. */
	static int literalCount(String definition) {
		Matcher value = VALUE.matcher(definition);
		int count = 0;
		while (value.find()) {
			count++;
		}
		return count;
	}

	/**
	 * What is wrong with the literals of the compound {@code CHECK} {@code name} on {@code table}: a literal the
	 * parser did not pair with a column (a form it cannot read must be red, not skipped), a literal on a column
	 * no registered enum is stored in, or one that is not a value of that enum. {@code valuesByColumn} is
	 * {@code table.column} to the values the application writes there. Empty when it is right.
	 */
	static List<String> unknownLiterals(String name, String table, String definition,
			Map<String, Set<String>> valuesByColumn) {
		List<String> problems = new ArrayList<>();
		List<LiteralPair> pairs = literalPairs(definition);
		if (pairs.size() != literalCount(definition)) {
			problems.add(name + " on " + table + ": " + literalCount(definition) + " literals, " + pairs.size()
					+ " read as column-literal pairs: " + definition);
		}
		for (LiteralPair pair : pairs) {
			Set<String> allowed = valuesByColumn.get(table + "." + pair.column());
			if (allowed == null) {
				problems.add(name + " on " + table + ": '" + pair.literal() + "' is compared with " + pair.column()
						+ ", a column no registered enum is stored in");
			}
			else if (!allowed.contains(pair.literal())) {
				problems.add(name + " on " + table + ": '" + pair.literal() + "' is not a value of " + table + "."
						+ pair.column() + " " + new TreeSet<>(allowed));
			}
		}
		return problems;
	}

	/**
	 * What is wrong with the named exceptions ({@code CHECK} name to the reason it is not read): no reason, a
	 * {@code CHECK} the schema no longer has, or one that no longer has a literal to except or now reads as a
	 * list of values. {@code definitions} is {@code CHECK} name to definition.
	 */
	static List<String> exceptionProblems(Map<String, String> exceptions, Map<String, String> definitions,
			Predicate<String> readsAsValueList) {
		List<String> problems = new ArrayList<>();
		exceptions.forEach((name, reason) -> {
			String definition = definitions.get(name);
			if (reason == null || reason.isBlank()) {
				problems.add(name + ": the exception has no reason");
			}
			if (definition == null) {
				problems.add(name + ": the schema no longer has this CHECK");
			}
			else if (!ANY_LITERAL.matcher(definition).find()) {
				problems.add(name + ": the CHECK has no literal any more, drop the exception");
			}
			else if (readsAsValueList.test(definition)) {
				problems.add(name + ": the CHECK reads as a list of values now, drop the exception");
			}
		});
		return problems;
	}

	private static String unquote(String literal) {
		return literal.substring(1, literal.length() - "'::text".length()).replace("''", "'");
	}

	/** Every {@code @Converter} of {@code pl.szczurradar.api} by the enum it converts, created through its own constructor. */
	static Map<Class<?>, AttributeConverter<Object, String>> convertersByEnum() {
		ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
		scanner.addIncludeFilter(new AnnotationTypeFilter(Converter.class));
		Map<Class<?>, AttributeConverter<Object, String>> converters = new LinkedHashMap<>();
		for (BeanDefinition candidate : scanner.findCandidateComponents("pl.szczurradar.api")) {
			Class<?> converterType = classOf(candidate);
			Class<?> enumType = ResolvableType.forClass(converterType).as(AttributeConverter.class).getGeneric(0).resolve();
			if (enumType == null || !enumType.isEnum()) {
				continue;
			}
			AttributeConverter<Object, String> previous = converters.put(enumType, instance(converterType));
			if (previous != null) {
				throw new IllegalStateException("two converters for " + enumType.getName() + ": "
						+ previous.getClass().getName() + " and " + converterType.getName());
			}
		}
		return converters;
	}

	/** The values {@code converter} writes for every constant of {@code enumType}. */
	static Set<String> writtenValues(Class<?> enumType, AttributeConverter<Object, String> converter) {
		return Arrays.stream(enumType.getEnumConstants())
			.map(converter::convertToDatabaseColumn)
			.collect(Collectors.toCollection(TreeSet::new));
	}

	/**
	 * What is wrong with the enum stored in {@code where}, given the converters and the values its column's
	 * {@code CHECK} allows: no converter, or a converter writing something else than the {@code CHECK} lists.
	 * Empty when it is right.
	 */
	static List<String> problems(String where, Class<?> enumType,
			Map<Class<?>, AttributeConverter<Object, String>> converters, Set<String> allowed) {
		AttributeConverter<Object, String> converter = converters.get(enumType);
		if (converter == null) {
			return List.of(where + " (" + enumType.getSimpleName() + "): no @Converter writes this enum");
		}
		return problems(where, enumType, writtenValues(enumType, converter), allowed);
	}

	/** What is wrong with {@code written} (what the real writer stores) against the values of the {@code CHECK}. */
	static List<String> problems(String where, Class<?> enumType, Set<String> written, Set<String> allowed) {
		Set<String> missingFromCheck = difference(written, allowed);
		Set<String> missingFromEnum = difference(allowed, written);
		if (missingFromCheck.isEmpty() && missingFromEnum.isEmpty()) {
			return List.of();
		}
		List<String> problems = new ArrayList<>();
		problems.add(where + " (" + enumType.getSimpleName() + "): written but not allowed by the CHECK: "
				+ missingFromCheck + ", allowed by the CHECK but never written: " + missingFromEnum);
		return problems;
	}

	private static Set<String> difference(Set<String> from, Set<String> without) {
		return from.stream().filter(value -> !without.contains(value)).collect(Collectors.toCollection(TreeSet::new));
	}

	private static Class<?> classOf(BeanDefinition candidate) {
		try {
			return Class.forName(candidate.getBeanClassName());
		}
		catch (ClassNotFoundException e) {
			throw new IllegalStateException(e);
		}
	}

	@SuppressWarnings("unchecked")
	private static AttributeConverter<Object, String> instance(Class<?> converterType) {
		try {
			Constructor<?> constructor = converterType.getDeclaredConstructor();
			constructor.setAccessible(true);
			return (AttributeConverter<Object, String>) constructor.newInstance();
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("cannot create " + converterType.getName(), e);
		}
	}

}
