package pl.szczurradar.api.measurement;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;

import pl.szczurradar.api.agents.assessor.AssessmentResult;
import pl.szczurradar.api.agents.assessor.Assessor;
import pl.szczurradar.api.agents.assessor.contract.ContractJson;
import pl.szczurradar.api.llm.LlmClientFactory;
import pl.szczurradar.api.llm.LlmProviderProperties;
import pl.szczurradar.api.llm.Provider;
import pl.szczurradar.api.measurement.GoldLabels.Post;
import pl.szczurradar.api.measurement.ModelsConfig.Model;

/**
 * Passes every post of {@code posts.json} through production's {@link Assessor} for the S-28 measurement:
 * up to {@link RedditMeasures#MAX_TRIES} tries a post, each try ({@code Assessor.assess}, one
 * {@link Recording}) one line of {@code recordings.jsonl}, so that no error's details are lost. Refuses
 * any configuration but production's provider and model.
 *
 * <pre>
 * ( source ~/.config/szczur-radar/openrouter.env &amp;&amp; ./mvnw -Pmeasurement test -Dtest=RedditRecorder -Dreddit.dir=&lt;path&gt; )
 * </pre>
 *
 * Optional: {@code -Dreddit.concurrency=4}, {@code -Dreddit.limit=<N>} (the first N posts),
 * {@code -Dreddit.maxUsd=4}.
 *
 * <p>Resumes from the file: a post with an {@code ok} line or three lines is skipped, one with fewer lines
 * gets the tries it has left (a line's {@code attempts} are transport retries, not tries). The fuse counts
 * every line in the file, those of an interrupted run too, at the prices of {@code luna} for the line's
 * recorded channel: past {@code maxUsd} no new try starts and the test fails. {@code Recording.write} and
 * {@code readAll} are not used: they are tied to the gold recordings' directory, which feeds the CI gate.
 */
@Tag("measurement")
class RedditRecorder {

	static final String RECORDINGS = "recordings.jsonl";

	private static final Provider PROVIDER = Provider.OPENROUTER;

	private static final String MODEL = "openai/gpt-6-luna";

	/** Only its prices: {@code ModelsConfig.matching} would refuse the tier production runs on. */
	private static final String PRICES = "luna";

	/** One run's state, shared by the pool's threads. */
	private record Run(Assessor assessor, Provider provider, String configId, RecordingsFile file, double maxUsd,
			AtomicBoolean stopped) {

		/** The tries {@code post} has left after its {@code done} lines, until one is {@code ok}. */
		void record(Post post, int done) throws IOException {
			for (int tryNumber = done + 1; tryNumber <= RedditMeasures.MAX_TRIES; tryNumber++) {
				if (stopped.get() || file.spent() > maxUsd) {
					return;
				}
				AssessmentResult result = GoldRecorder.assess(assessor, post.input());
				file.append(Recording.of(post.postId(), configId, provider, result));
				System.out.printf("%s try %d %-6s %-26s %6d ms, %d attempt(s)%s%n", post.postId(), tryNumber,
						result.status().value(), result.failureClass() == null ? "" : result.failureClass().value(),
						result.provenance().durationMs(), result.provenance().attempts(),
						result.provenance().flexFallback() ? ", flex fallback" : "");
				if (result.ok()) {
					return;
				}
			}
		}

	}

	/** {@code recordings.jsonl}: one line per try, written one at a time, and what the file has cost so far. */
	private static final class RecordingsFile {

		private final Path path;

		private final Model prices;

		private double spent;

		RecordingsFile(Path path, Model prices, List<Recording> lines) {
			this.path = path;
			this.prices = prices;
			this.spent = lines.stream().mapToDouble(line -> RedditMeasures.cost(prices, line)).sum();
		}

		synchronized void append(Recording line) throws IOException {
			Files.writeString(path, ContractJson.MAPPER.writeValueAsString(line) + "\n", StandardOpenOption.CREATE,
					StandardOpenOption.APPEND);
			spent += RedditMeasures.cost(prices, line);
		}

		synchronized double spent() {
			return spent;
		}

	}

	@Test
	void recordsTheRedditSample() throws Exception {
		LlmProviderProperties properties = LlmProviderProperties.from(new StandardEnvironment());
		assertThat(properties.provider()).as(LlmProviderProperties.PROVIDER).isEqualTo(PROVIDER);
		assertThat(properties.model()).as(LlmProviderProperties.MODEL).isEqualTo(MODEL);
		Path dir = Path.of(System.getProperty("reddit.dir", ""));
		assertThat(dir.toString()).as("-Dreddit.dir").isNotEmpty();
		int concurrency = Integer.getInteger("reddit.concurrency", 4);
		long limit = Long.getLong("reddit.limit", Long.MAX_VALUE);
		double maxUsd = Double.parseDouble(System.getProperty("reddit.maxUsd", "4"));
		Model prices = ModelsConfig.read(ModelsConfig.FILE).model(PRICES).orElseThrow();

		List<Post> posts = GoldLabels.readPosts(dir.resolve(RedditSampler.POSTS)).stream().limit(limit).toList();
		Path path = dir.resolve(RECORDINGS);
		List<Recording> lines = readRecordings(path);
		Map<String, List<Recording>> existing = RedditMeasures.byPost(lines);
		RecordingsFile file = new RecordingsFile(path, prices, lines);
		// One client for the whole run (llm/CLAUDE.md).
		Assessor assessor = new Assessor(LlmClientFactory.create(properties), properties.provider(), properties.model(),
				Clock.systemUTC());
		Run run = new Run(assessor, properties.provider(), "luna-" + properties.serviceTier().id(), file, maxUsd,
				new AtomicBoolean());

		System.out.printf("Recording %s (%s) on %d posts, %d lines already there, $%.4f spent%n", run.configId(),
				properties, posts.size(), lines.size(), file.spent());
		List<Future<?>> tasks = new ArrayList<>();
		// close() waits for the calls in flight: an interrupted call would be recorded as a failed try.
		try (ExecutorService pool = Executors.newFixedThreadPool(concurrency)) {
			for (Post post : posts) {
				List<Recording> done = existing.getOrDefault(post.postId(), List.of());
				if (RedditMeasures.finished(done)) {
					continue;
				}
				tasks.add(pool.submit(() -> {
					try {
						run.record(post, done.size());
					}
					catch (Exception ex) {
						run.stopped().set(true);
						throw ex;
					}
					return null;
				}));
			}
		}
		for (Future<?> task : tasks) {
			task.get();
		}

		System.out.printf("spent $%.4f, %d posts tried in this run%n", file.spent(), tasks.size());
		assertThat(file.spent()).as("USD spent against -Dreddit.maxUsd; a higher limit resumes the run")
			.isLessThanOrEqualTo(maxUsd);
		Map<String, List<Recording>> recorded = RedditMeasures.byPost(readRecordings(path));
		assertThat(posts).as("posts with an ok line or three lines").allSatisfy(
				post -> assertThat(RedditMeasures.finished(recorded.getOrDefault(post.postId(), List.of())))
					.as(post.postId())
					.isTrue());
	}

	/** The lines of {@code recordings.jsonl} in file order; none when it does not exist yet. */
	static List<Recording> readRecordings(Path file) throws IOException {
		if (!Files.exists(file)) {
			return List.of();
		}
		List<Recording> lines = new ArrayList<>();
		for (String line : Files.readAllLines(file)) {
			lines.add(ContractJson.MAPPER.readValue(line, Recording.class));
		}
		return lines;
	}

}
