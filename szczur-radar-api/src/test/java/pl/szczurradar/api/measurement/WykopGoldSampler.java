package pl.szczurradar.api.measurement;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import pl.szczurradar.api.agents.assessor.Prefilter;
import pl.szczurradar.api.agents.assessor.contract.AssessmentInputV1;
import pl.szczurradar.api.agents.assessor.text.Whitespace;
import pl.szczurradar.api.measurement.GoldLabels.Post;
import pl.szczurradar.api.sources.wykop.UnmappableItemException;
import pl.szczurradar.api.sources.wykop.WykopApiClient;
import pl.szczurradar.api.sources.wykop.WykopItem;
import pl.szczurradar.api.sources.wykop.WykopMapper;
import pl.szczurradar.api.sources.wykop.WykopRepository;
import pl.szczurradar.api.sources.wykop.WykopSearchResult;

/**
 * Samples the gold set (plan phase 5): reads every #gielda entry and link of the requested days
 * through {@link WykopRepository} (Wykop search API), maps each with {@link WykopMapper}, anonymizes
 * it, drops entries without content ({@link Prefilter}), draws {@code gold.perDay} posts per day with
 * a fixed seed and writes {@code posts.json}. Raw API answers are never written.
 *
 * <pre>
 * WYKOP_KEY=… WYKOP_SECRET=… ./mvnw -Pmeasurement test -Dtest=WykopGoldSampler -Dgold.days=2026-10-01,2026-10-02
 * </pre>
 *
 * Optional: {@code -Dgold.perDay=60}, {@code -Dgold.seed=20261004}, {@code -Dgold.out=<path>}
 * (default {@link GoldLabels#POSTS}).
 * Each post records whether it had a picture, embed or poll ({@code media}) — the model sees text only.
 *
 * <p>Anonymization: URLs become {@code <url>} and e-mail addresses {@code <email>}; {@code @nick}
 * mentions and authors' nicks (as whole words, any case; only nicks of ≥ {@value #MIN_BARE_NICK_LENGTH}
 * characters, so that short nicks do not replace ordinary words) become {@code @uzytkownikN} (one
 * number per nick in the whole sample); white space is left as it is. The replaced bare nicks are
 * printed for review. Only closed days (before today, Warsaw time) can be sampled: an open day is
 * still changing while it is paged.
 */
@Tag("measurement")
class WykopGoldSampler {

	static final String TAG = "gielda";

	/** Not preceded by a word character or a dot, so the domain part of an e-mail is not a mention. */
	private static final Pattern MENTION = Pattern.compile("(?<![\\p{L}\\p{N}._-])@([\\p{L}\\p{N}_-]+)");

	private static final Pattern EMAIL = Pattern.compile("[\\p{L}\\p{N}._%+-]+@[\\p{L}\\p{N}-]+(?:\\.[\\p{L}\\p{N}-]+)+");

	private static final Pattern URL = Pattern.compile("(?i)(?:https?://|www\\.)[^" + Whitespace.regexClass() + "]*");

	static final int MIN_BARE_NICK_LENGTH = 4;

	@Test
	void samplesTheGoldPosts() throws IOException {
		List<LocalDate> days = Arrays.stream(required("gold.days").split(",")).map(String::strip).map(LocalDate::parse)
			.sorted().distinct().toList();
		assertThat(days).as("gold.days").isNotEmpty().doesNotContainAnyElementsOf(GoldLabels.EXCLUDED_DAYS);
		assertThat(days).as("gold.days must be closed days").allSatisfy(
				day -> assertThat(day).isBefore(LocalDate.now(WykopMapper.WYKOP_ZONE)));
		int perDay = Integer.getInteger("gold.perDay", 60);
		long seed = Long.getLong("gold.seed", 20261004L);
		Path out = Path.of(System.getProperty("gold.out", GoldLabels.POSTS.toString()));

		WykopRepository wykop = new WykopRepository(new WykopApiClient(env("WYKOP_KEY"), env("WYKOP_SECRET")));
		Map<LocalDate, List<WykopItem>> byDay = new TreeMap<>();
		for (LocalDate day : days) {
			WykopSearchResult result = wykop.itemsOfDay(TAG, day);
			byDay.put(day, result.items());
			System.out.printf("%s: %d elements read, %d skipped (unexpected shape)%n", day, result.items().size(),
					result.skippedElements());
		}

		Anonymizer anonymizer = new Anonymizer(
				byDay.values().stream().flatMap(List::stream).map(WykopGoldSampler::author).filter(nick -> nick != null).toList());
		List<Post> posts = new ArrayList<>();
		for (Map.Entry<LocalDate, List<WykopItem>> day : byDay.entrySet()) {
			List<Candidate> assessable = new ArrayList<>();
			int unmappable = 0;
			for (WykopItem raw : day.getValue()) {
				try {
					AssessmentInputV1 input = WykopMapper.map(anonymizer.apply(raw), TAG);
					if (Prefilter.isAssessable(input)) {
						assessable.add(new Candidate(raw, input));
					}
				}
				catch (UnmappableItemException ex) {
					unmappable++;
				}
			}
			List<Candidate> drawn = new ArrayList<>(assessable);
			Collections.shuffle(drawn, new Random(seed + day.getKey().toEpochDay()));
			drawn = new ArrayList<>(drawn.subList(0, Math.min(perDay, drawn.size())));
			drawn.sort(Comparator.comparing(candidate -> candidate.input().item().publishedAt()));
			for (int i = 0; i < drawn.size(); i++) {
				Candidate candidate = drawn.get(i);
				String postId = "%s-%s-%03d".formatted(TAG, day.getKey(), i);
				posts.add(new Post(postId, day.getKey(), media(candidate.raw()), candidate.input()));
			}
			System.out.printf("%s: %d elements, %d unmappable, %d without content, %d assessable, %d drawn%n",
					day.getKey(), day.getValue().size(), unmappable,
					day.getValue().size() - unmappable - assessable.size(), assessable.size(), drawn.size());
		}
		System.out.printf("nicks replaced: %d mentions, %d bare author nicks; URLs: %d; e-mails: %d%n",
				anonymizer.mentions, anonymizer.bareNicks, anonymizer.urls, anonymizer.emails);
		System.out.println("bare author nicks replaced (check they are not ordinary words): "
				+ anonymizer.replacedBareNicks);

		GoldLabels.writePosts(out, posts);
		System.out.println("wrote " + posts.size() + " posts to " + out.toAbsolutePath()
				+ " (with a picture: " + posts.stream().filter(post -> post.media().photo()).count() + ")");
		assertThat(posts).as("drawn posts").isNotEmpty();
	}

