package pl.szczurradar.api.sources;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonValue;

/** A data source of posts; {@link #value()} is the stored and API value. */
public enum Source {

	WYKOP;

	@JsonValue
	public String value() {
		return name().toLowerCase(Locale.ROOT);
	}

}
