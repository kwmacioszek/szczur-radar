package pl.szczurradar.api.agents.assessor;

import java.util.List;
import java.util.regex.Pattern;

import pl.szczurradar.api.agents.assessor.contract.AssessmentInputV1;
import pl.szczurradar.api.agents.assessor.contract.AssessmentOutputV2;
import pl.szczurradar.api.agents.assessor.contract.Flag;
import pl.szczurradar.api.agents.assessor.contract.Relevance;
import pl.szczurradar.api.text.Whitespace;

/**
 * The no-content prefilter of contract-v2.md §11 — one definition for production, the gold sample and
 * tests. An entry without content (a bare tag, link or mention) never reaches the model and gets
 * {@link #noContentOutput()} instead.
 */
public final class Prefilter {

	/** Fewer code points than this after stripping = no content. */
	static final int MIN_CONTENT_CODE_POINTS = 3;

	/**
	 * What §11 removes, URLs first (a URL may contain {@code #}): {@code http(s)://} up to the nearest
	 * white space (§10 set), the {@code <url>} marker of anonymized samples, tags and mentions
	 * ({@code #}/{@code @} + word characters {@code [\p{L}\p{N}_]}).
	 */
	private static final List<Pattern> REMOVED = List.of(
			Pattern.compile("https?://[^" + Whitespace.regexClass() + "]*"), Pattern.compile("<url>"),
			Pattern.compile("#[\\p{L}\\p{N}_]+"), Pattern.compile("@[\\p{L}\\p{N}_]+"));

	private Prefilter() {
	}

	/** Whether the entry has content and goes to the model (§11). */
	public static boolean isAssessable(AssessmentInputV1 input) {
		String content = strip(input.item().title()) + strip(input.item().body());
		return content.codePointCount(0, content.length()) >= MIN_CONTENT_CODE_POINTS;
	}

	/** The ready-made output for an entry without content (§11). */
	public static AssessmentOutputV2 noContentOutput() {
		return new AssessmentOutputV2(Relevance.UNCLEAR, List.of(), List.of(), List.of(),
				List.of(Flag.INSUFFICIENT_CONTENT));
	}

	private static String strip(String text) {
		if (text == null) {
			return "";
		}
		String stripped = text;
		for (Pattern pattern : REMOVED) {
			stripped = pattern.matcher(stripped).replaceAll("");
		}
		return Whitespace.strip(stripped);
	}

}
