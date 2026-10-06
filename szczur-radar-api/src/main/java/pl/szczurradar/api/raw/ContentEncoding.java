package pl.szczurradar.api.raw;

/** How a stored payload is compressed; {@link #value()} is the stored value. */
public enum ContentEncoding {

	GZIP("gzip");

	private final String value;

	ContentEncoding(String value) {
		this.value = value;
	}

	public String value() {
		return value;
	}

}
