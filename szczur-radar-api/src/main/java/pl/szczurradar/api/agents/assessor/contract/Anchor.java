package pl.szczurradar.api.agents.assessor.contract;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/** {@code target.anchor}: the shortest fragment naming the target (contract-v2.md §6.2), 1–100 characters. */
@JsonPropertyOrder({ "document", "field", "quote" })
public record Anchor(Document document, Field field, String quote) implements Quote {
}
