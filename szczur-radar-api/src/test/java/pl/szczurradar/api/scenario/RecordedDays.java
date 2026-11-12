package pl.szczurradar.api.scenario;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import pl.szczurradar.api.agents.assessor.Prefilter;
import pl.szczurradar.api.agents.assessor.contract.AssessmentInputV1;
import pl.szczurradar.api.agents.assessor.prompt.AssessmentPrompt;
import pl.szczurradar.api.sources.LocalApiServer;
import pl.szczurradar.api.sources.RawInputMapper;
import pl.szczurradar.api.sources.Source;
import pl.szczurradar.api.sources.reddit.RedditItem;
import pl.szczurradar.api.sources.reddit.RedditPosts;
import pl.szczurradar.api.sources.wykop.WykopPosts;

/**
 * The posts of the sources' fixture days that go to the model, and the recorded provider answer of each
 * ({@value #RECORDINGS}): the one place that knows which fixture post is which recording. Read by the
 * scenarios' provider ({@link RecordedProvider}), the recorder ({@code measurement/ScenarioDaysRecorder}) and
 * {@code RecordedDaysTest}.
 *
 * <p>Each element of a {@link Page} is mapped the way the pipeline assesses a stored post: its raw payload
 * through the source's production {@link RawInputMapper} ({@code pipeline/assess/AssessmentInputs}). A Reddit
 * post taken down ({@link RedditItem#removed()}) is left out, as {@code RedditRepository} leaves it out of the
 * fetch, and so is an element without content ({@link Prefilter#isAssessable}), which never reaches the model.
 * A post is known to the provider by its user message ({@link AssessmentPrompt#userMessage}): the input's
 * contract JSON, without an id, the same for every attempt.
 */
public final class RecordedDays {

	/** The recordings' directory, from the API module's root (where Maven runs the tests). */
	public static final String RECORDINGS = "src/test/resources/fixtures/llm/scenario-days";

	public static final Path DIR = Path.of(RECORDINGS);

	/** How many fixture posts go to the model: 13 of Wykop, 14 of r/inwestowanie, 6 of r/stocks. */
	public static final int EXPECTED = 33;

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final List<Post> POSTS = read();

	/** A fixture page of a source and the prefix of its recordings' names. */
	public enum Page {

		WYKOP_ENTRIES(Source.WYKOP, "wykop", "search-entries-page.json", "wykop-entry-"),
		WYKOP_LINKS(Source.WYKOP, "wykop", "search-links-page.json", "wykop-link-"),
		REDDIT_INWESTOWANIE(Source.REDDIT, "reddit", "search-posts-inwestowanie.json", "reddit-"),
		REDDIT_STOCKS(Source.REDDIT, "reddit", "search-posts-stocks.json", "reddit-");

		private final Source source;

		private final String dir;

		private final String fixture;

		private final String prefix;

		Page(Source source, String dir, String fixture, String prefix) {
			this.source = source;
			this.dir = dir;
			this.fixture = fixture;
			this.prefix = prefix;
		}

		public Source source() {
			return source;
		}

		/** The fixture's name in {@code fixtures/<source>/}. */
		public String fixture() {
			return fixture;
		}

		private RawInputMapper mapper() {
			return switch (source) {
				case WYKOP -> new WykopPosts();
				case REDDIT -> new RedditPosts();
			};
		}

	}

	/** One fixture post that goes to the model, with its source id and the input the pipeline sends. */
	public record Post(Page page, String id, AssessmentInputV1 input) {

		/** The recording's file name in {@link #DIR}. */
		public String file() {
			return page.prefix + id + ".json";
		}

		/** The user message the pipeline sends for this post. */
		public String userMessage() {
			return AssessmentPrompt.userMessage(input);
		}

		public Path recording() {
			return DIR.resolve(file());
		}

	}

	private RecordedDays() {
	}

	/** Every fixture post that goes to the model, page after page in {@link Page}'s order, each in its fixture's order. */
	public static List<Post> posts() {
		return POSTS;
	}

	/** The recorded answer of {@code post}: the provider's HTTP 200 body, unchanged. */
	public static String recording(Post post) {
		try {
			return Files.readString(post.recording());
		}
		catch (IOException ex) {
			throw new UncheckedIOException("no recording " + post.recording() + " — record it with ScenarioDaysRecorder"
					+ " (src/test/java/pl/szczurradar/api/measurement/README.md)", ex);
		}
	}

	private static List<Post> read() {
		List<Post> posts = new ArrayList<>();
		for (Page page : Page.values()) {
			RawInputMapper mapper = page.mapper();
			for (JsonNode element : JSON.readTree(LocalApiServer.fixture(page.dir, page.fixture)).get("data")) {
				if (page.source == Source.REDDIT && JSON.treeToValue(element, RedditItem.class).removed()) {
					continue;
				}
				// The raw payload as the pipeline stores it (WykopPosts.post, RedditPosts.post).
				AssessmentInputV1 input = mapper.map(JSON.writeValueAsBytes(element));
				if (Prefilter.isAssessable(input)) {
					posts.add(new Post(page, element.get("id").asString(), input));
				}
			}
		}
		return List.copyOf(posts);
	}

}
