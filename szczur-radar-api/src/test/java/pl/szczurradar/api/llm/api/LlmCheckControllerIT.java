package pl.szczurradar.api.llm.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import pl.szczurradar.api.TestcontainersConfiguration;
import pl.szczurradar.api.cli.MigrateCommand;
import pl.szczurradar.api.config.LlmProviderProperties;
import pl.szczurradar.api.config.LlmProviderProperties.ServiceTier;
import pl.szczurradar.api.llm.CallFailure;
import pl.szczurradar.api.llm.Channel;
import pl.szczurradar.api.llm.LlmCallException;
import pl.szczurradar.api.llm.LlmClient;
import pl.szczurradar.api.llm.LlmConfigurationException;
import pl.szczurradar.api.llm.LlmResponse;
import pl.szczurradar.api.llm.OutputMode;
import pl.szczurradar.api.llm.transport.TransportException;
import pl.szczurradar.api.user.Role;

/** {@code GET /api/admin/llm/check}: the configured provider and model, and whether one tiny call works. */
@SpringBootTest(properties = MigrateCommand.ENABLE_FLYWAY)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LlmCheckControllerIT {

	@MockitoBean
	LlmClient llmClient;

	@Autowired
	MockMvcTester mvc;

	@Autowired
	LlmProviderProperties properties;

	@Test
	void aWorkingCallIsOk() {
		when(llmClient.check()).thenReturn(new LlmResponse("{\"ok\":true}", properties.provider(), "answering-model",
				Channel.DEEPSEEK, ServiceTier.DEFAULT, false, null, OutputMode.JSON_MODE, 30, 5, null, 1));

		assertThat(mvc.get().uri("/api/admin/llm/check").with(asAdmin())).hasStatusOk()
			.bodyJson()
			.isStrictlyEqualTo("{\"provider\":\"" + properties.provider().id() + "\",\"model\":\"" + properties.model()
					+ "\",\"ok\":true,\"refused\":false,\"answeredBy\":\"answering-model\",\"error\":null}");
	}

	@Test
	void aRefusedKeyIsNotOkAndSaysWhy() {
		when(llmClient.check()).thenThrow(new LlmConfigurationException("HTTP 401: Authentication Fails", 1,
				ServiceTier.DEFAULT, false,
				new TransportException(TransportException.Kind.CONFIGURATION, "HTTP 401", null, null)));

		assertThat(mvc.get().uri("/api/admin/llm/check").with(asAdmin())).hasStatusOk()
			.bodyJson()
			.isStrictlyEqualTo("{\"provider\":\"" + properties.provider().id() + "\",\"model\":\"" + properties.model()
					+ "\",\"ok\":false,\"refused\":true,\"answeredBy\":null,\"error\":\"HTTP 401: Authentication Fails\"}");
	}

	@Test
	void anOutageIsNotOkButNotRefused() {
		when(llmClient.check()).thenThrow(new LlmCallException(CallFailure.TIMEOUT, "failed after 3 attempts: time-out",
				3, ServiceTier.DEFAULT, false, new TransportException(TransportException.Kind.TIMEOUT, "time-out", null, null)));

		assertThat(mvc.get().uri("/api/admin/llm/check").with(asAdmin())).hasStatusOk()
			.bodyJson()
			.extractingPath("$.refused")
			.isEqualTo(false);
	}

	private static RequestPostProcessor asAdmin() {
		return user("00000000-0000-0000-0000-000000000001").roles(Role.ADMIN.name());
	}

}
