package pl.szczurradar.api.agents.assessor.contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/** {@code context} of the input (contract-v2.md §2). In the MVP both parents are {@code null}. */
@JsonPropertyOrder({ "parent_post", "parent_comment" })
public record Context(@JsonProperty("parent_post") Parent parentPost,
		@JsonProperty("parent_comment") Parent parentComment) {

	/** The MVP context: no parents. */
	public static final Context NONE = new Context(null, null);

}