	/** A drawable post: the raw element (for media and the link) and its anonymized input. */
	private record Candidate(WykopItem raw, AssessmentInputV1 input) {
	}

	private static GoldLabels.Media media(WykopItem item) {
		WykopItem.Media media = item.media();
		return media == null ? GoldLabels.Media.NONE
				: new GoldLabels.Media(media.photo() != null, media.embed() != null, media.survey() != null);
	}

	private static @Nullable String author(WykopItem item) {
		return item.author() == null ? null : item.author().username();
	}

	private static String required(String property) {
		String value = System.getProperty(property);
		if (value == null || value.isBlank()) {
			throw new IllegalStateException("-D" + property + " is required, e.g. -Dgold.days=2026-10-01,2026-10-02");
		}
		return value;
	}

	private static String env(String name) {
		String value = System.getenv(name);
		if (value == null || value.isBlank()) {
			throw new IllegalStateException(name + " is not set (Wykop API key and secret from your shell)");
		}
		return value;
	}

	/** Replaces nicks and URLs in an element's text, numbering nicks consistently across the sample. */
	static final class Anonymizer {

		private final Map<String, Integer> numbers = new LinkedHashMap<>();

		private final @Nullable Pattern bareAuthors;

		int mentions;

		int bareNicks;

		int urls;

		int emails;

		final Set<String> replacedBareNicks = new TreeSet<>();

		Anonymizer(List<String> authors) {
			List<String> nicks = authors.stream()
				.filter(nick -> nick.length() >= MIN_BARE_NICK_LENGTH)
				.map(nick -> nick.toLowerCase(Locale.ROOT))
				.distinct()
				.sorted(Comparator.comparingInt(String::length).reversed())
				.map(Pattern::quote)
				.toList();
			this.bareAuthors = nicks.isEmpty() ? null
					: Pattern.compile("(?iu)(?<![\\p{L}\\p{N}_@-])(?:" + String.join("|", nicks) + ")(?![\\p{L}\\p{N}_-])");
		}

		WykopItem apply(WykopItem item) {
			return new WykopItem(item.resource(), item.id(), item.createdAt(), text(item.content()), text(item.title()),
					text(item.description()), item.tags(), null, item.slug(), item.media());
		}

		@Nullable String text(@Nullable String text) {
			if (text == null) {
				return null;
			}
			Matcher url = URL.matcher(text);
			StringBuilder out = new StringBuilder();
			while (url.find()) {
				urls++;
				url.appendReplacement(out, "<url>");
			}
			url.appendTail(out);
			Matcher email = EMAIL.matcher(out.toString());
			out = new StringBuilder();
			while (email.find()) {
				emails++;
				email.appendReplacement(out, "<email>");
			}
			email.appendTail(out);
			Matcher mention = MENTION.matcher(out.toString());
			out = new StringBuilder();
			while (mention.find()) {
				mentions++;
				mention.appendReplacement(out, Matcher.quoteReplacement(alias(mention.group(1))));
			}
			mention.appendTail(out);
			if (bareAuthors == null) {
				return out.toString();
			}
			Matcher bare = bareAuthors.matcher(out.toString());
			out = new StringBuilder();
			while (bare.find()) {
				bareNicks++;
				replacedBareNicks.add(bare.group());
				bare.appendReplacement(out, Matcher.quoteReplacement(alias(bare.group())));
			}
			bare.appendTail(out);
			return out.toString();
		}

		private String alias(String nick) {
			if (nick.matches("uzytkownik\\d+")) {
				return "@" + nick;
			}
			return "@uzytkownik" + numbers.computeIfAbsent(nick.toLowerCase(Locale.ROOT), key -> numbers.size() + 1);
		}

	}

}
