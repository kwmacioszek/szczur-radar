package pl.szczurradar.api.measurement;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.SerializationFeature;

import pl.szczurradar.api.agents.assessor.contract.AssessmentInputV1;
import pl.szczurradar.api.agents.assessor.contract.ContractJson;
import pl.szczurradar.api.agents.assessor.contract.Direction;
import pl.szczurradar.api.agents.assessor.contract.Relevance;

/**
 * The gold set's files: {@code posts.json} written by {@link WykopGoldSampler} and {@code labels.json}
 * exported by {@code context/changes/assessment-contract-harness/pilot/labeling-gold.html}. Both are
 * read with the strict contract mapper: an unknown or missing field fails instead of being ignored.
 */
public final class GoldLabels {

	/** The gold set's directory, relative to {@code szczur-radar-api/} (Maven's working directory). */
	public static final Path DIRECTORY = Path.of("src/test/resources/fixtures/assessor/gold");

	public static final Path POSTS = DIRECTORY.resolve("posts.json");

	public static final Path LABELS = DIRECTORY.resolve("labels.json");

	/**
	 * Default place of the links to the sampled posts on wykop.pl ({@code {post_id: url}}): outside the
	 * repo, because a link leads to the author.
	 */
	public static final Path LOCAL_LINKS = Path.of(System.getProperty("user.home"), ".config", "szczur-radar",
			"gold-links.json");

	/** Days whose posts shaped the contract's rules (pilots); never part of the gold set. */
	public static final List<LocalDate> EXCLUDED_DAYS = List.of(LocalDate.parse("2026-09-28"),
			LocalDate.parse("2026-09-29"));

	private GoldLabels() {
	}

	/**
	 * One sampled post: an anonymized, assessable contract input and which attachments the source
	 * element had. {@code media} is a hint for the labeling sheet only — the model never sees it.
	 */
	@JsonPropertyOrder({ "post_id", "day", "media", "input" })
	public record Post(@JsonProperty("post_id") String postId, LocalDate day, Media media, AssessmentInputV1 input) {

		public Post {
			Objects.requireNonNull(postId, "post_id");
			Objects.requireNonNull(day, "day");
			Objects.requireNonNull(media, "media");
			Objects.requireNonNull(input, "input");
		}

	}

	/** Whether the source element had a picture, an embed or a poll (their content is not in the input). */
	@JsonPropertyOrder({ "photo", "embed", "survey" })
	public record Media(boolean photo, boolean embed, boolean survey) {

		public static final Media NONE = new Media(false, false, false);

	}

	/**
	 * The user's label of one post (format of {@code labeling-gold.html}'s export).
	 *
	 * @param hard the user was unsure; excluded from every metric's denominator except compliance
	 * @param targets empty for {@code not_investment_related}
	 */
	@JsonPropertyOrder({ "post_id", "relevance", "hard", "note", "targets" })
	public record Label(@JsonProperty("post_id") String postId, Relevance relevance, boolean hard, String note,
			List<LabeledTarget> targets) {

		public Label {
			Objects.requireNonNull(postId, "post_id");
			Objects.requireNonNull(relevance, "relevance");
			targets = List.copyOf(targets);
		}

	}

	/** A target the user marked: the anchor as selected in the post, the direction and "niepewne". */
	@JsonPropertyOrder({ "anchor", "direction", "hedged" })
	public record LabeledTarget(String anchor, Direction direction, boolean hedged) {

		public LabeledTarget {
			Objects.requireNonNull(anchor, "anchor");
			Objects.requireNonNull(direction, "direction");
		}

	}

	public static List<Post> readPosts(Path path) throws IOException {
		return ContractJson.MAPPER.readValue(Files.readString(path), new TypeReference<List<Post>>() {
		});
	}

	public static List<Label> readLabels(Path path) throws IOException {
		return ContractJson.MAPPER.readValue(Files.readString(path), new TypeReference<List<Label>>() {
		});
	}

	/** Writes {@code posts.json}: indented, non-ASCII text unescaped, one trailing newline. */
	public static void writePosts(Path path, List<Post> posts) throws IOException {
		write(path, posts);
	}

	/** Writes the local {@code {post_id: url}} file read by the labeling sheet. */
	public static void writeLinks(Path path, Map<String, String> links) throws IOException {
		write(path, links);
	}

	private static void write(Path path, Object value) throws IOException {
		Files.createDirectories(path.toAbsolutePath().getParent());
		Files.writeString(path, ContractJson.MAPPER.writer()
			.with(SerializationFeature.INDENT_OUTPUT)
			.writeValueAsString(value) + "\n");
	}

}
