package pl.szczurradar.api.posts;

import org.jspecify.annotations.Nullable;

import pl.szczurradar.api.agents.assessor.contract.Direction;
import pl.szczurradar.api.agents.assessor.contract.TargetKind;
import pl.szczurradar.api.text.Whitespace;

/**
 * The feed filter (FR-011a): conditions on one target of a post's latest {@code ok} assessment, each
 * {@code null} when not given. A filter without conditions is no filter ({@link #isEmpty()}).
 *
 * @param quote text the target's {@code anchor.quote} contains, case-insensitively; stripped of white space
 *     (§10 set) at both ends, and {@code null} when nothing is left
 * @param kind the target's {@code kind}
 * @param direction the {@code direction} of the author's stance on that target
 */
public record PostFilter(@Nullable String quote, @Nullable TargetKind kind, @Nullable Direction direction) {

	/** The escape character of {@link #quotePattern()}, named in the query's {@code ESCAPE}. */
	public static final String LIKE_ESCAPE = "!";

	public PostFilter {
		if (quote != null) {
			quote = Whitespace.strip(quote);
			if (quote.isEmpty()) {
				quote = null;
			}
		}
	}

	/** Whether no condition is given: the feed as without a filter. */
	public boolean isEmpty() {
		return quote == null && kind == null && direction == null;
	}

	/**
	 * The {@code ILIKE} pattern "contains {@link #quote()}": {@code %}, {@code _} and {@link #LIKE_ESCAPE}
	 * escaped, so they match only themselves; {@code null} without a quote.
	 */
	public @Nullable String quotePattern() {
		if (quote == null) {
			return null;
		}
		String escaped = quote.replace(LIKE_ESCAPE, LIKE_ESCAPE + LIKE_ESCAPE)
			.replace("%", LIKE_ESCAPE + "%")
			.replace("_", LIKE_ESCAPE + "_");
		return "%" + escaped + "%";
	}

}
