package pl.szczurradar.api.measurement;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.SerializationFeature;

import pl.szczurradar.api.agents.assessor.contract.AssessmentInputV1;
import pl.szczurradar.api.agents.assessor.contract.AssessmentOutputV2;
import pl.szczurradar.api.agents.assessor.contract.Assertiveness;
import pl.szczurradar.api.agents.assessor.contract.ContractJson;
import pl.szczurradar.api.agents.assessor.contract.Relevance;
import pl.szczurradar.api.agents.assessor.contract.Target;
import pl.szczurradar.api.measurement.GoldLabels.LabeledTarget;
import pl.szczurradar.api.measurement.GoldLabels.Media;
import pl.szczurradar.api.measurement.GoldLabels.Post;
import pl.szczurradar.api.measurement.RedditReport.Assessed;

/**
 * Freezes the queue of the S-28 labeling sheet ({@code labeling-reddit.html}) in {@code sheet.json}: up to
 * 40 posts with a valid assessment, no megathreads (the run measures both), drawn and interleaved by
 * {@link RedditMeasures#sheetQueue}. Every post carries the assessment the sheet pre-fills: the first
 * {@code ok} line, judged again with today's code as in {@link RedditReport}. No model calls.
 *
 * <pre>
 * ./mvnw -Pmeasurement test -Dtest=RedditSheet -Dreddit.dir=&lt;path&gt;
 * </pre>
 *
 * Optional: {@code -Dreddit.seed=20261030}.
 */
@Tag("measurement")
class RedditSheet {

	static final String SHEET = "sheet.json";

	private static final long DEFAULT_SEED = 20261030;

	/** One post of {@code sheet.json}: the fields of {@link Post} and the model's assessment. */
	@JsonPropertyOrder({ "post_id", "day", "media", "input", "model" })
	record SheetPost(@JsonProperty("post_id") String postId, LocalDate day, Media media, AssessmentInputV1 input,
			SheetModel model) {
	}

	/** The assessment as the sheet's labels hold it, so that a post approved unchanged equals the model. */
	@JsonPropertyOrder({ "relevance", "targets" })
	record SheetModel(Relevance relevance, List<LabeledTarget> targets) {
	}

	@Test
	void writesTheSheet() throws IOException {
		Path dir = Path.of(Objects.requireNonNull(System.getProperty("reddit.dir"), "-Dreddit.dir=<path> is required"));
		long seed = Long.getLong("reddit.seed", DEFAULT_SEED);
		List<Post> posts = GoldLabels.readPosts(dir.resolve(RedditSampler.POSTS));
		List<Recording> lines = RedditRecorder.readRecordings(dir.resolve(RedditRecorder.RECORDINGS));

		List<Assessed> candidates = Assessed.all(posts, lines).stream()
			.filter(Assessed::ok)
			.filter(post -> !post.megathread())
			.toList();
		List<SheetPost> sheet = RedditMeasures.sheetQueue(candidates, Assessed::bucket, seed).stream()
			.map(RedditSheet::sheetPost)
			.toList();

		Files.writeString(dir.resolve(SHEET), ContractJson.MAPPER.writer()
			.with(SerializationFeature.INDENT_OUTPUT)
			.writeValueAsString(sheet) + "\n");
		System.out.println("wrote " + sheet.size() + " posts of " + candidates.size() + " candidates to "
				+ dir.resolve(SHEET).toAbsolutePath());
	}

	private static SheetPost sheetPost(Assessed assessed) {
		Post post = assessed.post();
		AssessmentOutputV2 output = assessed.output().orElseThrow();
		return new SheetPost(post.postId(), post.day(), post.media(), post.input(),
				new SheetModel(output.relevance(), output.targets().stream().map(RedditSheet::labeled).toList()));
	}

	private static LabeledTarget labeled(Target target) {
		return new LabeledTarget(target.anchor().quote(), target.stance().direction(),
				target.stance().assertiveness() == Assertiveness.HEDGED);
	}

}
