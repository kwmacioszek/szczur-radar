package pl.szczurradar.api.raw;

/** The media type of a stored payload before compression; {@link #value()} is the stored value. */
public enum RawMediaType {

	JSON("application/json");

	private final String value;

	RawMediaType(String value) {
		this.value = value;
	}

	public String value() {
		return value;
	}

}
