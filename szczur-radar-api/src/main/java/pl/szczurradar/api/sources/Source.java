package pl.szczurradar.api.sources;

import java.util.Arrays;
import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonValue;

/** A data source of posts; {@link #value()} is the stored and API value. */
public enum Source {

	WYKOP;

	@JsonValue
	public String value() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** The source stored as {@code value}. */
	public static Source fromValue(String value) {
		return Arrays.stream(values())
			.filter(source -> source.value().equals(value))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("unknown source: " + value));
	}

}
