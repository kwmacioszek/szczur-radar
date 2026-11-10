package pl.szczurradar.api.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The variants of the contract files, derived from the response records ({@link ContractRegistry#SHAPES}):
 * walking each file with the type its body is, every record reached is checked per component over every file
 * it occurs in, at any depth and in every element of an array —
 * <ul>
 * <li>a {@code @Nullable} component is {@code null} at least once and not {@code null} at least once;</li>
 * <li>a {@code List} component is non-empty at least once, so the shape of its elements is in the contract.</li>
 * </ul>
 * A variant no test can produce goes into {@link #KNOWN_GAPS} with the reason; an entry whose rule is met
 * fails, so the list does not rot. The walk also checks that each object of a file has exactly the JSON
 * names of its record's components (the registry names the right type), and that the files in the
 * directory are exactly the files of the registry.
 */
class ContractVariantsTest {

	/** Relative to the module directory, where Maven runs the tests. */
	private static final Path DIRECTORY = Path.of("../szczur-radar-web/src/contract/");

	/** The registry of the rules the SPA copies ({@code SpaRulesTest}), not a response body. */
	private static final String RULES = "rules.json";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	/** {@code <Record>.<component>} → why no test can produce the missing variant. */
	private static final Map<String, String> KNOWN_GAPS = Map.of(
			"PostItem.assessment",
			"null only for an assessed post without an ok assessment, which recording a post assessed never leaves"
					+ " (PostController: 'should not happen for an assessed post')",
			"LastAttempt.failureClass",
			"a post in the failed list has a failed latest attempt or none; ck_assessments_outcome requires"
					+ " failure_class for a failed attempt",
			"NotifiedRun.finishedAt",
			"a run is notified only once it ended (RunNotifications.runEnded on a run with attention reasons, which"
					+ " a queued or running run has none of), and every ending of RunRecorder sets finished_at");

	/**
	 * Components the walk must find as {@code @Nullable} and as lists, so it cannot pass by finding nothing
	 * (a broken annotation check, a registry without types).
	 */
	private static final Set<String> MUST_FIND_NULLABLE = Set.of("RunView.rangeFrom", "NotificationItem.run",
			"PostItem.title", "LastAttempt.provider");

	private static final Set<String> MUST_FIND_LISTS = Set.of("RunView.attentionReasons", "Claim.targetIds",
			"MissingDayView.sources");

	@Test
	void theFilesAreExactlyTheFilesOfTheRegistry() throws IOException {
		Set<String> inDirectory;
		try (Stream<Path> files = Files.list(DIRECTORY)) {
			inDirectory = files.map(file -> file.getFileName().toString())
				.filter(name -> name.endsWith(".json") && !name.equals(RULES))
				.collect(Collectors.toCollection(TreeSet::new));
		}
		Set<String> registered = files();

		assertThat(difference(inDirectory, registered))
			.withFailMessage("%s", "Contract files without an entry in ContractRegistry.SHAPES: "
					+ difference(inDirectory, registered) + " — add the route that gives the body, its file and type")
			.isEmpty();
		assertThat(difference(registered, inDirectory))
			.withFailMessage("%s", "Files in ContractRegistry.SHAPES missing in " + DIRECTORY + ": "
					+ difference(registered, inDirectory))
			.isEmpty();
	}

	@Test
	void everyExceptionHasItsReason() {
		Map<String, String> exceptions = new TreeMap<>();
		KNOWN_GAPS.forEach((component, reason) -> exceptions.put("KNOWN_GAPS " + component, reason));
		ContractRegistry.NOT_READ_BY_SPA.forEach((route, reason) -> exceptions.put("NOT_READ_BY_SPA " + route, reason));

		assertThat(exceptions).as("the entries of KNOWN_GAPS and NOT_READ_BY_SPA").isNotEmpty();
		assertThat(exceptions.entrySet().stream().filter(entry -> entry.getValue().isBlank()).map(Map.Entry::getKey))
			.as("entries without a reason")
			.isEmpty();
	}

	@Test
	void everyNullableAndListComponentHasItsVariants() {
		Walk walk = walk();

		Set<String> nullable = walk.components.values()
			.stream()
			.filter(Component::nullable)
			.map(Component::name)
			.collect(Collectors.toCollection(TreeSet::new));
		Set<String> lists = walk.components.values()
			.stream()
			.filter(Component::list)
			.map(Component::name)
			.collect(Collectors.toCollection(TreeSet::new));
		assertThat(nullable).as("@Nullable components the walk found").containsAll(MUST_FIND_NULLABLE);
		assertThat(lists).as("list components the walk found").containsAll(MUST_FIND_LISTS);

		List<String> missing = new ArrayList<>();
		Set<String> gapsNeeded = new TreeSet<>();
		walk.components.values().forEach(component -> {
			List<String> lacks = component.lacks(walk.observed.getOrDefault(component.name, new Observed()));
			if (lacks.isEmpty()) {
				return;
			}
			if (KNOWN_GAPS.containsKey(component.name)) {
				gapsNeeded.add(component.name);
				return;
			}
			missing.add(component.name + ": " + String.join(", ", lacks) + " in any of "
					+ walk.filesOf.get(component.record));
		});
		assertThat(missing).withFailMessage("%s", "Variants missing from the contract files:\n  "
				+ String.join("\n  ", missing) + "\nSeed each one in the integration test that writes one of"
				+ " those files (and its sample in samples.ts), or add the component to KNOWN_GAPS in"
				+ " ContractVariantsTest with the reason no test can produce it")
			.isEmpty();

		Set<String> unknown = difference(KNOWN_GAPS.keySet(), walk.components.keySet());
		assertThat(unknown).withFailMessage("%s", "KNOWN_GAPS names components no contract type has: " + unknown)
			.isEmpty();
		Set<String> stale = difference(difference(KNOWN_GAPS.keySet(), gapsNeeded), unknown);
		assertThat(stale).withFailMessage("%s", "KNOWN_GAPS entries whose variants the files now have: " + stale
				+ " — remove them from ContractVariantsTest")
			.isEmpty();
	}

	/** Every contract file walked with its type; the components reached and what the files hold for them. */
	private static Walk walk() {
		Walk walk = new Walk();
		ContractRegistry.SHAPES.values()
			.stream()
			.flatMap(List::stream)
			.forEach(shape -> walk.value(shape.type(), read(shape.file()), shape.file(), "$"));
		return walk;
	}

	private static JsonNode read(String file) {
		return JSON.readTree(DIRECTORY.resolve(file));
	}

	private static Set<String> files() {
		return ContractRegistry.SHAPES.values()
			.stream()
			.flatMap(List::stream)
			.map(ContractRegistry.Shape::file)
			.collect(Collectors.toCollection(TreeSet::new));
	}

	private static Set<String> difference(Set<String> from, Set<String> without) {
		return from.stream().filter(value -> !without.contains(value)).collect(Collectors.toCollection(TreeSet::new));
	}

	/** {@code List<X>} → {@code X}; anything else → {@code null}. */
	private static @Nullable Type listElement(Type type) {
		if (type instanceof ParameterizedType parameterized && parameterized.getRawType() == List.class) {
			return parameterized.getActualTypeArguments()[0];
		}
		return null;
	}

	/** A value without inner structure: what the walk does not descend into. */
	private static boolean leaf(Type type) {
		if (!(type instanceof Class<?> scalar)) {
			return false;
		}
		return scalar.isEnum() || scalar.isPrimitive() || scalar == String.class || scalar == UUID.class
				|| scalar == Instant.class || scalar == LocalDate.class || scalar == Integer.class || scalar == Long.class
				|| scalar == Boolean.class;
	}

	/** The JSON name of a component: its {@code @JsonProperty} value, else its name. */
	private static String jsonName(RecordComponent component) {
		JsonProperty property = component.getAccessor().getAnnotation(JsonProperty.class);
		if (property == null) {
			property = component.getAnnotation(JsonProperty.class);
		}
		return property != null && !property.value().isEmpty() ? property.value() : component.getName();
	}

	/** A component of a reachable record, by {@code <Record>.<component>}. */
	private record Component(Class<?> record, String name, boolean nullable, boolean list) {

		/** The variants {@code observed} lacks for this component's rules. */
		List<String> lacks(Observed observed) {
			List<String> lacks = new ArrayList<>();
			if (nullable && observed.nulls == 0) {
				lacks.add("no null");
			}
			if (nullable && observed.values == 0) {
				lacks.add("no non-null value");
			}
			if (list && observed.nonEmptyLists == 0) {
				lacks.add("no non-empty list");
			}
			return lacks;
		}

	}

	/** What the files hold for one component. */
	private static final class Observed {

		int nulls;

		int values;

		int nonEmptyLists;

	}

	/** The walk over the files: the reachable records' components and what the files hold for them. */
	private static final class Walk {

		final Map<String, Component> components = new TreeMap<>();

		final Map<String, Observed> observed = new LinkedHashMap<>();

		final Map<Class<?>, Set<String>> filesOf = new LinkedHashMap<>();

		/** The record's simple name → the record, to keep the names of {@link #KNOWN_GAPS} unambiguous. */
		private final Map<String, Class<?>> records = new LinkedHashMap<>();

		/** {@code node} (at {@code path} in {@code file}) as a value of {@code type}. */
		void value(Type type, JsonNode node, String file, String path) {
			Type element = listElement(type);
			if (element != null) {
				reach(element, file);
				if (node.isNull()) {
					return;
				}
				assertThat(node.isArray()).as("%s %s: an array for %s", file, path, type).isTrue();
				for (int index = 0; index < node.size(); index++) {
					value(element, node.get(index), file, path + "[" + index + "]");
				}
				return;
			}
			if (type instanceof Class<?> record && record.isRecord()) {
				reach(record, file);
				if (!node.isNull()) {
					record(record, node, file, path);
				}
				return;
			}
			assertThat(leaf(type)).as("%s %s: a type the walk knows (a record, List<X>, an enum or a scalar): %s",
					file, path, type)
				.isTrue();
		}

		/**
		 * Makes {@code type}'s records and their components known as reachable, also those of a component no
		 * file holds a value of, and notes that {@code file} can hold them.
		 */
		private void reach(Type type, String file) {
			Type element = listElement(type);
			if (element != null) {
				reach(element, file);
				return;
			}
			if (!(type instanceof Class<?> record) || !record.isRecord()) {
				return;
			}
			if (!filesOf.computeIfAbsent(record, key -> new TreeSet<>()).add(file)) {
				return;
			}
			Class<?> named = records.putIfAbsent(record.getSimpleName(), record);
			assertThat(named == null || named == record)
				.as("two contract records named %s: %s and %s", record.getSimpleName(), named, record)
				.isTrue();
			for (RecordComponent component : record.getRecordComponents()) {
				String name = record.getSimpleName() + "." + component.getName();
				components.putIfAbsent(name, new Component(record, name,
						component.getAnnotatedType().isAnnotationPresent(Nullable.class),
						listElement(component.getGenericType()) != null));
				reach(component.getGenericType(), file);
			}
		}

		private void record(Class<?> record, JsonNode node, String file, String path) {
			assertThat(node.isObject()).as("%s %s: an object for %s", file, path, record.getName()).isTrue();
			RecordComponent[] recordComponents = record.getRecordComponents();
			Set<String> names = new TreeSet<>();
			for (RecordComponent component : recordComponents) {
				names.add(jsonName(component));
			}
			Set<String> keys = new TreeSet<>();
			node.properties().forEach(property -> keys.add(property.getKey()));
			assertThat(keys).as("%s %s: the keys of %s (is the registry's type right?)", file, path, record.getName())
				.isEqualTo(names);
			for (RecordComponent component : recordComponents) {
				JsonNode value = node.get(jsonName(component));
				Observed seen = observed.computeIfAbsent(record.getSimpleName() + "." + component.getName(),
						key -> new Observed());
				if (value.isNull()) {
					seen.nulls++;
				}
				else {
					seen.values++;
					if (value.isArray() && !value.isEmpty()) {
						seen.nonEmptyLists++;
					}
				}
				value(component.getGenericType(), value, file, path + "." + jsonName(component));
			}
		}

	}

}
