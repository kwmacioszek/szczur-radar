package pl.szczurradar.api.measurement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import pl.szczurradar.api.measurement.GoldLabels.Post;
import pl.szczurradar.api.measurement.GoldMetrics.Metric;
import pl.szczurradar.api.measurement.ModelsConfig.Model;

/**
 * The recorded answers of the measured models as a CI regression test (plan phase 6), without
 * secrets or model calls: every current recording judged again gives the recorded verdict, and the
 * default configuration has a recording for every gold post and meets every threshold of
 * {@link GoldMetrics}.
 */
class RecordingsReplayTest {

	@Test
	void everyCurrentRecordingIsJudgedAsItWasRecorded() throws IOException {
		ModelsConfig config = ModelsConfig.read(ModelsConfig.FILE);
		List<Post> posts = GoldLabels.readPosts(GoldLabels.POSTS);
		List<String> changed = new ArrayList<>();
		for (Model model : config.models()) {
			Replay.Outcome outcome = Replay.of(model, posts);
			outcome.changedVerdicts().forEach(postId -> changed.add(model.id() + "/" + postId));
		}

		assertThat(changed).as("recordings whose verdict changed (validator, parser or schema drift)").isEmpty();
	}

	@Test
	void theDefaultConfigurationIsCompleteAndMeetsTheThresholds() throws IOException {
		ModelsConfig config = ModelsConfig.read(ModelsConfig.FILE);
		assumeTrue(config.defaultId() != null, "no default model chosen yet (plan phase 6, step 4)");
		assumeTrue(Files.exists(GoldLabels.LABELS), "no gold labels");
		Model model = config.model(config.defaultId()).orElseThrow();
		Replay.Outcome outcome = Replay.of(model, GoldLabels.readPosts(GoldLabels.POSTS));

		assertThat(outcome.missing()).as("gold posts without a current recording of " + model.id()).isEmpty();
		GoldMetrics metrics = GoldMetrics.compute(GoldLabels.readLabels(GoldLabels.LABELS), outcome.outputs(),
				GoldLabels.readAliases(GoldLabels.ALIASES));
		for (Metric metric : Metric.values()) {
			assertThat(metrics.meets(metric))
				.as("%s of %s: %s (threshold %s %.2f)", metric, model.id(), metrics.ratio(metric),
						metric.atLeast() ? "≥" : "≤", metric.threshold())
				.isTrue();
		}
	}

}
