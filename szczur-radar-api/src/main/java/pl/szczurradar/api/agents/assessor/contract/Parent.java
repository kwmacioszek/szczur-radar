package pl.szczurradar.api.agents.assessor.contract;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/** A parent post or comment in {@code context} (contract-v2.md §2); {@code null} when absent. */
@JsonPropertyOrder({ "title", "body" })
public record Parent(String title, String body) implements TextFields {

	public Parent {
		TextFields.requireText(title, body, "parent");
	}

}
