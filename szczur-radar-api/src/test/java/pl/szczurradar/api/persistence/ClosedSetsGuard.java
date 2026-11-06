package pl.szczurradar.api.persistence;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
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

	private ClosedSetsGuard() {
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
