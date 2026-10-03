package pl.szczurradar.api.llm;

import java.util.Map;
import java.util.Set;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import pl.szczurradar.api.config.LlmProviderProperties.Provider;

/**
 * The subset of a JSON Schema each provider accepts. The full schema stays the caller's contract —
 * the raw answer is always validated against it — this only shapes what is sent:
 *
 * <ul>
 *   <li>all providers: {@code $ref}s to {@code #/$defs/...} inlined, {@code $defs} and
 *       {@code $schema} dropped (Spring AI's Gemini mapping silently loses {@code $ref});
 *   <li>Gemini: no {@code minItems}/{@code maxItems} (HTTP 400 in the pilot);
 *   <li>DeepSeek, OpenRouter: no {@code minItems}/{@code maxItems}/{@code minLength}/{@code maxLength};
 *   <li>OpenRouter (OpenAI strict mode): also no {@code uniqueItems} ("'uniqueItems' is not
 *       permitted", HTTP 400 on 2026-10-03).
 * </ul>
 *
 * Property order is kept (a provider dropped closing braces when an object ended with a nested one).
 */
public final class ProviderSchemas {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private static final Set<String> COUNT_LIMITS = Set.of("minItems", "maxItems");

	private static final Set<String> COUNT_AND_LENGTH_LIMITS = Set.of("minItems", "maxItems", "minLength",
			"maxLength");

	private static final Set<String> OPENAI_STRICT_UNSUPPORTED = Set.of("minItems", "maxItems", "minLength",
			"maxLength", "uniqueItems");

	private ProviderSchemas() {
	}

	public static String forProvider(Provider provider, String schema) {
		JsonNode root = MAPPER.readTree(schema);
		Set<String> dropped = switch (provider) {
			case GEMINI -> COUNT_LIMITS;
			case DEEPSEEK -> COUNT_AND_LENGTH_LIMITS;
			case OPENROUTER -> OPENAI_STRICT_UNSUPPORTED;
		};
		return MAPPER.writeValueAsString(simplify(root, root.path("$defs"), dropped, false));
	}

	/**
	 * @param propertyMap whether {@code node} is the value of {@code properties}: its keys are
	 *     property names, never keywords to drop
	 */
	private static JsonNode simplify(JsonNode node, JsonNode definitions, Set<String> dropped, boolean propertyMap) {
		if (node.isObject()) {
			JsonNode ref = node.get("$ref");
			if (!propertyMap && ref != null && ref.isString()) {
				return simplify(resolve(ref.asString(), definitions), definitions, dropped, false);
			}
			ObjectNode copy = MAPPER.createObjectNode();
			for (Map.Entry<String, JsonNode> property : node.properties()) {
				String name = property.getKey();
				if (!propertyMap && (dropped.contains(name) || name.equals("$defs") || name.equals("$schema"))) {
					continue;
				}
				copy.set(name, simplify(property.getValue(), definitions, dropped,
						!propertyMap && name.equals("properties")));
			}
			return copy;
		}
		if (node.isArray()) {
			ArrayNode copy = MAPPER.createArrayNode();
			node.forEach(element -> copy.add(simplify(element, definitions, dropped, false)));
			return copy;
		}
		return node;
	}

	private static JsonNode resolve(String ref, JsonNode definitions) {
		String prefix = "#/$defs/";
		if (!ref.startsWith(prefix) || !definitions.has(ref.substring(prefix.length()))) {
			throw new IllegalArgumentException("unsupported or unknown $ref " + ref);
		}
		return definitions.get(ref.substring(prefix.length()));
	}

}
