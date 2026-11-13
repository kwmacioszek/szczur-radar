package pl.szczurradar.api.text;

import java.util.Arrays;

/**
 * A language of posts and of the model's text; {@link #value()} is the stored value ({@code posts.language},
 * {@code V16__posts_language.sql}). A source declares the language of its posts
 * ({@code sources/Source.language()}); {@link LanguageEvidence} tells a text's language when it can.
 */
public enum Language {

	PL("pl"),

	EN("en");

	private final String value;

	Language(String value) {
		this.value = value;
	}

	public String value() {
		return value;
	}

	/** The language stored as {@code value}. */
	public static Language fromValue(String value) {
		return Arrays.stream(values())
			.filter(language -> language.value.equals(value))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("unknown language: " + value));
	}

}
