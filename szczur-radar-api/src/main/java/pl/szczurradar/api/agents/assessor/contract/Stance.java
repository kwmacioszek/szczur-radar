package pl.szczurradar.api.agents.assessor.contract;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * The author's stance on one target (contract-v2.md §3, §7). {@code assertiveness} is {@code null}
 * for directions without one (table §7.2).
 */
@JsonPropertyOrder({ "direction", "assertiveness", "claim_ids" })
public record Stance(Direction direction, Assertiveness assertiveness,
		@JsonProperty("claim_ids") List<String> claimIds) {

	public Stance {
		claimIds = List.copyOf(claimIds);
	}

}
