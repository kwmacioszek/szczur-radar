package pl.szczurradar.api.llm;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
 *   <li>Gemini: no {@code minItems}/{@code maxItems} (HTTP 400 in the pilot); a nullable enum
 *       ({@code {"enum": ["a", "b", null]}}) becomes {@code {"anyOf": [{"type": "string", "enum": ["a",
 *       "b"]}, {"type": "null"}]}} — Gemini never answered {@code null} to the enum form (live smoke
 *       2026-10-04: every {@code not_expressed} stance got an {@code assertiveness}, D12), and the
 *       {@code anyOf} form is what Spring AI's mapping keeps ({@code "type": [..., "null"]} fails in the
 *       GenAI SDK, {@code nullable} is dropped);
 *   <li>DeepSeek, OpenRouter: no {@code minItems}/{@code maxItems}/{@code minLength}/{@code maxLength};
 *   <li>OpenRouter (OpenAI strict mode): also no {@code uniqueItems} ("'uniqueItems' is not
 *       permitted", HTTP 400 on 2026-10-03).
 * </ul>
 *
 * Property order is kept (a provider dropped closing braces when an object ended with a nested one).
 *
 * <p>Each {@code (provider, schema text)} pair is transformed once and cached (thread-safe), so
 * concurrent assessments do not parse and rewrite the schema on every call. The cache holds one
 * entry per distinct schema text: the application sends one schema, tests a handful.
 */
public final class ProviderSchemas {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private static final Set<String> COUNT_LIMITS = Set.of("minItems", "maxItems");

	private static final Set<String> COUNT_AND_LENGTH_LIMITS = Set.of("minItems", "maxItems", "minLength",
			"maxLength");

	private static final Set<String> OPENAI_STRICT_UNSUPPORTED = Set.of("minItems", "maxItems", "minLength",
			"maxLength", "uniqueItems");

	private static final Map<Key, String> CACHE = new ConcurrentHashMap<>();

	private ProviderSchemas() {
	}

	/** A cache key: the full schema text, never its hash alone. */
	private record Key(Provider provider, String schema) {
	}

	public static String forProvider(Provider provider, String schema) {
		return CACHE.computeIfAbsent(new Key(provider, schema), key -> transform(key.provider(), key.schema()));
	}

	private static String transform(Provider provider, String schema) {
		JsonNode root = MAPPER.readTree(schema);
		Set<String> dropped = switch (provider) {
			case GEMINI -> COUNT_LIMITS;
			case DEEPSEEK -> COUNT_AND_LENGTH_LIMITS;
			case OPENROUTER -> OPENAI_STRICT_UNSUPPORTED;
		};
		JsonNode simplified = simplify(root, root.path("$defs"), dropped, false);
		return MAPPER.writeValueAsString(provider == Provider.GEMINI ? nullableEnumsAsAnyOf(simplified, false) : simplified);
	}

	/** Rewrites every string enum that allows {@code null} as {@code anyOf} of the string enum and {@code null}. */
	private static JsonNode nullableEnumsAsAnyOf(JsonNode node, boolean propertyMap) {
		if (node.isArray()) {
			ArrayNode copy = MAPPER.createArrayNode();
			node.forEach(element -> copy.add(nullableEnumsAsAnyOf(element, false)));
			return copy;
		}
		if (!node.isObject()) {
			return node;
		}
		ObjectNode copy = MAPPER.createObjectNode();
		JsonNode values = node.get("enum");
		if (!propertyMap && values != null && values.isArray() && !node.has("type") && hasNullAndStrings(values)) {
			ArrayNode strings = MAPPER.createArrayNode();
			values.forEach(value -> {
				if (!value.isNull()) {
					strings.add(value);
				}
			});
			ObjectNode stringEnum = MAPPER.createObjectNode().put("type", "string");
			stringEnum.set("enum", strings);
			copy.set("anyOf", MAPPER.createArrayNode().add(stringEnum).add(MAPPER.createObjectNode().put("type", "null")));
			node.properties().forEach(property -> {
				if (!property.getKey().equals("enum")) {
					copy.set(property.getKey(), property.getValue());
				}
			});
			return copy;
		}
		for (Map.Entry<String, JsonNode> property : node.properties()) {
			copy.set(property.getKey(),
					nullableEnumsAsAnyOf(property.getValue(), !propertyMap && property.getKey().equals("properties")));
		}
		return copy;
	}

	private static boolean hasNullAndStrings(JsonNode values) {
		boolean hasNull = false;
		for (JsonNode value : values) {
			if (value.isNull()) {
				hasNull = true;
			}
			else if (!value.isString()) {
				return false;
			}
		}
		return hasNull && values.size() > 1;
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
