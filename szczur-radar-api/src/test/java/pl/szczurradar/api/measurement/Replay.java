package pl.szczurradar.api.measurement;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import pl.szczurradar.api.agents.assessor.AssessmentResult;
import pl.szczurradar.api.agents.assessor.Assessor;
import pl.szczurradar.api.agents.assessor.Preparation;
import pl.szczurradar.api.agents.assessor.Provenance;
import pl.szczurradar.api.agents.assessor.contract.AssessmentOutputV2;
import pl.szczurradar.api.agents.assessor.validation.FailureClass;
import pl.szczurradar.api.config.LlmProviderProperties.ServiceTier;
import pl.szczurradar.api.llm.LlmResponse;
import pl.szczurradar.api.measurement.GoldLabels.Post;
import pl.szczurradar.api.measurement.ModelsConfig.Model;

/**
 * Judges a configuration's recordings again with today's {@link Assessor#evaluate} — no model call.
 * Only current recordings (current prompt and contract, the configuration's provider) count; an
 * older or missing one makes the set incomplete.
 */
final class Replay {

	/** Never called: replaying only evaluates recorded answers. */
	private static final Assessor ASSESSOR = new Assessor(request -> {
		throw new IllegalStateException("a replay makes no model calls");
	}, Clock.systemUTC());

	/**
	 * @param results today's verdict per post with a current recording
	 * @param changedVerdicts posts whose verdict differs from the recorded one
	 * @param missing gold posts without a current recording
	 */
	record Outcome(Model model, Map<String, AssessmentResult> results, Map<String, Recording> recordings,
			List<String> changedVerdicts, List<String> missing) {

		boolean complete() {
			return missing.isEmpty();
		}

		/** The valid outputs by post, the input of {@link GoldMetrics#compute}. */
		Map<String, AssessmentOutputV2> outputs() {
			return results.entrySet()
				.stream()
				.filter(entry -> entry.getValue().ok())
				.collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().output()));
		}

	}

	private Replay() {
	}

	static Outcome of(Model model, List<Post> posts) {
		Map<String, Recording> recordings = Recording.readAll(model.id())
			.stream()
			.filter(Recording::current)
			.filter(recording -> recording.provider().equals(model.provider()))
			.collect(Collectors.toMap(Recording::postId, recording -> recording, (a, b) -> a, LinkedHashMap::new));
		Map<String, AssessmentResult> results = new LinkedHashMap<>();
		List<String> changed = new ArrayList<>();
		List<String> missing = new ArrayList<>();
		Set<String> goldIds = posts.stream().map(Post::postId).collect(Collectors.toSet());
		for (Post post : posts) {
			Recording recording = recordings.get(post.postId());
			if (recording == null) {
				missing.add(post.postId());
				continue;
			}
			AssessmentResult result = judge(post, recording);
			results.put(post.postId(), result);
			if (!recording.sameVerdict(result)) {
				changed.add(post.postId());
			}
		}
		recordings.keySet().retainAll(goldIds);
		return new Outcome(model, results, recordings, changed, missing);
	}

	/** Today's verdict on a recording: a re-evaluated answer, or the recorded call failure. */
	private static AssessmentResult judge(Post post, Recording recording) {
		LlmResponse response = recording.response();
		if (response == null) {
			return failedCall(recording);
		}
		Preparation preparation = ASSESSOR.prepare(post.input());
		if (!(preparation instanceof Preparation.Prepared prepared)) {
			throw new IllegalStateException(post.postId() + " is not assessable; the gold set holds assessable posts");
		}
		return ASSESSOR.evaluate(post.input(), prepared.request(), response, Duration.ofMillis(recording.durationMs()),
				recording.recordedAt());
	}

	/** A recorded call without an answer stays what it was: there is nothing to judge again. */
	private static AssessmentResult failedCall(Recording recording) {
		if (!"failed".equals(recording.status()) || recording.failureClass() == null) {
			throw new IllegalStateException(recording.postId() + ": a recording without text must be a failed call");
		}
		FailureClass failureClass = Arrays.stream(FailureClass.values())
			.filter(candidate -> candidate.value().equals(recording.failureClass()))
			.findFirst()
			.orElseThrow();
		Provenance provenance = new Provenance(Provenance.Source.MODEL, null, null, null,
				recording.serviceTier() == null ? ServiceTier.DEFAULT
						: ServiceTier.valueOf(recording.serviceTier().toUpperCase(Locale.ROOT)),
				recording.flexFallback(), null, null, recording.promptVersion(), recording.contractVersion(),
				recording.recordedAt(), null, null, null, recording.durationMs(), Math.max(1, recording.attempts()));
		return new AssessmentResult(AssessmentResult.Status.FAILED, null, failureClass, recording.violations(),
				provenance, null);
	}

}
