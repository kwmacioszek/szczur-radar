package pl.szczurradar.api.text;

import org.jspecify.annotations.Nullable;

/**
 * U+0000 (NUL), which PostgreSQL rejects in {@code TEXT} and, as the escape {@code \u0000}, in
 * {@code jsonb}. A text holding it would fail every attempt to store its post or assessment, so it
 * never reaches the database: source text loses it (it carries no meaning in a post), and what the
 * model or the validator wrote gets U+FFFD in its place.
 */
public final class Nul {

	private static final String NUL = "\u0000";

	private static final String REPLACEMENT = "�";

	private Nul() {
	}

	/** {@code text} without U+0000; {@code null} stays {@code null}. */
	public static @Nullable String removed(@Nullable String text) {
		return text == null ? null : text.replace(NUL, "");
	}

	/** {@code text} with U+FFFD for every U+0000; {@code null} stays {@code null}. */
	public static @Nullable String replaced(@Nullable String text) {
		return text == null ? null : text.replace(NUL, REPLACEMENT);
	}

	/**
	 * JSON text with every {@code \u0000} escape (how Jackson writes U+0000) turned into
	 * {@code �}. An escaped backslash followed by the letters {@code u0000} changes too; the result
	 * is still valid JSON.
	 */
	public static String replacedInJson(String json) {
		return json.replace("\\u0000", "\\uFFFD");
	}

}
