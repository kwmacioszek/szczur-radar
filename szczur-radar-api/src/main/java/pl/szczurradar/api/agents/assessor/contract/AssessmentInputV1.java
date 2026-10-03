package pl.szczurradar.api.agents.assessor.contract;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * The model's input (contract-v2.md §2): the single statement to assess ({@code item}) and the
 * optional parents used only to interpret references. Invalid input cannot be constructed.
 */
@JsonPropertyOrder({ "schema_version", "item", "context" })
public record AssessmentInputV1(@JsonProperty("schema_version") String schemaVersion, Item item, Context context) {

	/** The constant value of {@code schema_version}. */
	public static final String SCHEMA_VERSION = "AssessmentInputV1";

	public AssessmentInputV1 {
		if (!SCHEMA_VERSION.equals(schemaVersion)) {
			throw new IllegalArgumentException("schema_version must be " + SCHEMA_VERSION + ", was " + schemaVersion);
		}
		Objects.requireNonNull(item, "item");
		Objects.requireNonNull(context, "context");
	}

	/** An input with the constant {@code schema_version}. */
	public static AssessmentInputV1 of(Item item, Context context) {
		return new AssessmentInputV1(SCHEMA_VERSION, item, context);
	}

	/** The text of {@code field} in {@code document}, or {@code null} if either is absent. */
	public String text(Document document, Field field) {
		TextFields source = switch (document) {
			case ITEM -> item;
			case PARENT_POST -> context.parentPost();
			case PARENT_COMMENT -> context.parentComment();
		};
		if (source == null) {
			return null;
		}
		return switch (field) {
			case TITLE -> source.title();
			case BODY -> source.body();
		};
	}

}
