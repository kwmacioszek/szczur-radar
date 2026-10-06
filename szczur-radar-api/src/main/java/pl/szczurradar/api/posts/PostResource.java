package pl.szczurradar.api.posts;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The source-native type of a post: a microblog entry or a found link; {@link #value()} is the
 * stored and API value.
 */
public enum PostResource {

	ENTRY, LINK;

	@JsonValue
	public String value() {
		return name().toLowerCase(Locale.ROOT);
	}

}
