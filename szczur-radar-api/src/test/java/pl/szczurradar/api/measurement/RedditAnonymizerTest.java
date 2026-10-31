package pl.szczurradar.api.measurement;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import pl.szczurradar.api.agents.assessor.contract.AssessmentInputV1;
import pl.szczurradar.api.measurement.WykopGoldSampler.Anonymizer;
import pl.szczurradar.api.sources.reddit.RedditItem;
import pl.szczurradar.api.sources.reddit.RedditMapper;

/** The Reddit sample's anonymization ({@link RedditSampler}), offline. */
class RedditAnonymizerTest {

	private static String anonymized(String text, String... authors) {
		return new Anonymizer(List.of(authors)).text(RedditSampler.userLinksAsMentions(text));
	}

	@Test
	void userLinksBecomeNumberedAliasesConsistentAcrossTheSample() {
		Anonymizer anonymizer = new Anonymizer(List.of());

		assertThat(
				anonymizer.text(RedditSampler.userLinksAsMentions("Thanks u/Some_Nick and /u/other-nick, and /u/some_nick")))
			.isEqualTo("Thanks @uzytkownik1 and @uzytkownik2, and @uzytkownik1");
		assertThat(anonymizer.text(RedditSampler.userLinksAsMentions("cc u/other-nick"))).isEqualTo("cc @uzytkownik2");
	}

	@Test
	void userLinksAreFoundWhereverTheCheckOnPostsJsonWouldFindThem() {
		assertThat(anonymized("(u/nick1), **/u/nick2** [u/nick3](x) bought/u/nick4")).isEqualTo(
				"(@uzytkownik1), **@uzytkownik2** [@uzytkownik3](x) bought/@uzytkownik4");
		assertThat(anonymized("U/Capital /U/Second")).isEqualTo("@uzytkownik1 @uzytkownik2");
	}

	@Test
	void aUserLinkRightAfterADotOrDashStillLosesItsNick() {
		assertThat(anonymized("thanks.u/nick1 -u/nick2 _u/nick3"))
			.isEqualTo("thanks. @uzytkownik1 - @uzytkownik2 _ @uzytkownik3");
	}

	@Test
	void aNickWithRedditsEscapedUnderscoreIsReplacedWhole() {
		assertThat(anonymized("as u/some\\_nick said")).isEqualTo("as @uzytkownik1 said");
	}

	@Test
	void urlsWithASchemeOrABareWwwBecomeMarkers() {
		assertThat(anonymized("See https://reddit.com/u/nick?a=b, http://x.pl and www.sec.gov/filing and WWW.SEC.GOV/x\nend"))
			.isEqualTo("See <url> <url> and <url> and <url>\nend");
	}

	@Test
	void eMailAddressesAreReplaced() {
		assertThat(anonymized("write to john.smith@gmail.com or u/nick1")).isEqualTo("write to <email> or @uzytkownik1");
	}

	@Test
	void authorsNicksInTheTextAreReplacedAsWholeWords() {
		assertThat(anonymized("as Trader99 said, and Trader999 agrees", "Trader99"))
			.isEqualTo("as @uzytkownik1 said, and Trader999 agrees");
	}

	@Test
	void ordinaryWordsStayAsTheyAre() {
		String text = "Menu/options, you/them, EU/USD, XAU/USD, u/s and U/S stay; r/stocks stays too";

		assertThat(anonymized(text)).isEqualTo(text);
	}

	@Test
	void textWithoutAnythingToReplaceIsNotChangedAndNoTextStaysNoText() {
		assertThat(RedditSampler.userLinksAsMentions("Plain text\n\n  with white space"))
			.isEqualTo("Plain text\n\n  with white space");
		assertThat(RedditSampler.userLinksAsMentions(null)).isNull();
	}

	@Test
	void deletedAccountsAndAutoModeratorAreNotNicks() {
		List<RedditItem> items = List.of(item("t", "b", "[deleted]"), item("t", "b", "AutoModerator"), item("t", "b", null),
				item("t", "b", "Trader99"));

		assertThat(RedditSampler.authors(items)).containsExactly("Trader99");
	}

	@Test
	void anItemLosesWhatLeadsToItsAuthorAndKeepsTheRest() {
		RedditItem raw = item("Title by u/nick1", "Body at https://x.pl", "Trader99");

		RedditItem anonymized = RedditSampler.anonymized(raw, new Anonymizer(List.of("Trader99")));

		assertThat(anonymized.author()).isNull();
		assertThat(anonymized.url()).isNull();
		assertThat(anonymized.permalink()).isNull();
		assertThat(anonymized.title()).isEqualTo("Title by @uzytkownik1");
		assertThat(anonymized.selftext()).isEqualTo("Body at <url>");
		assertThat(anonymized.id()).isEqualTo(raw.id());
		assertThat(anonymized.createdUtc()).isEqualTo(raw.createdUtc());
	}

	@Test
	void theAnonymizedInputHasTheFieldsOfProductionsMapping() {
		for (Boolean isSelf : new Boolean[] { true, false, null }) {
			RedditItem raw = new RedditItem("1abc", "ValueInvesting", "Title by u/nick1", "Body", isSelf, null, null, "Trader99",
					1_761_000_000L, null);

			AssessmentInputV1 production = RedditMapper.map(raw);
			AssessmentInputV1 sampled = RedditMapper.map(RedditSampler.anonymized(raw, new Anonymizer(List.of("Trader99"))));

			assertThat(sampled.item().channels()).isEqualTo(production.item().channels());
			assertThat(sampled.item().itemType()).isEqualTo(production.item().itemType());
			assertThat(sampled.item().publishedAt()).isEqualTo(production.item().publishedAt());
			assertThat(sampled.item().body()).isEqualTo(production.item().body());
		}
	}

	private static RedditItem item(String title, String selftext, @Nullable String author) {
		return new RedditItem("1abc", "stocks", title, selftext, true, "https://www.reddit.com/r/stocks/comments/1abc/x/",
				"/r/stocks/comments/1abc/x/", author, 1_761_000_000L, null);
	}

}
