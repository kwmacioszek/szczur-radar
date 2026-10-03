package pl.szczurradar.api.agents.assessor.contract;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * The model's output (contract-v2.md §3): relevance, targets with the author's stance, claims with
 * a quote, flags. The JSON shape is {@code assessor/assessment-output-v2.schema.json}; the
 * component order here is the schema's property order ({@code ContractSchemaTest}).
 *
 * <p>An instance only says the JSON had this shape — whether it satisfies the contract is decided by
 * {@code OutputParser} (shape, limits) and {@code RuleBasedValidator} (D1–D15).
 */
@JsonPropertyOrder({ "relevance", "modifiers", "targets", "claims", "flags" })
public record AssessmentOutputV2(Relevance relevance, List<Modifier> modifiers, List<Target> targets,
		List<Claim> claims, List<Flag> flags) {

	/** The contract version recorded in an assessment's provenance (never in the model's JSON). */
	public static final String CONTRACT_VERSION = "V2";

	public AssessmentOutputV2 {
		modifiers = List.copyOf(modifiers);
		targets = List.copyOf(targets);
		claims = List.copyOf(claims);
		flags = List.copyOf(flags);
	}

}
