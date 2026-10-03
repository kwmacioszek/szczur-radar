package pl.szczurradar.api.agents.assessor.contract;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * A claim (contract-v2.md §3): what the author states, with exactly one verbatim quote from
 * {@code item}. Property order keeps {@code statement} after {@code evidence} so the object does not
 * end with a nested object (see {@link Target}).
 */
@JsonPropertyOrder({ "id", "kind", "attribution", "target_ids", "evidence", "statement" })
public record Claim(String id, ClaimKind kind, Attribution attribution,
		@JsonProperty("target_ids") List<String> targetIds, Evidence evidence, String statement) {

	public Claim {
		targetIds = List.copyOf(targetIds);
	}

}
