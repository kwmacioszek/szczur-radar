package pl.szczurradar.api.measurement;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;

import pl.szczurradar.api.agents.assessor.AssessmentResult;
import pl.szczurradar.api.agents.assessor.Assessor;
import pl.szczurradar.api.agents.assessor.Preparation;
import pl.szczurradar.api.agents.assessor.contract.AssessmentInputV1;
import pl.szczurradar.api.llm.LlmClientFactory;
import pl.szczurradar.api.llm.LlmProviderProperties;
import pl.szczurradar.api.llm.LlmUnavailableException;
import pl.szczurradar.api.measurement.GoldLabels.Post;
import pl.szczurradar.api.measurement.ModelsConfig.Model;

/**
 * Records one model configuration on the whole gold set (plan phase 6): the configuration in
 * {@code models.json} matching {@code LLM_*} (none → error), every post assessed through
 * {@link Assessor}, each result written as a {@link Recording}. A post that already has a current
 * recording of this configuration is skipped, so an interrupted run resumes; {@code -Dgold.rerecord=true}
 * records everything again. Only this configuration's recordings are written.
 *
 * <pre>
 * ( source ~/.config/szczur-radar/&lt;provider&gt;.env &amp;&amp; ./mvnw -Pmeasurement test -Dtest=GoldRecorder )
 * </pre>
 */
@Tag("measurement")
class GoldRecorder {

	@Test
	void recordsTheGoldSet() throws IOException {
		LlmProviderProperties properties = LlmProviderProperties.from(new StandardEnvironment());
		ModelsConfig config = ModelsConfig.read(ModelsConfig.FILE);
		Model model = config.matching(properties)
			.orElseThrow(() -> new IllegalStateException("no configuration in " + ModelsConfig.FILE + " matches "
					+ properties + " — add it there first"));
		boolean rerecord = Boolean.getBoolean("gold.rerecord");
		Map<String, Recording> existing = Recording.readAll(model.id())
			.stream()
			.filter(Recording::current)
			.collect(Collectors.toMap(Recording::postId, Function.identity()));
		List<Post> posts = GoldLabels.readPosts(GoldLabels.POSTS);
		// One client for the whole run (llm/CLAUDE.md).
		Assessor assessor = new Assessor(LlmClientFactory.create(properties), properties.provider(), properties.model(),
				Clock.systemUTC());

		System.out.println("Recording " + model.id() + " (" + properties + ") on " + posts.size() + " posts");
		int recorded = 0;
		int ok = 0;
		for (Post post : posts) {
			if (!rerecord && existing.containsKey(post.postId())) {
				continue;
			}
			AssessmentResult result = assess(assessor, post.input());
			Recording.of(post.postId(), model.id(), properties.provider(), result).write();
			recorded++;
			ok += result.ok() ? 1 : 0;
			System.out.printf("%s %-6s %-26s %6d ms, %d attempt(s)%s%n", post.postId(), result.status().value(),
					result.failureClass() == null ? "" : result.failureClass().value(),
					result.provenance().durationMs(), result.provenance().attempts(),
					result.provenance().flexFallback() ? ", flex fallback" : "");
		}
		System.out.printf("recorded %d posts (%d ok), skipped %d with a current recording%n", recorded, ok,
				posts.size() - recorded);
		assertThat(Recording.readAll(model.id()).stream().filter(Recording::current).count())
			.as("current recordings of " + model.id())
			.isEqualTo(posts.size());
	}

	/**
	 * {@link Assessor#assess}, with a provider unavailable after the retries recorded as the failed call
	 * of this entry, as before {@code LlmUnavailableException} existed: the measurement reports it and goes
	 * on.
	 */
	private static AssessmentResult assess(Assessor assessor, AssessmentInputV1 input) {
		long start = System.nanoTime();
		try {
			return assessor.assess(input);
		}
		catch (LlmUnavailableException ex) {
			return assessor.failed(((Preparation.Prepared) assessor.prepare(input)).request(), ex,
					Duration.ofNanos(System.nanoTime() - start));
		}
	}

}
