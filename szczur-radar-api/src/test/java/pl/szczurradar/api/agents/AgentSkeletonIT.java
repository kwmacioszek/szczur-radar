package pl.szczurradar.api.agents;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.embabel.agent.core.Agent;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentProcess;
import com.embabel.agent.core.AgentProcessStatusCode;
import com.embabel.agent.core.ProcessOptions;
import com.embabel.agent.spi.LlmService;
import com.embabel.common.ai.model.DefaultModelSelectionCriteria;
import com.embabel.common.ai.model.ModelMetadata;
import com.embabel.common.ai.model.ModelProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

import pl.szczurradar.api.TestcontainersConfiguration;
import pl.szczurradar.api.agents.SkeletonAgent.Ping;
import pl.szczurradar.api.agents.SkeletonAgent.Pong;
import pl.szczurradar.api.config.LlmProviderProperties;

/**
 * Embabel inside the real application context, with the same configuration as the server and the
 * real LLM_API_PROVIDER / LLM_MODEL / LLM_API_KEY of the environment (a CI secret): no test switch,
 * no placeholder key, no fallback that disables the platform. Missing settings fail the context
 * start (LlmConfigurationCheck) — the test is never skipped.
 *
 * <p>Neither the start nor the skeleton action calls the LLM, so this confirms the local
 * registration of the platform, the selected model and the agent, not that the key works.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AgentSkeletonIT {

	@Autowired
	AgentPlatform agentPlatform;

	@Autowired
	ModelProvider modelProvider;

	@Autowired
	Environment environment;

	@Test
	void theSelectedModelIsTheDefaultLlm() {
		LlmProviderProperties llm = LlmProviderProperties.from(environment);

		LlmService<?> defaultLlm = modelProvider.getLlm(DefaultModelSelectionCriteria.INSTANCE);

		assertThat(defaultLlm.getName()).isEqualTo(llm.model());
	}

	@Test
	void onlyTheSelectedProvidersModelsAreRegistered() {
		String provider = modelProvider.getLlm(DefaultModelSelectionCriteria.INSTANCE).getProvider();

		assertThat(modelProvider.listModels()).extracting(ModelMetadata::getProvider).containsOnly(provider);
	}

	@Test
	void theSkeletonAgentIsDeployedAndRunsWithoutCallingTheLlm() {
		Agent agent = agentPlatform.agents()
			.stream()
			.filter(candidate -> candidate.getName().equals(SkeletonAgent.class.getSimpleName()))
			.findFirst()
			.orElseThrow(() -> new AssertionError("SkeletonAgent not deployed: " + agentPlatform.agents()));

		AgentProcess process = agentPlatform.runAgentFrom(agent, ProcessOptions.DEFAULT, Map.of("it", new Ping("hello")));

		assertThat(process.getStatus()).isEqualTo(AgentProcessStatusCode.COMPLETED);
		assertThat(process.resultOfType(Pong.class)).isEqualTo(new Pong("pong: hello"));
		assertThat(process.getLlmInvocations()).isEmpty();
	}

}
