package pl.szczurradar.api.agents.assessor;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import pl.szczurradar.api.llm.Channel;
import pl.szczurradar.api.llm.OutputMode;
import pl.szczurradar.api.llm.Provider;
import pl.szczurradar.api.llm.ReasoningEffort;
import pl.szczurradar.api.llm.ServiceTier;

/** {@link Provenance} as JSON: every closed value in lower case, as stored and recorded. */
class ProvenanceTest {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

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

}
