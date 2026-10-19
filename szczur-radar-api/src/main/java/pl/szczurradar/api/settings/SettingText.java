package pl.szczurradar.api.settings;

import org.jspecify.annotations.Nullable;

/** The text of an environment setting as the settings read it: unset and blank both mean empty. */
public final class SettingText {

	/**
	 * The longest part of a refused value a message repeats, so that a key pasted into the wrong setting
	 * stays out of the logs.
	 */
	static final int ECHO_LIMIT = 16;

	private SettingText() {
	}

	/** {@code value} without white space at both ends ({@link String#strip()}); {@code ""} when unset. */
	public static String trimmed(@Nullable String value) {
		return value == null ? "" : value.strip();
	}

	/**
	 * {@code value} as a message about a refused setting may repeat it: whole up to {@value #ECHO_LIMIT}
	 * characters, otherwise its start and {@code …}.
	 */
	public static String echoed(String value) {
		if (value.codePointCount(0, value.length()) <= ECHO_LIMIT) {
			return value;
		}
		return value.substring(0, value.offsetByCodePoints(0, ECHO_LIMIT)) + "…";
	}

}
