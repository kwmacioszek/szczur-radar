package pl.szczurradar.api.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import pl.szczurradar.api.agents.assessor.validation.OutputParser;
import pl.szczurradar.api.config.LlmProviderProperties.Provider;

/** What each provider receives of the V2 output schema. */
class ProviderSchemasTest {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private static final String V2 = OutputParser.schemaText();

	@Test
	void geminiLosesOnlyCountLimits() {
		String sent = ProviderSchemas.forProvider(Provider.GEMINI, V2);
		assertThat(sent).doesNotContain("minItems", "maxItems", "$ref", "$defs", "$schema")
			.contains("\"maxLength\":100", "\"minLength\":1", "\"additionalProperties\":false", "\"uniqueItems\":true");
	}

	@Test
	void deepSeekAndOpenRouterLoseCountAndLengthLimits() {
		for (Provider provider : List.of(Provider.DEEPSEEK, Provider.OPENROUTER)) {
			assertThat(ProviderSchemas.forProvider(provider, V2)).as(provider.id())
				.doesNotContain("minItems", "maxItems", "minLength", "maxLength", "$ref", "$defs", "$schema")
				.contains("\"additionalProperties\":false", "\"required\"");
		}
	}

	@Test
	void openRouterAlsoLosesUniqueItemsWhichOpenAiStrictModeRejects() {
		assertThat(ProviderSchemas.forProvider(Provider.OPENROUTER, V2)).doesNotContain("uniqueItems");
		assertThat(ProviderSchemas.forProvider(Provider.DEEPSEEK, V2)).contains("\"uniqueItems\":true");
	}

	@Test
	void referencesAreInlinedAndPropertyOrderIsKept() {
		JsonNode sent = MAPPER.readTree(ProviderSchemas.forProvider(Provider.GEMINI, V2));
		JsonNode target = sent.at("/properties/targets/items");
		assertThat(names(target.get("properties"))).containsExactly("id", "kind", "anchor", "stance", "reference");
		assertThat(names(target.at("/properties/anchor/properties"))).containsExactly("document", "field", "quote");
		assertThat(names(sent.at("/properties/claims/items/properties"))).containsExactly("id", "kind", "attribution",
				"target_ids", "evidence", "statement");
		assertThat(target.at("/properties/stance/properties/assertiveness/enum").toString())
			.isEqualTo("[\"hedged\",\"unhedged\",null]");
	}

	@Test
	void propertiesNamedLikeKeywordsAreKept() {
		String schema = """
				{"type":"object","properties":{"maxLength":{"type":"integer","maxItems":3}},"required":["maxLength"]}""";
		assertThat(ProviderSchemas.forProvider(Provider.OPENROUTER, schema))
			.isEqualTo("{\"type\":\"object\",\"properties\":{\"maxLength\":{\"type\":\"integer\"}},\"required\":[\"maxLength\"]}");
	}

	private static List<String> names(JsonNode object) {
		return new ArrayList<>(object.propertyNames());
	}

}
