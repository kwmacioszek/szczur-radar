package pl.szczurradar.api.sources;

import java.util.Arrays;
import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonValue;

/** A data source of posts; {@link #value()} is the stored and API value. */
public enum Source {

	WYKOP,

	/**
	 * Registered (the value in every {@code CHECK} set of a source, {@code V12}), not implemented: no
	 * {@link SourceFetcher} or {@link RawInputMapper} until S-27 ({@code sources/CLAUDE.md}, step 1), so it
	 * is never configured and a range request naming it is refused.
	 */
	REDDIT;

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
