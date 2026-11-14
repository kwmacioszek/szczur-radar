package pl.szczurradar.api.agents.assessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import pl.szczurradar.api.llm.Channel;
import pl.szczurradar.api.llm.OutputMode;
import pl.szczurradar.api.llm.Provider;
import pl.szczurradar.api.llm.ReasoningEffort;
import pl.szczurradar.api.llm.ServiceTier;

/**
 * {@link Provenance} as JSON (every closed value in lower case, as stored and recorded) and its invariants (§15): a
 * prefilter result has no call details, a model result has a prompt version, a tier and an attempt.
 */
class ProvenanceTest {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private static final Instant AT = Instant.parse("2026-10-06T10:00:00Z");

	/** The prefilter's provenance with one call detail set: each must be refused on its own. */
	static Stream<Arguments> prefilterWithOneCallDetail() {
		return Stream.of(
				detail("provider", () -> prefilter(Provider.OPENROUTER, null, null, null, false, null, null, null, null,
						null, null, 0, 0)),
				detail("model", () -> prefilter(null, "openai/gpt-6-luna", null, null, false, null, null, null, null, null,
						null, 0, 0)),
				detail("channel", () -> prefilter(null, null, Channel.OPENAI_FLEX, null, false, null, null, null, null,
						null, null, 0, 0)),
				detail("serviceTier", () -> prefilter(null, null, null, ServiceTier.FLEX, false, null, null, null, null,
						null, null, 0, 0)),
				detail("flexFallback", () -> prefilter(null, null, null, null, true, null, null, null, null, null, null, 0,
						0)),
				detail("reasoningEffort", () -> prefilter(null, null, null, null, false, ReasoningEffort.LOW, null, null,
						null, null, null, 0, 0)),
				detail("outputMode", () -> prefilter(null, null, null, null, false, null, OutputMode.JSON_SCHEMA, null,
						null, null, null, 0, 0)),
				detail("promptVersion", () -> prefilter(null, null, null, null, false, null, null, "p1", null, null, null,
						0, 0)),
				detail("inputTokens", () -> prefilter(null, null, null, null, false, null, null, null, 1200, null, null,
						0, 0)),
				detail("outputTokens", () -> prefilter(null, null, null, null, false, null, null, null, null, 300, null,
						0, 0)),
				detail("cachedTokens", () -> prefilter(null, null, null, null, false, null, null, null, null, null, 1024,
						0, 0)),
				detail("durationMs", () -> prefilter(null, null, null, null, false, null, null, null, null, null, null, 1,
						0)),
				detail("attempts", () -> prefilter(null, null, null, null, false, null, null, null, null, null, null, 0,
						1)));
	}

	@Test
	void everyEnumIsWrittenAsItsLowerCaseId() {
		Provenance provenance = new Provenance(Provenance.Source.MODEL, Provider.OPENROUTER, "openai/gpt-6-luna",
				Channel.OPENAI_FLEX, ServiceTier.FLEX, false, ReasoningEffort.XHIGH, OutputMode.JSON_SCHEMA, "p1", "V2",
				Instant.parse("2026-10-06T10:00:00Z"), 1200, 300, 1024, 15, 1);

		JsonNode json = MAPPER.valueToTree(provenance);

		assertThat(json.get("source").asString()).isEqualTo("model");
		assertThat(json.get("provider").asString()).isEqualTo("openrouter");
		assertThat(json.get("channel").asString()).isEqualTo(Channel.OPENAI_FLEX.value());
		assertThat(json.get("serviceTier").asString()).isEqualTo("flex");
		assertThat(json.get("reasoningEffort").asString()).isEqualTo("xhigh");
		assertThat(json.get("outputMode").asString()).isEqualTo(OutputMode.JSON_SCHEMA.value());
		assertThat(MAPPER.treeToValue(json, Provenance.class)).isEqualTo(provenance);
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("prefilterWithOneCallDetail")
	void aPrefilterResultWithAnyOneCallDetailIsRefused(String detail, Supplier<Provenance> provenance) {
		assertThatIllegalArgumentException().isThrownBy(() -> provenance.get())
			.withMessage("a prefilter result has no call details");
	}

	@Test
	void aPrefilterResultWithoutCallDetailsIsAccepted() {
		assertThat(prefilter(null, null, null, null, false, null, null, null, null, null, null, 0, 0))
			.isEqualTo(Provenance.prefilter("V2", AT));
	}

	@Test
	void aModelResultWithoutAPromptVersionATierOrWithZeroAttemptsIsRefused() {
		assertThatIllegalArgumentException().isThrownBy(() -> model(null, ServiceTier.FLEX, 1))
			.withMessage("a model result needs a prompt version, a tier and an attempt");
		assertThatIllegalArgumentException().isThrownBy(() -> model("p1", null, 1))
			.withMessage("a model result needs a prompt version, a tier and an attempt");
		assertThatIllegalArgumentException().isThrownBy(() -> model("p1", ServiceTier.FLEX, 0))
			.withMessage("a model result needs a prompt version, a tier and an attempt");
	}

	@Test
	void aModelResultWithOneAttemptAndNoResponseFieldsIsAccepted() {
		// A call that produced no text (Assessor.failed): no channel, effort, output mode or tokens.
		Provenance provenance = model("p1", ServiceTier.DEFAULT, 1);

		assertThat(provenance.attempts()).isEqualTo(1);
		assertThat(provenance.channel()).isNull();
	}

	@Test
	void everyProviderTierAndEffortIdIsItsJsonValue() {
		for (Provider provider : Provider.values()) {
			assertThat(MAPPER.writeValueAsString(provider)).isEqualTo("\"" + provider.id() + "\"");
		}
		for (ServiceTier tier : ServiceTier.values()) {
			assertThat(MAPPER.writeValueAsString(tier)).isEqualTo("\"" + tier.id() + "\"");
		}
		for (ReasoningEffort effort : ReasoningEffort.values()) {
			assertThat(MAPPER.writeValueAsString(effort)).isEqualTo("\"" + effort.id() + "\"");
		}
	}

	private static Arguments detail(String name, Supplier<Provenance> provenance) {
		return Arguments.of(name, provenance);
	}

	private static Provenance prefilter(Provider provider, String model, Channel channel, ServiceTier serviceTier,
			boolean flexFallback, ReasoningEffort reasoningEffort, OutputMode outputMode, String promptVersion,
			Integer inputTokens, Integer outputTokens, Integer cachedTokens, long durationMs, int attempts) {
		return new Provenance(Provenance.Source.PREFILTER, provider, model, channel, serviceTier, flexFallback,
				reasoningEffort, outputMode, promptVersion, "V2", AT, inputTokens, outputTokens, cachedTokens, durationMs,
				attempts);
	}

	private static Provenance model(String promptVersion, ServiceTier serviceTier, int attempts) {
		return new Provenance(Provenance.Source.MODEL, Provider.GEMINI, "gemini-3.1-flash-lite", null, serviceTier,
				false, null, null, promptVersion, "V2", AT, null, null, null, 15, attempts);
	}

}
