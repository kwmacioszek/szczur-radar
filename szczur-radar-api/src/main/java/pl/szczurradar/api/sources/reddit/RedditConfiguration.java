package pl.szczurradar.api.sources.reddit;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

import pl.szczurradar.api.settings.SettingText;

/**
 * The application's {@link RedditRepository}, from {@code REDDIT_SUBREDDITS}: the subreddits to read, comma
 * separated (not a secret; the names may be logged). It is optional: without it the API, CI and tests start
 * and there is simply no {@link RedditRepository} bean — {@link RedditFetcher} injects
 * {@code Optional<RedditRepository>} and reports Reddit as not configured, so a range run that needs it is
 * refused ({@code source_not_configured}). A value naming no subreddit or an invalid name is a
 * configuration error that fails the start.
 *
 * <p>Each name is stripped, an {@code r/} in front is dropped, its case is kept (the payload's own spelling
 * is what the input's {@code channels} gets anyway) and a name repeated in another case counts once (the
 * first one stays). A name is 2–21 letters, digits or {@code _}, as on Reddit.
 */
@Configuration(proxyBeanMethods = false)
public class RedditConfiguration {

	public static final String SUBREDDITS = "REDDIT_SUBREDDITS";

	private static final String PREFIX = "r/";

	private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{2,21}");

	@Bean
	@Conditional(SubredditsSet.class)
	RedditRepository redditRepository(Environment environment) {
		return new RedditRepository(new RedditApiClient(),
				subreddits(SettingText.trimmed(environment.getProperty(SUBREDDITS))));
	}

	/**
	 * The subreddits {@code value} names, in order, each once.
	 *
	 * @throws IllegalStateException when it names none or an element is not a valid name (named in the
	 *     message)
	 */
	static List<String> subreddits(String value) {
		List<String> names = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (String element : value.split(",")) {
			String given = element.strip();
			if (given.isEmpty()) {
				continue;
			}
			String name = given.startsWith(PREFIX) ? given.substring(PREFIX.length()) : given;
			if (!NAME.matcher(name).matches()) {
				throw invalid("has an invalid subreddit name '" + given
						+ "' (2–21 letters, digits or _, optionally after r/)");
			}
			if (seen.add(name.toLowerCase(Locale.ROOT))) {
				names.add(name);
			}
		}
		if (names.isEmpty()) {
			throw invalid("names no subreddit (a comma-separated list such as stocks,investing)");
		}
		return names;
	}

	private static IllegalStateException invalid(String problem) {
		return new IllegalStateException("Invalid Reddit configuration: " + SUBREDDITS + " " + problem
				+ ". See szczur-radar-api/README.md (Reddit).");
	}

	/** Matches when {@code REDDIT_SUBREDDITS} is set (non-blank). */
	static class SubredditsSet implements Condition {

		@Override
		public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
			return !SettingText.trimmed(context.getEnvironment().getProperty(SUBREDDITS)).isEmpty();
		}

	}

}
