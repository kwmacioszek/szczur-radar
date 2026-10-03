package pl.szczurradar.api.agents.assessor.validation.rules;

import java.util.ArrayList;
import java.util.List;

import pl.szczurradar.api.agents.assessor.contract.AssessmentInputV1;
import pl.szczurradar.api.agents.assessor.contract.AssessmentOutputV2;
import pl.szczurradar.api.agents.assessor.contract.Quote;
import pl.szczurradar.api.agents.assessor.text.Whitespace;

/**
 * Every quote of an output with its path, and the steps of the quote invariant (contract-v2.md §10)
 * shared by D5, D7 and D6. Each quote gets at most one of the three: D5 (no such field) stops it,
 * then D7 (empty after normalization), then D6 (not a fragment).
 */
record QuotedText(String path, Quote quote) {

	/** Anchors first, then evidence, in document order. */
	static List<QuotedText> all(AssessmentOutputV2 output) {
		List<QuotedText> quotes = new ArrayList<>();
		for (int i = 0; i < output.targets().size(); i++) {
			quotes.add(new QuotedText("$.targets[" + i + "].anchor", output.targets().get(i).anchor()));
		}
		for (int i = 0; i < output.claims().size(); i++) {
			quotes.add(new QuotedText("$.claims[" + i + "].evidence", output.claims().get(i).evidence()));
		}
		return quotes;
	}

	/** The quoted field's text, or {@code null} when D5 fails. */
	String fieldText(AssessmentInputV1 input) {
		return input.text(quote.document(), quote.field());
	}

	boolean resolves(AssessmentInputV1 input) {
		return fieldText(input) != null;
	}

	/** D5 passed and the normalized quote has at least one character (D7 passed). */
	boolean resolvesToText(AssessmentInputV1 input) {
		return resolves(input) && !Whitespace.normalize(quote.quote()).isEmpty();
	}

	/** {@code document.field}, e.g. {@code item.body}. */
	String location() {
		return quote.document().value() + "." + quote.field().value();
	}

}
