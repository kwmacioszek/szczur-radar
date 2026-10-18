package pl.szczurradar.api.settings;

import org.jspecify.annotations.Nullable;

/** The text of an environment setting as the settings read it: unset and blank both mean empty. */
public final class SettingText {

	private SettingText() {
	}

	/** {@code value} without white space at both ends ({@link String#strip()}); {@code ""} when unset. */
	public static String trimmed(@Nullable String value) {
		return value == null ? "" : value.strip();
	}

}
