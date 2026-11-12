package pl.szczurradar.api.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import pl.szczurradar.api.agents.assessor.Provenance;
import pl.szczurradar.api.agents.assessor.contract.Assertiveness;
import pl.szczurradar.api.agents.assessor.contract.Attribution;
import pl.szczurradar.api.agents.assessor.contract.ClaimKind;
import pl.szczurradar.api.agents.assessor.contract.Direction;
import pl.szczurradar.api.agents.assessor.contract.Document;
import pl.szczurradar.api.agents.assessor.contract.Field;
import pl.szczurradar.api.agents.assessor.contract.Flag;
import pl.szczurradar.api.agents.assessor.contract.ItemType;
import pl.szczurradar.api.agents.assessor.contract.Modifier;
import pl.szczurradar.api.agents.assessor.contract.Reference;
import pl.szczurradar.api.agents.assessor.contract.Relevance;
import pl.szczurradar.api.agents.assessor.contract.TargetKind;
import pl.szczurradar.api.agents.assessor.validation.FailureClass;
import pl.szczurradar.api.auth.Passwords;
import pl.szczurradar.api.config.ErrorCode;
import pl.szczurradar.api.config.PageLimits;
import pl.szczurradar.api.llm.Provider;
import pl.szczurradar.api.notifications.NotificationKind;
import pl.szczurradar.api.pipeline.api.PipelineRunController;
import pl.szczurradar.api.pipeline.run.AttentionReason;
import pl.szczurradar.api.pipeline.run.InterruptionCause;
import pl.szczurradar.api.pipeline.run.RunKind;
import pl.szczurradar.api.pipeline.run.RunStatus;
import pl.szczurradar.api.pipeline.run.RunStep;
import pl.szczurradar.api.pipeline.run.SourceFetchStatus;
import pl.szczurradar.api.pipeline.run.StepStatus;
import pl.szczurradar.api.posts.Post;
import pl.szczurradar.api.posts.PostFilter;
import pl.szczurradar.api.posts.PostStage;
import pl.szczurradar.api.posts.QualityStatus;
import pl.szczurradar.api.sources.Source;
import pl.szczurradar.api.text.Whitespace;
import pl.szczurradar.api.user.Role;

/**
 * The registry of the rules the SPA copies, {@code szczur-radar-web/src/contract/rules.json}, against the
 * server: the white space set, the limits, the lists of values (in the order of their enum) and the error
 * codes. The SPA's {@code rules.test.ts} reads the same file and compares it with the SPA's copies, so a rule
 * changed on one side alone turns one of the two tests red. Each registry below is complete both ways: a key
 * the file has and the test does not know fails, and so does a key the test knows and the file lacks.
 */
class SpaRulesTest {

	/** Relative to the module directory, where Maven runs the tests. */
	private static final Path RULES = Path.of("../szczur-radar-web/src/contract/rules.json");

	private static final JsonMapper JSON = JsonMapper.builder().build();

	/** The limits by their name in {@code rules.json}, and the constant of the server each one is. */
	private static final Map<String, Integer> LIMITS = Map.of(
			"feedQueryMaxLength", PostFilter.MAX_QUERY_LENGTH,
			"runRangeMaxDays", PipelineRunController.MAX_RANGE_DAYS,
			"pageMaxSize", PageLimits.MAX_SIZE,
			"passwordMinLength", Passwords.MIN_LENGTH,
			"passwordMaxLength", Passwords.MAX_LENGTH,
			"maxAssessmentAttempts", Post.MAX_ASSESSMENT_ATTEMPTS,
			"pageMax", PageLimits.MAX_PAGE);

	/**
	 * The lists of values by their name in {@code rules.json}, which is the name of the SPA's {@code labels}
	 * set where it has one (not always: {@code attentionReason} and the lists the SPA has no label for have a
	 * name of their own), and the enum each one is. Complete both ways against the enums the response types of
	 * {@link ContractRegistry#SHAPES} reach ({@link #valuesAreTheEnumsTheResponsesReach()}).
	 */
	private static final Map<String, Class<? extends Enum<?>>> VALUES = values();

	private final JsonNode rules = JSON.readTree(RULES);

	private static Map<String, Class<? extends Enum<?>>> values() {
		Map<String, Class<? extends Enum<?>>> values = new LinkedHashMap<>();
		values.put("itemType", ItemType.class);
		values.put("relevance", Relevance.class);
		values.put("direction", Direction.class);
		values.put("flag", Flag.class);
		values.put("modifier", Modifier.class);
		values.put("claimKind", ClaimKind.class);
		values.put("attribution", Attribution.class);
		values.put("targetKind", TargetKind.class);
		values.put("reference", Reference.class);
		values.put("runKind", RunKind.class);
		values.put("runStatus", RunStatus.class);
		values.put("interruptionCause", InterruptionCause.class);
		values.put("stepStatus", StepStatus.class);
		values.put("step", RunStep.class);
		values.put("failureClass", FailureClass.class);
		values.put("source", Source.class);
		values.put("sourceStatus", SourceFetchStatus.class);
		values.put("attentionReason", AttentionReason.class);
		values.put("qualityStatus", QualityStatus.class);
		values.put("role", Role.class);
		values.put("notificationKind", NotificationKind.class);
		values.put("postStage", PostStage.class);
		values.put("resultSource", Provenance.Source.class);
		values.put("document", Document.class);
		values.put("field", Field.class);
		values.put("assertiveness", Assertiveness.class);
		values.put("provider", Provider.class);
		return values;
	}

