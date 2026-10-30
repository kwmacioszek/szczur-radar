package pl.szczurradar.api.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.regex.Pattern;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The files shared with the SPA, {@code szczur-radar-web/src/contract/}: the full bodies of the large
 * responses, which the integration tests compare with the real body and the SPA's Vitest with its typed
 * samples. A body is compared as masked: every string value that is a UUID becomes {@value #UUID_MARK} and
 * every ISO-8601 instant {@value #INSTANT_MARK}; everything else is literal. Key order does not matter,
 * array order does.
 */
public final class ContractFiles {

	/** Relative to the module directory, where Maven runs the tests. */
	private static final String DIRECTORY = "../szczur-radar-web/src/contract/";

	static final String UUID_MARK = "<uuid>";

	static final String INSTANT_MARK = "<instant>";

	static final Pattern UUID_VALUE = Pattern
		.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

	static final Pattern INSTANT_VALUE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z");

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private ContractFiles() {
	}

	/**
	 * Asserts that {@code json}, masked, equals the contract file {@code fileName}. A failure carries the
	 * masked body, pretty-printed, ready to be pasted into the file.
	 */
	public static void assertMatches(String json, String fileName) {
		JsonNode actual = mask(JSON.readTree(json));
		Path file = Path.of(DIRECTORY + fileName);
		String pretty = JSON.writerWithDefaultPrettyPrinter().writeValueAsString(actual);
		assertThat(file).as("the contract file %s is missing; the masked body is:%n%s", file, pretty).exists();
		assertThat(actual)
			.as("the body differs from %s; the masked body is:%n%s", file, pretty)
			.isEqualTo(JSON.readTree(file));
	}

	/** {@code node} with its UUID and instant string values replaced by their marks, at any depth. */
	static JsonNode mask(JsonNode node) {
		if (node.isString()) {
			String value = node.asString();
			if (UUID_VALUE.matcher(value).matches()) {
				return JSON.getNodeFactory().stringNode(UUID_MARK);
			}
			if (INSTANT_VALUE.matcher(value).matches()) {
				return JSON.getNodeFactory().stringNode(INSTANT_MARK);
			}
			return node;
		}
		if (node.isObject()) {
			ObjectNode masked = JSON.createObjectNode();
			node.properties().forEach(property -> masked.set(property.getKey(), mask(property.getValue())));
			return masked;
		}
		if (node.isArray()) {
			ArrayNode masked = JSON.createArrayNode();
			node.forEach(element -> masked.add(mask(element)));
			return masked;
		}
		return node;
	}

}
