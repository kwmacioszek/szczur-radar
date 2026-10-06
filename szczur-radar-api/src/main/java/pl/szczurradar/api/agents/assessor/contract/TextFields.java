package pl.szczurradar.api.agents.assessor.contract;

import pl.szczurradar.api.agents.assessor.text.Whitespace;

/** A document with the two quotable text fields of contract-v2.md §2: {@code title} and {@code body}. */
public sealed interface TextFields permits Item, Parent {

	String title();

	String body();

	/**
	 * Checks the text rules of §2: absent text is {@code null} (never empty or white space only, §10
	 * set) and at least one of the two fields has text.
	 */
	static void requireText(String title, String body, String what) {
		requireNullOrText(title, what + ".title");
		requireNullOrText(body, what + ".body");
		if (title == null && body == null) {
			throw new IllegalArgumentException(what + " must have a title or a body");
		}
	}

	private static void requireNullOrText(String text, String path) {
		if (text != null && Whitespace.normalize(text).isEmpty()) {
			throw new IllegalArgumentException(path + " must be null instead of empty or white space only");
		}
	}

}