	@Test
	void theRegistryHasExactlyTheKnownSections() {
		assertThat(keys(rules)).containsExactlyInAnyOrder("whitespace", "limits", "values", "errorCodes");
	}

	@Test
	void whitespaceIsTheSetOfTheServer() {
		List<Integer> expected = IntStream.rangeClosed(0, Character.MAX_CODE_POINT)
			.filter(Whitespace::isWhitespace)
			.boxed()
			.toList();

		assertThat(ints(rules.get("whitespace"))).as("rules.json whitespace").isEqualTo(expected);
	}

	@Test
	void limitsAreTheConstantsOfTheServer() {
		JsonNode limits = rules.get("limits");

		assertThat(keys(limits)).as("the limits in rules.json").isEqualTo(new TreeSet<>(LIMITS.keySet()));
		LIMITS.forEach((name, constant) -> assertThat(limits.get(name).asInt()).as("limit %s", name)
			.isEqualTo(constant));
	}

	@Test
	void valuesAreTheEnumsOfTheServerInDeclarationOrder() {
		JsonNode values = rules.get("values");

		assertThat(keys(values)).as("the lists of values in rules.json").isEqualTo(new TreeSet<>(VALUES.keySet()));
		VALUES.forEach((name, type) -> assertThat(strings(values.get(name))).as("values of %s", name)
			.isEqualTo(wireValues(type)));
	}

	@Test
	void valuesAreTheEnumsTheResponsesReach() {
		Map<Class<?>, String> reached = ContractTypes.reachableEnums(
				ContractRegistry.SHAPES.values().stream().flatMap(List::stream).map(ContractRegistry.Shape::type).toList());
		Set<Class<?>> registered = new LinkedHashSet<>(VALUES.values());

		assertThat(reached).as("the enums the response types reach (a walk that finds nothing proves nothing)")
			.isNotEmpty();
		assertThat(describe(reached, registered, false))
			.withFailMessage("%s", "Enums in a response of ContractRegistry.SHAPES with no list in SpaRulesTest.VALUES"
					+ " and rules.json: " + describe(reached, registered, false) + " — add the list (in declaration"
					+ " order) and the SPA's union for it")
			.isEmpty();
		assertThat(unreached(reached, registered))
			.withFailMessage("%s", "Lists in SpaRulesTest.VALUES whose enum no response type reaches: "
					+ unreached(reached, registered) + " — remove the list from VALUES and rules.json, or the"
					+ " response type from ContractRegistry")
			.isEmpty();
	}

	@Test
	void anEnumOutsideTheRegistryIsReported() {
		Map<Class<?>, String> reached = ContractTypes.reachableEnums(List.of(Outer.class));

		assertThat(describe(reached, new LinkedHashSet<>(VALUES.values()), false))
			.containsExactly(Outer.Colour.class.getName() + " (at Inner.colour)");
	}

	private record Outer(List<Inner> inners) {

		private enum Colour {
			RED
		}

		private record Inner(Colour colour, Role role) {
		}

	}

	/** The reached enums that are (or, with {@code inRegistry}, are not) in {@code registered}, by binary name. */
	private static List<String> describe(Map<Class<?>, String> reached, Set<Class<?>> registered,
			boolean inRegistry) {
		return reached.entrySet()
			.stream()
			.filter(entry -> registered.contains(entry.getKey()) == inRegistry)
			.map(entry -> entry.getKey().getName() + " (at " + entry.getValue() + ")")
			.toList();
	}

	private static List<String> unreached(Map<Class<?>, String> reached, Set<Class<?>> registered) {
		return registered.stream().filter(type -> !reached.containsKey(type)).map(Class::getName).collect(Collectors.toList());
	}

	@Test
	void errorCodesAreTheErrorCodesOfTheServerInDeclarationOrder() {
		assertThat(strings(rules.get("errorCodes"))).as("rules.json errorCodes").isEqualTo(wireValues(ErrorCode.class));
	}

	/** The enum's constants as the API writes them (the {@code @JsonValue}), in declaration order. */
	private static List<String> wireValues(Class<? extends Enum<?>> type) {
		return Arrays.stream(type.getEnumConstants()).map(constant -> JSON.valueToTree(constant).asString()).toList();
	}

	private static Set<String> keys(JsonNode node) {
		Set<String> keys = new TreeSet<>();
		node.properties().forEach(property -> keys.add(property.getKey()));
		return keys;
	}

	private static List<String> strings(JsonNode array) {
		List<String> strings = new ArrayList<>();
		array.forEach(element -> strings.add(element.asString()));
		return strings;
	}

	private static List<Integer> ints(JsonNode array) {
		List<Integer> ints = new ArrayList<>();
		array.forEach(element -> ints.add(element.asInt()));
		return ints;
	}

}
