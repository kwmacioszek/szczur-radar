package pl.szczurradar.api.agents.assessor.contract;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/** {@code claim.evidence}: the claim's single quote from {@code item} (contract-v2.md §3), 1–320 characters. */
@JsonPropertyOrder({ "document", "field", "quote" })
public record Evidence(Document document, Field field, String quote) implements Quote {
}
