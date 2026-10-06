package pl.szczurradar.api.agents.assessor.contract;

/**
 * A quote object {@code {document, field, quote}} (contract-v2.md §3, §10): a verbatim fragment of
 * one text field of the input, checked by the quote invariant.
 */
public sealed interface Quote permits Anchor, Evidence {

	Document document();

	Field field();

	String quote();

}
