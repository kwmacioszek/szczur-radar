package pl.szczurradar.api.measurement;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.SerializationFeature;

import pl.szczurradar.api.agents.assessor.AssessmentResult;
import pl.szczurradar.api.agents.assessor.Provenance;
import pl.szczurradar.api.agents.assessor.contract.AssessmentOutputV2;
import pl.szczurradar.api.agents.assessor.contract.ContractJson;
import pl.szczurradar.api.agents.assessor.prompt.AssessmentPrompt;
import pl.szczurradar.api.agents.assessor.validation.Violation;
import pl.szczurradar.api.llm.Channel;
import pl.szczurradar.api.llm.LlmResponse;
import pl.szczurradar.api.llm.OutputMode;
import pl.szczurradar.api.llm.Provider;
import pl.szczurradar.api.llm.ReasoningEffort;
import pl.szczurradar.api.llm.ServiceTier;

/**
 * One recorded assessment of a gold post ({@code fixtures/llm/recordings/<configId>/<post_id>.json}):
 * the model's raw answer exactly as returned and the call's metadata, so that CI can judge it again
 * ({@code RecordingsReplayTest}) and the report can count quality and cost without calling a model.
 *
 * @param rawText {@code null} when the call produced no text ({@code provider_error}, {@code timeout})
 * @param status the verdict when recorded: {@code ok} or {@code failed}
 */
@JsonPropertyOrder({ "post_id", "configId", "provider", "model", "channel", "serviceTier", "flexFallback",
		"reasoningEffort", "outputMode", "promptVersion", "contractVersion", "recordedAt", "rawText", "inputTokens",
		"outputTokens", "cachedTokens", "durationMs", "attempts", "status", "failureClass", "violations" })
public record Recording(@JsonProperty("post_id") String postId, String configId, String provider,
		@Nullable String model, @Nullable String channel, @Nullable String serviceTier, boolean flexFallback,
		@Nullable String reasoningEffort, @Nullable String outputMode, String promptVersion, String contractVersion,
		Instant recordedAt, @Nullable String rawText, @Nullable Integer inputTokens, @Nullable Integer outputTokens,
		@Nullable Integer cachedTokens, long durationMs, int attempts, String status, @Nullable String failureClass,
		List<Violation> violations) {

	/** The recordings' root, relative to {@code szczur-radar-api/}. */
	public static final Path DIRECTORY = Path.of("src/test/resources/fixtures/llm/recordings");

	public Recording {
		Objects.requireNonNull(postId, "post_id");
		Objects.requireNonNull(configId, "configId");
		violations = List.copyOf(violations);
	}

	/** The recording of {@code result}, an assessment of {@code postId} by configuration {@code configId}. */
	public static Recording of(String postId, String configId, Provider provider, AssessmentResult result) {
		Provenance p = result.provenance();
		return new Recording(postId, configId, provider.id(), p.model(), p.channel() == null ? null : p.channel().value(),
				p.serviceTier() == null ? null : p.serviceTier().id(), p.flexFallback(),
				p.reasoningEffort() == null ? null : p.reasoningEffort().id(),
				p.outputMode() == null ? null : p.outputMode().value(), p.promptVersion(), p.contractVersion(),
				p.assessedAt(), result.rawText(), p.inputTokens(), p.outputTokens(), p.cachedTokens(), p.durationMs(),
				p.attempts(), result.status().value(), result.failureClass() == null ? null : result.failureClass().value(),
				result.violations());
	}

	/** Made with the current prompt and contract — the only recordings that count for a measurement. */
	public boolean current() {
		return AssessmentPrompt.PROMPT_VERSION.equals(promptVersion)
				&& AssessmentOutputV2.CONTRACT_VERSION.equals(contractVersion);
	}

	/** The model's answer as the client returned it; {@code null} when the call produced no text. */
	public @Nullable LlmResponse response() {
		if (rawText == null) {
			return null;
		}
		return new LlmResponse(rawText, byId(Provider.values(), Provider::id, provider), Objects.requireNonNull(model),
				byId(Channel.values(), Channel::value, channel), byId(ServiceTier.values(), ServiceTier::id, serviceTier),
				flexFallback, reasoningEffort == null ? null : byId(ReasoningEffort.values(), ReasoningEffort::id, reasoningEffort),
				byId(OutputMode.values(), OutputMode::value, outputMode), inputTokens, outputTokens, cachedTokens, attempts);
	}

	/** Whether the recorded verdict is the same as {@code result}'s. */
	public boolean sameVerdict(AssessmentResult result) {
		return status.equals(result.status().value())
				&& Objects.equals(failureClass, result.failureClass() == null ? null : result.failureClass().value());
	}

	public void write() {
		Path file = DIRECTORY.resolve(configId).resolve(postId + ".json");
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file,
					ContractJson.MAPPER.writer().with(SerializationFeature.INDENT_OUTPUT).writeValueAsString(this) + "\n");
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/** Every recording of {@code configId} (empty when there are none). */
	public static List<Recording> readAll(String configId) {
		Path directory = DIRECTORY.resolve(configId);
		if (!Files.isDirectory(directory)) {
			return List.of();
		}
		try (Stream<Path> files = Files.list(directory)) {
			List<Recording> recordings = new ArrayList<>();
			for (Path file : files.filter(path -> path.toString().endsWith(".json")).sorted().toList()) {
				recordings.add(ContractJson.MAPPER.readValue(Files.readString(file), Recording.class));
			}
			return recordings;
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static <E extends Enum<E>> E byId(E[] values, java.util.function.Function<E, String> id,
			@Nullable String value) {
		return Arrays.stream(values)
			.filter(candidate -> id.apply(candidate).equals(value))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("unknown value '" + value + "'"));
	}

}
