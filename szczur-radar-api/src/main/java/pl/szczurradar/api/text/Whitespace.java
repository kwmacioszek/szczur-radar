package pl.szczurradar.api.text;

/**
 * The white space of contract-v2.md §10 — the one definition used by the validator (quote
 * invariant), the prefilter (§11), the input model, the source mappers, the LLM clients (blank
 * answers) and the gold metrics.
 *
 * <p>Neither {@link Character#isWhitespace(int)} nor Apache {@code StringUtils.normalizeSpace}
 * matches the contract's set (both miss U+2007, U+202F and U+0085), so the set is listed here.
 */
public final class Whitespace {

	private Whitespace() {
	}

	/** Whether {@code codePoint} is in the white space set of contract-v2.md §10 pt 1. */
	public static boolean isWhitespace(int codePoint) {
		return (codePoint >= 0x0009 && codePoint <= 0x000D)
				|| (codePoint >= 0x001C && codePoint <= 0x001F)
				|| codePoint == 0x0020
				|| codePoint == 0x0085
				|| codePoint == 0x00A0
				|| codePoint == 0x1680
				|| (codePoint >= 0x2000 && codePoint <= 0x200A)
				|| codePoint == 0x2028
				|| codePoint == 0x2029
				|| codePoint == 0x202F
				|| codePoint == 0x205F
				|| codePoint == 0x3000;
	}

	/**
	 * White space normalization of §10: every white space character becomes a space, runs of spaces
	 * become one space, both ends are trimmed. Nothing else changes (case, markdown, quotes, NFC).
	 */
	public static String normalize(String text) {
		StringBuilder normalized = new StringBuilder(text.length());
		boolean pendingSpace = false;
		for (int i = 0; i < text.length();) {
			int codePoint = text.codePointAt(i);
			i += Character.charCount(codePoint);
			if (isWhitespace(codePoint)) {
				pendingSpace = true;
				continue;
			}
			if (pendingSpace && !normalized.isEmpty()) {
				normalized.append(' ');
			}
			pendingSpace = false;
			normalized.appendCodePoint(codePoint);
		}
		return normalized.toString();
	}

	/** {@code text} without white space (§10 set) at both ends; the inside is left as is. */
	public static String strip(String text) {
		int start = 0;
		while (start < text.length() && isWhitespace(text.codePointAt(start))) {
			start += Character.charCount(text.codePointAt(start));
		}
		int end = text.length();
		while (end > start && isWhitespace(text.codePointBefore(end))) {
			end -= Character.charCount(text.codePointBefore(end));
		}
		return text.substring(start, end);
	}

	/**
	 * A regex character class body matching exactly the §10 set (for use inside {@code [...]} or
	 * {@code [^...]}), so patterns such as "URL up to the nearest white space" agree with this class.
	 */
	public static String regexClass() {
		return "\\x{9}-\\x{D}\\x{1C}-\\x{1F}\\x{20}\\x{85}\\x{A0}\\x{1680}\\x{2000}-\\x{200A}"
				+ "\\x{2028}\\x{2029}\\x{202F}\\x{205F}\\x{3000}";
	}

}
