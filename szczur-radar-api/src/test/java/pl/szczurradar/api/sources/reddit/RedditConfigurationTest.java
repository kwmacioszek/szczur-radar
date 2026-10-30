package pl.szczurradar.api.sources.reddit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** {@link RedditConfiguration}: {@code REDDIT_SUBREDDITS} is optional, but never an empty or invalid list. */
class RedditConfigurationTest {

	private final ApplicationContextRunner context = new ApplicationContextRunner()
		.withUserConfiguration(RedditConfiguration.class)
		// An empty value overrides a real REDDIT_SUBREDDITS of the machine.
		.withPropertyValues(RedditConfiguration.SUBREDDITS + "=");

	@Test
	void withoutTheSettingThereIsNoRepositoryAndTheContextStarts() {
		context.run(started -> {
			assertThat(started).hasNotFailed();
			assertThat(started).doesNotHaveBean(RedditRepository.class);
		});
	}

	@Test
	void theSubredditsAreReadInOrderOnceEachWithTheirCase() {
		context.withPropertyValues(RedditConfiguration.SUBREDDITS + "=stocks, r/ValueInvesting ,investing,STOCKS")
			.run(started -> assertThat(started.getBean(RedditRepository.class).subreddits())
				.containsExactly("stocks", "ValueInvesting", "investing"));
	}

	@Test
	void aListNamingNoSubredditFailsTheStart() {
		context.withPropertyValues(RedditConfiguration.SUBREDDITS + "= , ").run(started -> {
			assertThat(started).hasFailed();
			assertThat(started.getStartupFailure()).rootCause()
				.isInstanceOf(IllegalStateException.class)
				.hasMessageStartingWith("Invalid Reddit configuration: REDDIT_SUBREDDITS names no subreddit")
				.hasMessageEndingWith("See szczur-radar-api/README.md (Reddit).");
		});
	}

	@Test
	void anInvalidNameFailsTheStartNamingIt() {
		context.withPropertyValues(RedditConfiguration.SUBREDDITS + "=stocks,r/bad name").run(started -> {
			assertThat(started).hasFailed();
			assertThat(started.getStartupFailure()).rootCause()
				.hasMessageContaining("REDDIT_SUBREDDITS has an invalid subreddit name 'r/bad name'");
		});
	}

	@Test
	void emptyElementsBetweenCommasAreIgnored() {
		assertThat(RedditConfiguration.subreddits(" ,stocks,, investing ,")).containsExactly("stocks", "investing");
	}

	@ParameterizedTest
	@ValueSource(strings = { "a", "abcdefghijklmnopqrstuv", "r/", "R/stocks", "/r/stocks", "stocks!", "r/r/stocks" })
	void aNameOutsideRedditsRuleIsRefused(String name) {
		assertThatIllegalStateException().isThrownBy(() -> RedditConfiguration.subreddits("investing," + name))
			.withMessageContaining("'" + name + "'");
	}

	@Test
	void namesOfTwoToTwentyOneLettersDigitsOrUnderscoresAreAccepted() {
		assertThat(RedditConfiguration.subreddits("ab,abcdefghijklmnopqrstu,Wall_Street_Bets2"))
			.containsExactly("ab", "abcdefghijklmnopqrstu", "Wall_Street_Bets2");
	}

}
