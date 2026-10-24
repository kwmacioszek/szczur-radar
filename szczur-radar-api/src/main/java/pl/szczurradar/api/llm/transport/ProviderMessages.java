package pl.szczurradar.api.llm.transport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/**
 * Cleans provider error texts before they become exception messages (which callers record): the
 * configured key is replaced with {@code <hidden>} — some providers quote it in 401 answers — and
 * the text is capped at {@value #MAX_LENGTH} characters. The same goes for the causes a client
 * attaches ({@link #cleanCause}), since a log prints the whole chain.
 */
public record ProviderMessages(String apiKey) {

	public static final int MAX_LENGTH = 500;

	/** How many elements of a cause chain {@link #cleanCause} copies at most. */
	static final int MAX_CAUSE_DEPTH = 16;

	public String clean(String text) {
		String cleaned = apiKey.isBlank() ? text : text.replace(apiKey, "<hidden>");
		return cleaned.length() > MAX_LENGTH ? cleaned.substring(0, MAX_LENGTH) + "…" : cleaned;
	}

	/**
	 * A copy of {@code error}'s cause chain to attach as a cause instead of the SDK's own exception, whose
	 * message may quote the key: each element becomes a {@link CleanedProviderException} with the
	 * message {@code <original class>: <cleaned message>} (only the class name when the original has no
	 * message), the original's stack trace and the copy of its cause. Suppressed exceptions are left out;
	 * the copy ends after {@value #MAX_CAUSE_DEPTH} elements or where the chain comes back to an element
	 * it already has. Classify by the original chain first: the copy keeps none of its types.
	 */
	public @Nullable Throwable cleanCause(@Nullable Throwable error) {
		List<Throwable> chain = new ArrayList<>();
		Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		for (Throwable cause = error; cause != null && chain.size() < MAX_CAUSE_DEPTH && seen.add(cause);
				cause = cause.getCause()) {
			chain.add(cause);
		}
		Throwable cleaned = null;
		for (Throwable original : chain.reversed()) {
			String message = original.getMessage();
			String name = original.getClass().getName();
			cleaned = new CleanedProviderException(message == null ? name : name + ": " + clean(message), cleaned,
					original.getStackTrace());
		}
		return cleaned;
	}

	/** Never the key: every client holds one of these, and a client may end up in a log or dump. */
	@Override
	public String toString() {
		return "ProviderMessages[apiKey=<hidden>]";
	}

}
