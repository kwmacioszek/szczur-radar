package pl.szczurradar.api.text;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/**
 * The language a text shows evidence of, if any (S-36): Polish first — a Polish letter, or at least
 * {@link #MIN_WORDS} occurrences of Polish function words —, then English — at least {@link #MIN_WORDS}
 * occurrences of English function words —, otherwise none. Polish goes first, so a Polish text quoting English
 * stays Polish. A short text without function words (numbers, tickers, a few nouns) has no evidence and is
 * never taken for either language.
 *
 * <p>The text is read in Unicode NFC, so a decomposed letter counts as its composed one. A word is a run of
 * Unicode letters after {@link Locale#ROOT} lower-casing; every occurrence counts. Neither
 * list holds a word both languages use ({@code to, on, by, do, we, a, i, no, as, was, but}). The Polish list is
 * S-28's ({@code measurement/RedditMeasures}); the English one and the rule's check on production data are in
 * {@code context/changes/statement-language-quality/evidence/README.md}.
 */
public final class LanguageEvidence {

	/** The fewest occurrences of a language's function words that count as evidence of it. */
	public static final int MIN_WORDS = 2;

	private static final Pattern POLISH_LETTER = Pattern.compile("[ąćęłńóśźż]",
			Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

	private static final Pattern WORD = Pattern.compile("\\p{L}+");

	private static final Set<String> POLISH_WORDS = Set.of("że", "się", "nie", "jest", "oraz", "ale", "czy", "będzie",
			"które", "jednak", "dla", "przez", "jako", "lub");

	private static final Set<String> ENGLISH_WORDS = Set.of("the", "is", "are", "were", "of", "and", "that", "this",
			"will", "with", "for", "has", "have", "be", "not", "from", "its", "their", "than", "which", "would", "could",
			"should", "it", "an", "or", "in", "at", "if", "because", "about", "after", "before", "while");

	private LanguageEvidence() {
	}

	/** The language {@code text} shows evidence of; empty for {@code null}, a text without letters or no evidence. */
	public static Optional<Language> of(@Nullable String raw) {
		if (raw == null) {
			return Optional.empty();
		}
		// A decomposed letter (e + U+0328) would match neither the Polish letters nor one word (review F5).
		String text = Normalizer.normalize(raw, Normalizer.Form.NFC);
		if (POLISH_LETTER.matcher(text).find()) {
			return Optional.of(Language.PL);
		}
		int polish = 0;
		int english = 0;
		Matcher words = WORD.matcher(text.toLowerCase(Locale.ROOT));
		while (words.find()) {
			String word = words.group();
			if (POLISH_WORDS.contains(word)) {
				polish++;
			}
			else if (ENGLISH_WORDS.contains(word)) {
				english++;
			}
		}
		if (polish >= MIN_WORDS) {
			return Optional.of(Language.PL);
		}
		return english >= MIN_WORDS ? Optional.of(Language.EN) : Optional.empty();
	}

}
