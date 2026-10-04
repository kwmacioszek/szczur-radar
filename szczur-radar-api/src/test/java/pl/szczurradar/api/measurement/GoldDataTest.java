package pl.szczurradar.api.measurement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import pl.szczurradar.api.agents.assessor.Prefilter;
import pl.szczurradar.api.agents.assessor.contract.Item;
import pl.szczurradar.api.agents.assessor.contract.Relevance;
import pl.szczurradar.api.agents.assessor.text.Whitespace;
import pl.szczurradar.api.measurement.GoldLabels.Label;
import pl.szczurradar.api.measurement.GoldLabels.LabeledTarget;
import pl.szczurradar.api.measurement.GoldLabels.Post;

/**
 * The gold set's files ({@code fixtures/assessor/gold/}). Skipped while a file does not exist yet —
 * {@code posts.json} comes from {@link WykopGoldSampler}, {@code labels.json} from the user's
 * labeling — and mandatory from then on.
 */
class GoldDataTest {

	static final int MIN_NOT_HARD_POSTS = 100;

	/** Mentions after anonymization: only {@code @uzytkownikN}. */
	private static final Pattern MENTION = Pattern.compile("@[\\p{L}\\p{N}_-]+");

	private static final Pattern ANONYMOUS = Pattern.compile("@uzytkownik\\d+");

	private static final Pattern URL = Pattern.compile("(?i)https?://|www\\.");

	@Test
	void postsAreAnonymizedAssessableAndFromTwoNewDays() throws IOException {
		List<Post> posts = posts();

		assertThat(posts).extracting(Post::postId).doesNotHaveDuplicates();
		assertThat(posts).allSatisfy(post -> {
			assertThat(Prefilter.isAssessable(post.input())).as(post.postId()).isTrue();
			assertThat(post.input().item().publishedAt().toLocalDate()).as(post.postId()).isEqualTo(post.day());
			texts(post.input().item()).forEach(text -> assertAnonymized(post.postId(), text));
		});
		List<LocalDate> days = posts.stream().map(Post::day).distinct().toList();
		assertThat(days).hasSizeGreaterThanOrEqualTo(2).doesNotContainAnyElementsOf(GoldLabels.EXCLUDED_DAYS);
	}

	@Test
	void everyPostIsLabeledWithAnchorsFromItsText() throws IOException {
		Map<String, Post> posts = posts().stream().collect(Collectors.toMap(Post::postId, Function.identity()));
		List<Label> labels = labels();

		assertThat(labels).extracting(Label::postId).doesNotHaveDuplicates();
		assertThat(labels).extracting(Label::postId).containsExactlyInAnyOrderElementsOf(posts.keySet());
		assertThat(labels).allSatisfy(label -> {
			if (label.relevance() == Relevance.NOT_INVESTMENT_RELATED) {
				assertThat(label.targets()).as(label.postId()).isEmpty();
			}
			List<String> texts = texts(posts.get(label.postId()).input().item()).map(Whitespace::normalize).toList();
			for (LabeledTarget target : label.targets()) {
				String anchor = Whitespace.normalize(target.anchor());
				assertThat(anchor).as(label.postId()).isNotEmpty();
				assertThat(texts).as("%s: anchor '%s' in the post", label.postId(), anchor)
					.anySatisfy(text -> assertThat(text).contains(anchor));
				assertAnonymized(label.postId(), target.anchor());
			}
			assertAnonymized(label.postId(), label.note());
		});
	}

	@Test
	void theGoldSetHasEnoughPostsThatAreNotHard() throws IOException {
		Map<String, LocalDate> dayOf = posts().stream().collect(Collectors.toMap(Post::postId, Post::day));
		List<Label> notHard = labels().stream().filter(label -> !label.hard()).toList();

		assertThat(notHard).hasSizeGreaterThanOrEqualTo(MIN_NOT_HARD_POSTS);
		assertThat(notHard.stream().map(label -> dayOf.get(label.postId())).distinct()).hasSizeGreaterThanOrEqualTo(2);
	}

	private static List<Post> posts() throws IOException {
		assumeTrue(Files.exists(GoldLabels.POSTS), "no gold posts yet (WykopGoldSampler)");
		return GoldLabels.readPosts(GoldLabels.POSTS);
	}

	private static List<Label> labels() throws IOException {
		assumeTrue(Files.exists(GoldLabels.LABELS), "no gold labels yet (labeling-gold.html)");
		return GoldLabels.readLabels(GoldLabels.LABELS);
	}

	private static Stream<String> texts(Item item) {
		return Stream.of(item.title(), item.body()).filter(text -> text != null);
	}

	private static void assertAnonymized(String postId, String text) {
		assertThat(URL.matcher(text).find()).as("%s: URL in '%s'", postId, text).isFalse();
		Matcher mention = MENTION.matcher(text);
		while (mention.find()) {
			assertThat(ANONYMOUS.matcher(mention.group()).matches()).as("%s: nick '%s'", postId, mention.group()).isTrue();
		}
	}

}
