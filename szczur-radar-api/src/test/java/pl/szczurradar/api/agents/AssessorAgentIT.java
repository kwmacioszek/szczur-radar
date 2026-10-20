package pl.szczurradar.api.agents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import pl.szczurradar.api.TestcontainersConfiguration;
import pl.szczurradar.api.agents.assessor.AssessmentResult;
import pl.szczurradar.api.agents.assessor.AssessorAgent;
import pl.szczurradar.api.agents.assessor.ContractFixtures;
import pl.szczurradar.api.agents.assessor.Prefilter;
import pl.szczurradar.api.agents.assessor.Provenance;
import pl.szczurradar.api.llm.CallFailure;
import pl.szczurradar.api.llm.LlmClient;
import pl.szczurradar.api.llm.LlmProviderProperties;
import pl.szczurradar.api.llm.LlmUnavailableException;
import pl.szczurradar.api.llm.ServiceTier;

/**
 * Embabel inside the real application context, with the same configuration as the server and the
 * real LLM_API_PROVIDER / LLM_MODEL / LLM_API_KEY of the environment (a CI secret): no test switch,
 * no placeholder key, no fallback that disables the platform. Missing settings fail the context
 * start (LlmConfigurationCheck) — the test is never skipped.
 *
 * <p>Nothing here calls the LLM: the start does not, and the assessor agent runs on an entry without
 * content, which the prefilter decides. The {@link LlmClient} is a mock that proves it — so this
 * confirms the local registration of the platform, the selected model and the agent, not that the
 * key works. One case makes the mock fail the call, to show that the agent's action fires once.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AssessorAgentIT {

	@Autowired
	AgentPlatform agentPlatform;

	@Autowired
	ModelProvider modelProvider;

	@Autowired
	Environment environment;

	@MockitoBean
	LlmClient llmClient;

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
	void theAssessorAgentIsDeployedAndAssessesAnEntryWithoutContentWithoutCallingTheLlm() {
		AgentProcess process = agentPlatform.runAgentFrom(assessorAgent(), ProcessOptions.DEFAULT,
				Map.of("it", ContractFixtures.post("#gielda https://example.com")));

		assertThat(process.getStatus()).isEqualTo(AgentProcessStatusCode.COMPLETED);
		AssessmentResult result = process.resultOfType(AssessmentResult.class);
		assertThat(result.ok()).isTrue();
		assertThat(result.output()).isEqualTo(Prefilter.noContentOutput());
		assertThat(result.provenance().source()).isEqualTo(Provenance.Source.PREFILTER);
		verify(llmClient, never()).complete(any());
		assertThat(process.getLlmInvocations()).isEmpty();
	}

	@Test
	void theAssessorActionFiresOnceWhenTheProviderIsUnavailable() {
		when(llmClient.complete(any())).thenThrow(new LlmUnavailableException(CallFailure.PROVIDER_ERROR,
				"HTTP 503 after 3 attempts", 3, ServiceTier.DEFAULT, false, null));

		assertThatExceptionOfType(LlmUnavailableException.class).isThrownBy(() -> agentPlatform
			.runAgentFrom(assessorAgent(), ProcessOptions.DEFAULT, Map.of("it", ContractFixtures.validInput())));

		// The paid call is not repeated by Embabel's default action retries.
		verify(llmClient).complete(any());
	}

	private Agent assessorAgent() {
		return agentPlatform.agents()
			.stream()
			.filter(candidate -> candidate.getName().equals(AssessorAgent.class.getSimpleName()))
			.findFirst()
			.orElseThrow(() -> new AssertionError("AssessorAgent not deployed: " + agentPlatform.agents()));
	}

}
