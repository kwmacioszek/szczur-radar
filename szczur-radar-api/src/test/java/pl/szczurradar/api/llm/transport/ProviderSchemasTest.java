package pl.szczurradar.api.llm.transport;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import pl.szczurradar.api.agents.assessor.validation.OutputParser;
import pl.szczurradar.api.llm.Provider;

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
		assertThat(target.at("/properties/stance/properties/assertiveness").toString())
			.isEqualTo("{\"anyOf\":[{\"type\":\"string\",\"enum\":[\"hedged\",\"unhedged\"]},{\"type\":\"null\"}]}");
	}

	@Test
	void onlyGeminiGetsTheNullableEnumAsAnyOf() {
		// Gemini never answered null to {"enum": [..., null]} (live smoke 2026-10-04, D12).
		for (Provider provider : List.of(Provider.DEEPSEEK, Provider.OPENROUTER)) {
			JsonNode sent = MAPPER.readTree(ProviderSchemas.forProvider(provider, V2));
			assertThat(sent.at("/properties/targets/items/properties/stance/properties/assertiveness").toString())
				.as(provider.id())
				.isEqualTo("{\"enum\":[\"hedged\",\"unhedged\",null]}");
		}
		String schema = """
				{"type":"object","properties":{"enum":{"enum":["a",null],"description":"d"},"n":{"enum":[1,null]}}}""";
		assertThat(ProviderSchemas.forProvider(Provider.GEMINI, schema)).isEqualTo(
				"{\"type\":\"object\",\"properties\":{\"enum\":{\"anyOf\":[{\"type\":\"string\",\"enum\":[\"a\"]},"
						+ "{\"type\":\"null\"}],\"description\":\"d\"},\"n\":{\"enum\":[1,null]}}}");
	}

	@Test
	void propertiesNamedLikeKeywordsAreKept() {
		String schema = """
				{"type":"object","properties":{"maxLength":{"type":"integer","maxItems":3}},"required":["maxLength"]}""";
		assertThat(ProviderSchemas.forProvider(Provider.OPENROUTER, schema))
			.isEqualTo("{\"type\":\"object\",\"properties\":{\"maxLength\":{\"type\":\"integer\"}},\"required\":[\"maxLength\"]}");
	}

	@Test
	void eachProviderAndSchemaIsTransformedOnceAndReused() {
		String first = ProviderSchemas.forProvider(Provider.DEEPSEEK, V2);

		assertThat(ProviderSchemas.forProvider(Provider.DEEPSEEK, V2)).isSameAs(first);
		assertThat(OutputParser.schemaText()).isSameAs(V2);
	}

	@Test
	void concurrentCallsWithDifferentSchemasAndProvidersGetTheirOwnResults() throws Exception {
		List<String> schemas = List.of(V2, """
				{"type":"object","properties":{"enum":{"enum":["a",null],"description":"d"},"n":{"enum":[1,null]}}}""",
				"""
				{"type":"object","properties":{"maxLength":{"type":"integer","maxItems":3}},"required":["maxLength"]}""");
		Map<String, String> expected = new HashMap<>();
		for (Provider provider : Provider.values()) {
			for (int i = 0; i < schemas.size(); i++) {
				expected.put(provider.id() + i, ProviderSchemas.forProvider(provider, schemas.get(i)));
			}
		}
		// Different schemas of one provider never share a result.
		assertThat(Set.of(expected.get("gemini0"), expected.get("gemini1"), expected.get("gemini2"))).hasSize(3);

		ExecutorService executor = Executors.newFixedThreadPool(8);
		try {
			List<Callable<Boolean>> calls = new ArrayList<>();
			for (int round = 0; round < 50; round++) {
				for (Provider provider : Provider.values()) {
					for (int i = 0; i < schemas.size(); i++) {
						String schema = schemas.get(i);
						String key = provider.id() + i;
						calls.add(() -> ProviderSchemas.forProvider(provider, schema).equals(expected.get(key)));
					}
				}
			}
			for (Future<Boolean> result : executor.invokeAll(calls)) {
				assertThat(result.get()).isTrue();
			}
		}
		finally {
			executor.shutdownNow();
		}
	}

	private static List<String> names(JsonNode object) {
		return new ArrayList<>(object.propertyNames());
	}

}
