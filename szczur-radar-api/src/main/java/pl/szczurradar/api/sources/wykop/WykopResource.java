package pl.szczurradar.api.sources.wykop;

import java.util.Arrays;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/** The Wykop resource types the assessment maps; {@link #value()} is the API's {@code resource}. */
public enum WykopResource {

	/** A microblog entry → {@code post}. */
	ENTRY("entry"),

	/** A found link → {@code link}. */
	LINK("link");

	private final String value;

	WykopResource(String value) {
		this.value = value;
	}

	public String value() {
		return value;
	}

	/** The type of an API {@code resource} value; empty for any other (comments, unknown types). */
	public static Optional<WykopResource> of(@Nullable String resource) {
		return Arrays.stream(values()).filter(type -> type.value.equals(resource)).findFirst();
	}

}
