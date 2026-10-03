package pl.szczurradar.api.agents.assessor.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import pl.szczurradar.api.agents.assessor.validation.OutputParser;

/**
 * The records and the V2 output schema describe the same shape: same property names in the same
 * order, every property required, no extra properties, and the same enum values.
 */
class ContractSchemaTest {

	private final JsonNode schema = ContractJson.MAPPER.readTree(OutputParser.schemaText());

	@Test
	void recordsHaveTheSchemasPropertiesInOrder() {
		assertSameShape(schema, AssessmentOutputV2.class);
		assertSameShape(definition("target"), Target.class);
		assertSameShape(definition("anchor"), Anchor.class);
		assertSameShape(definition("stance"), Stance.class);
		assertSameShape(definition("claim"), Claim.class);
		assertSameShape(definition("evidence"), Evidence.class);
	}

	@Test
	void noObjectEndsWithANestedObject() {
		for (JsonNode object : List.of(schema, definition("target"), definition("anchor"), definition("stance"),
				definition("claim"), definition("evidence"))) {
			List<String> names = propertyNames(object);
			JsonNode last = object.get("properties").get(names.getLast());
			assertThat(last.has("$ref") && List.of("#/$defs/anchor", "#/$defs/stance", "#/$defs/evidence",
					"#/$defs/target", "#/$defs/claim").contains(last.get("$ref").asString()))
				.as("last property %s", names.getLast())
				.isFalse();
		}
	}

	@Test
	void schemaEnumsAreTheJavaEnums() {
		JsonNode properties = schema.get("properties");
		assertThat(enumValues(properties.get("relevance"))).isEqualTo(values(Relevance.values()));
		assertThat(enumValues(properties.get("modifiers").get("items"))).isEqualTo(values(Modifier.values()));
		assertThat(enumValues(properties.get("flags").get("items"))).isEqualTo(values(Flag.values()));
		assertThat(enumValues(definition("target").get("properties").get("kind"))).isEqualTo(values(TargetKind.values()));
		assertThat(enumValues(definition("target").get("properties").get("reference")))
			.isEqualTo(values(Reference.values()));
		assertThat(enumValues(definition("stance").get("properties").get("direction")))
			.isEqualTo(values(Direction.values()));
		List<String> assertiveness = new ArrayList<>(values(Assertiveness.values()));
		assertiveness.add("null");
		assertThat(enumValues(definition("stance").get("properties").get("assertiveness"))).isEqualTo(assertiveness);
		assertThat(enumValues(definition("claim").get("properties").get("kind"))).isEqualTo(values(ClaimKind.values()));
		assertThat(enumValues(definition("claim").get("properties").get("attribution")))
			.isEqualTo(values(Attribution.values()));
		assertThat(enumValues(definition("document"))).isEqualTo(values(Document.values()));
		assertThat(enumValues(definition("field"))).isEqualTo(values(Field.values()));
	}

	@Test
	void schemaHasTheLimitsOfSection9() {
		JsonNode properties = schema.get("properties");
		assertThat(properties.get("targets").get("maxItems").asInt()).isEqualTo(12);
		assertThat(properties.get("claims").get("maxItems").asInt()).isEqualTo(24);
		assertThat(definition("stance").get("properties").get("claim_ids").get("maxItems").asInt()).isEqualTo(4);
		assertThat(definition("claim").get("properties").get("target_ids").get("maxItems").asInt()).isEqualTo(12);
		assertThat(definition("anchor").get("properties").get("quote").get("maxLength").asInt()).isEqualTo(100);
		assertThat(definition("evidence").get("properties").get("quote").get("maxLength").asInt()).isEqualTo(320);
		assertThat(definition("claim").get("properties").get("statement").get("maxLength").asInt()).isEqualTo(240);
		assertThat(properties.get("modifiers").get("uniqueItems").asBoolean()).isTrue();
		assertThat(properties.get("flags").get("uniqueItems").asBoolean()).isTrue();
	}

	@Test
	void outputHasNoForbiddenFields() {
		assertThat(schema.toString()).doesNotContain("supported_by_claim_ids", "\"score\"", "\"confidence\"",
				"is_signal", "schema_version");
	}

	private JsonNode definition(String name) {
		return schema.get("$defs").get(name);
	}

	private static void assertSameShape(JsonNode object, Class<? extends Record> type) {
		List<String> names = propertyNames(object);
		assertThat(jsonNames(type)).as(type.getSimpleName()).isEqualTo(names);
		assertThat(object.get("additionalProperties").asBoolean(true)).as(type.getSimpleName()).isFalse();
		List<String> required = new ArrayList<>();
		object.get("required").forEach(name -> required.add(name.asString()));
		assertThat(required).as(type.getSimpleName()).isEqualTo(names);
	}

	private static List<String> propertyNames(JsonNode object) {
		return new ArrayList<>(object.get("properties").propertyNames());
	}

	private static List<String> jsonNames(Class<? extends Record> type) {
		return Arrays.stream(type.getRecordComponents()).map(ContractSchemaTest::jsonName).toList();
	}

	private static String jsonName(RecordComponent component) {
		JsonProperty property = component.getAccessor().getAnnotation(JsonProperty.class);
		return property != null ? property.value() : component.getName();
	}

	private static List<String> enumValues(JsonNode node) {
		List<String> values = new ArrayList<>();
		node.get("enum").forEach(value -> values.add(value.isNull() ? "null" : value.asString()));
		return values;
	}

	private static List<String> values(Enum<?>[] constants) {
		return Arrays.stream(constants).map(constant -> ContractJson.MAPPER.writeValueAsString(constant)
			.replace("\"", "")).toList();
	}

}
