package pl.szczurradar.api.agents.assessor.validation;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.path.PathType;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

import pl.szczurradar.api.agents.assessor.contract.AssessmentOutputV2;
import pl.szczurradar.api.agents.assessor.contract.ContractJson;

/**
 * Raw model text → {@link AssessmentOutputV2} or violations, in the order of contract-v2.md §12:
 * strict JSON parsing ({@link FailureClass#INVALID_STRUCTURED_OUTPUT}), then the V2 JSON Schema
 * ({@link FailureClass#SCHEMA_VIOLATION}), then mapping to records. Nothing is repaired: no markdown
 * fences stripped, no lenient parsing — framework output converters must not run before this.
 * Domain rules come after, in {@code RuleBasedValidator}.
 */
public final class OutputParser {

	/** Classpath location of the V2 output schema — the single source of the output shape. */
	public static final String SCHEMA_RESOURCE = "/assessor/assessment-output-v2.schema.json";

	private final Schema schema;

	public OutputParser() {
		SchemaRegistryConfig config = SchemaRegistryConfig.builder().pathType(PathType.JSON_PATH).build();
		SchemaRegistry registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12,
				builder -> builder.schemaRegistryConfig(config));
		this.schema = registry.getSchema(schemaText(), InputFormat.JSON);
	}

	/** The V2 output schema as stored in the resource. */
	public static String schemaText() {
		try (InputStream in = OutputParser.class.getResourceAsStream(SCHEMA_RESOURCE)) {
			if (in == null) {
				throw new IllegalStateException("missing classpath resource " + SCHEMA_RESOURCE);
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/** Parses {@code raw} strictly; never throws for bad model output. */
	public Result parse(String raw) {
		if (raw == null || raw.isBlank()) {
			return reject(FailureClass.INVALID_STRUCTURED_OUTPUT, "json", "no text in the response");
		}
		JsonNode tree;
		try {
			tree = ContractJson.MAPPER.readTree(raw);
		}
		catch (JacksonException ex) {
			return reject(FailureClass.INVALID_STRUCTURED_OUTPUT, "json", ex.getOriginalMessage());
		}
		List<Error> errors = schema.validate(tree);
		if (!errors.isEmpty()) {
			return new Rejected(errors.stream()
				.map(error -> new Violation(FailureClass.SCHEMA_VIOLATION, "schema:" + error.getKeyword(),
						error.getInstanceLocation().toString(), error.getMessage()))
				.toList());
		}
		try {
			return new Parsed(ContractJson.MAPPER.treeToValue(tree, AssessmentOutputV2.class));
		}
		catch (JacksonException ex) {
			// The schema and the records describe the same shape (ContractSchemaTest); a mismatch here
			// is still the response's shape, never a reason to accept it.
			return reject(FailureClass.SCHEMA_VIOLATION, "schema:mapping", ex.getOriginalMessage());
		}
	}

	/** A rejection of the whole response ({@code $}). */
	private static Result reject(FailureClass failureClass, String rule, String message) {
		return new Rejected(List.of(new Violation(failureClass, rule, "$", message)));
	}

	/** Outcome of {@link #parse(String)}. */
	public sealed interface Result permits Parsed, Rejected {
	}

	/** The response has the V2 shape; domain rules are still to be checked. */
	public record Parsed(AssessmentOutputV2 output) implements Result {
	}

	/** The response is not V2 JSON; all violations share one class. */
	public record Rejected(List<Violation> violations) implements Result {

		public Rejected {
			violations = List.copyOf(violations);
			if (violations.isEmpty()) {
				throw new IllegalArgumentException("a rejection needs at least one violation");
			}
		}

		public FailureClass failureClass() {
			return violations.getFirst().failureClass();
		}

	}

}
