package pl.szczurradar.api.agents.assessor.contract;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * A target (contract-v2.md §3, §6): the subject the author talks about and the author's stance on it.
 * Property order keeps {@code reference} last — no object may end with a nested object, because a
 * provider dropped closing braces there (plan, Critical Implementation Details).
 */
@JsonPropertyOrder({ "id", "kind", "anchor", "stance", "reference" })
public record Target(String id, TargetKind kind, Anchor anchor, Stance stance, Reference reference) {
}
