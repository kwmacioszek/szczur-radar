package pl.szczurradar.api.llm;

/**
 * Cleans provider error texts before they become exception messages (which callers record): the
 * configured key is replaced with {@code <hidden>} — some providers quote it in 401 answers — and
 * the text is capped at {@value #MAX_LENGTH} characters.
 */
record ProviderMessages(String apiKey) {

	static final int MAX_LENGTH = 500;

	String clean(String text) {
		String cleaned = apiKey.isBlank() ? text : text.replace(apiKey, "<hidden>");
		return cleaned.length() > MAX_LENGTH ? cleaned.substring(0, MAX_LENGTH) + "…" : cleaned;
	}

	/** Never the key: every client holds one of these, and a client may end up in a log or dump. */
	@Override
	public String toString() {
		return "ProviderMessages[apiKey=<hidden>]";
	}

}
